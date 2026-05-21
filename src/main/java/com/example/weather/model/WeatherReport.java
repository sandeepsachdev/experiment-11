package com.example.weather.model;

import java.util.List;

/**
 * A full 12-month weather report for a location, including how each month and the
 * period as a whole varied from the climate normals.
 */
public record WeatherReport(
        String locationName,
        double latitude,
        double longitude,
        String periodLabel,
        String baselineLabel,
        List<MonthlyWeather> months,
        double avgTempAnomaly,
        double totalRainfall,
        double totalNormalRainfall,
        double rainfallAnomaly,
        double rainfallPctOfNormal,
        int warmerMonths,
        int wetterMonths,
        String generatedAt) {
}
