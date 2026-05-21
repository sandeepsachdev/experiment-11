package com.example.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "weather")
public record WeatherProperties(
        @DefaultValue Location location,
        @DefaultValue Baseline baseline,
        @DefaultValue Cache cache) {

    public record Location(
            @DefaultValue("Cherrybrook, NSW, Australia") String name,
            @DefaultValue("-33.7217") double latitude,
            @DefaultValue("151.0458") double longitude) {
    }

    public record Baseline(
            @DefaultValue("1991") int startYear,
            @DefaultValue("2020") int endYear) {
    }

    public record Cache(
            @DefaultValue("360") long ttlMinutes) {
    }
}
