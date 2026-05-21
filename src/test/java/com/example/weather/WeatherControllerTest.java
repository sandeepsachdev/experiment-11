package com.example.weather;

import com.example.weather.model.MonthlyWeather;
import com.example.weather.model.WeatherReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WeatherController.class)
class WeatherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeatherService weatherService;

    @Test
    void rendersDashboardWithReport() throws Exception {
        WeatherReport report = sampleReport();
        when(weatherService.getReport()).thenReturn(report);
        when(weatherService.toJson(any())).thenReturn(new ObjectMapper().writeValueAsString(report));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cherrybrook, NSW, Australia")))
                .andExpect(content().string(containsString("Apr 2026")))
                .andExpect(content().string(containsString("tempChart")))
                .andExpect(content().string(containsString("Month-by-month detail")))
                // responsive detail table: scroll wrapper + per-cell labels for the mobile card layout
                .andExpect(content().string(containsString("table-scroll")))
                .andExpect(content().string(containsString("data-label=\"Avg temp (°C)\"")))
                // anomaly cell colour-coding for the success path
                .andExpect(content().string(containsString("class=\"num warm\"")));
    }

    @Test
    void rendersFriendlyErrorWhenReportFails() throws Exception {
        when(weatherService.getReport()).thenThrow(new WeatherException("upstream down"));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("could not be loaded")));
    }

    @Test
    void healthCheckResponds() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    private static WeatherReport sampleReport() {
        MonthlyWeather warmWet = new MonthlyWeather("Apr 2026", 2026, 4,
                19.4, 18.1, 1.3, 120.5, 95.0, 25.5, 126.8);
        MonthlyWeather coolDry = new MonthlyWeather("Mar 2026", 2026, 3,
                20.0, 21.2, -1.2, 40.0, 88.0, -48.0, 45.5);
        List<MonthlyWeather> months = List.of(coolDry, warmWet);
        return new WeatherReport(
                "Cherrybrook, NSW, Australia", -33.7217, 151.0458,
                "May 2025 – Apr 2026", "1991–2020 average", months,
                0.1, 160.5, 183.0, -22.5, 87.7, 1, 1,
                "21 May 2026, 08:00 UTC");
    }
}
