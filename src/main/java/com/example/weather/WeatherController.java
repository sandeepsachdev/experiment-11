package com.example.weather;

import com.example.weather.model.WeatherReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WeatherController {

    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        try {
            WeatherReport report = weatherService.getReport();
            model.addAttribute("report", report);
            model.addAttribute("reportJson", weatherService.toJson(report));
        } catch (RuntimeException ex) {
            log.error("Unable to build the weather report", ex);
            model.addAttribute("error",
                    "Sorry — the weather data could not be loaded right now. Please try again shortly.");
        }
        return "index";
    }

    @GetMapping(value = "/api/weather", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public WeatherReport api() {
        return weatherService.getReport();
    }

    @GetMapping("/healthz")
    @ResponseBody
    public String health() {
        return "OK";
    }
}
