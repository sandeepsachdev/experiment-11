package com.example.weather;

import com.example.weather.model.MonthlyWeather;
import com.example.weather.model.WeatherReport;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fetches daily weather for the configured location from the Open-Meteo historical
 * archive (ERA5 reanalysis) and turns it into a 12-month report comparing each month
 * to its 1991-2020 climate normal.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final WeatherProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    private volatile WeatherReport cached;
    private volatile Instant cachedAt;

    public WeatherService(WeatherProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Returns a report, reusing a cached one while it is still fresh. */
    public synchronized WeatherReport getReport() {
        Instant freshSince = Instant.now().minus(Duration.ofMinutes(props.cache().ttlMinutes()));
        if (cached != null && cachedAt != null && cachedAt.isAfter(freshSince)) {
            return cached;
        }
        WeatherReport report = buildReport();
        cached = report;
        cachedAt = Instant.now();
        return report;
    }

    public String toJson(WeatherReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new WeatherException("Could not serialise the weather report.", e);
        }
    }

    private WeatherReport buildReport() {
        // The archive lags real time by a few days, so stay clear of the most recent week.
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(6);
        YearMonth lastMonth = YearMonth.from(cutoff).minusMonths(1);
        YearMonth firstMonth = lastMonth.minusMonths(11);

        int baselineStart = props.baseline().startYear();
        int baselineEnd = props.baseline().endYear();
        LocalDate fetchStart = LocalDate.of(baselineStart, 1, 1);
        LocalDate fetchEnd = lastMonth.atEndOfMonth();

        ArchiveResponse response = fetch(fetchStart, fetchEnd);
        Daily daily = response == null ? null : response.daily();
        if (daily == null || daily.time() == null || daily.time().isEmpty()) {
            throw new WeatherException("The weather service returned no data.");
        }

        Map<YearMonth, MonthAccumulator> byMonth = accumulateByMonth(daily);

        double[] normalTemp = new double[13];
        double[] normalRain = new double[13];
        computeNormals(byMonth, baselineStart, baselineEnd, normalTemp, normalRain);

        List<MonthlyWeather> months = new ArrayList<>(12);
        YearMonth cursor = firstMonth;
        for (int i = 0; i < 12; i++) {
            MonthAccumulator acc = byMonth.getOrDefault(cursor, new MonthAccumulator());
            int m = cursor.getMonthValue();
            double avgTemp = acc.hasTemperature() ? acc.meanTemperature() : normalTemp[m];
            double rainfall = acc.totalPrecipitation();
            double normT = normalTemp[m];
            double normR = normalRain[m];
            double rainPct = normR > 0 ? (rainfall / normR) * 100.0 : 0.0;

            months.add(new MonthlyWeather(
                    cursor.format(MONTH_LABEL),
                    cursor.getYear(),
                    m,
                    round1(avgTemp),
                    round1(normT),
                    round1(avgTemp - normT),
                    round1(rainfall),
                    round1(normR),
                    round1(rainfall - normR),
                    round1(rainPct)));
            cursor = cursor.plusMonths(1);
        }

        return summarise(months, firstMonth, lastMonth, baselineStart, baselineEnd);
    }

    private Map<YearMonth, MonthAccumulator> accumulateByMonth(Daily daily) {
        List<String> dates = daily.time();
        List<Double> temps = daily.temperatureMean();
        List<Double> rain = daily.precipitationSum();
        Map<YearMonth, MonthAccumulator> byMonth = new HashMap<>();

        for (int i = 0; i < dates.size(); i++) {
            YearMonth ym = YearMonth.from(LocalDate.parse(dates.get(i)));
            Double t = temps != null && i < temps.size() ? temps.get(i) : null;
            Double p = rain != null && i < rain.size() ? rain.get(i) : null;
            byMonth.computeIfAbsent(ym, k -> new MonthAccumulator()).add(t, p);
        }
        return byMonth;
    }

    private void computeNormals(Map<YearMonth, MonthAccumulator> byMonth,
                                int baselineStart, int baselineEnd,
                                double[] normalTemp, double[] normalRain) {
        int[] yearCount = new int[13];
        for (Map.Entry<YearMonth, MonthAccumulator> entry : byMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            if (ym.getYear() < baselineStart || ym.getYear() > baselineEnd) {
                continue;
            }
            MonthAccumulator acc = entry.getValue();
            if (!acc.hasTemperature()) {
                continue;
            }
            int m = ym.getMonthValue();
            normalTemp[m] += acc.meanTemperature();
            normalRain[m] += acc.totalPrecipitation();
            yearCount[m]++;
        }
        for (int m = 1; m <= 12; m++) {
            if (yearCount[m] == 0) {
                throw new WeatherException("No baseline data available for month " + m + ".");
            }
            normalTemp[m] /= yearCount[m];
            normalRain[m] /= yearCount[m];
        }
    }

    private WeatherReport summarise(List<MonthlyWeather> months, YearMonth firstMonth,
                                    YearMonth lastMonth, int baselineStart, int baselineEnd) {
        double avgTempAnomaly = months.stream().mapToDouble(MonthlyWeather::tempAnomaly).average().orElse(0);
        double totalRainfall = months.stream().mapToDouble(MonthlyWeather::rainfall).sum();
        double totalNormalRainfall = months.stream().mapToDouble(MonthlyWeather::normalRainfall).sum();
        double rainfallPct = totalNormalRainfall > 0
                ? (totalRainfall / totalNormalRainfall) * 100.0 : 0.0;
        int warmerMonths = (int) months.stream().filter(m -> m.tempAnomaly() > 0).count();
        int wetterMonths = (int) months.stream().filter(m -> m.rainfallAnomaly() > 0).count();

        String generatedAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH));

        return new WeatherReport(
                props.location().name(),
                props.location().latitude(),
                props.location().longitude(),
                firstMonth.format(MONTH_LABEL) + " – " + lastMonth.format(MONTH_LABEL),
                baselineStart + "–" + baselineEnd + " average",
                months,
                round1(avgTempAnomaly),
                round1(totalRainfall),
                round1(totalNormalRainfall),
                round1(totalRainfall - totalNormalRainfall),
                round1(rainfallPct),
                warmerMonths,
                wetterMonths,
                generatedAt);
    }

    private ArchiveResponse fetch(LocalDate start, LocalDate end) {
        try {
            return restClient.get()
                    .uri(ARCHIVE_URL + "?latitude={lat}&longitude={lon}&start_date={start}"
                            + "&end_date={end}&daily=temperature_2m_mean,precipitation_sum"
                            + "&timezone=auto",
                            props.location().latitude(), props.location().longitude(), start, end)
                    .retrieve()
                    .body(ArchiveResponse.class);
        } catch (RestClientException ex) {
            log.error("Failed to fetch weather data from Open-Meteo", ex);
            throw new WeatherException("Could not reach the Open-Meteo weather service.", ex);
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** Running totals for one calendar month. */
    private static final class MonthAccumulator {
        private double temperatureSum;
        private int temperatureDays;
        private double precipitationSum;

        void add(Double temperature, Double precipitation) {
            if (temperature != null) {
                temperatureSum += temperature;
                temperatureDays++;
            }
            if (precipitation != null) {
                precipitationSum += precipitation;
            }
        }

        boolean hasTemperature() {
            return temperatureDays > 0;
        }

        double meanTemperature() {
            return temperatureSum / temperatureDays;
        }

        double totalPrecipitation() {
            return precipitationSum;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ArchiveResponse(Daily daily) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Daily(
            List<String> time,
            @JsonProperty("temperature_2m_mean") List<Double> temperatureMean,
            @JsonProperty("precipitation_sum") List<Double> precipitationSum) {
    }
}
