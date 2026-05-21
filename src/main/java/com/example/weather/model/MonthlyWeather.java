package com.example.weather.model;

/**
 * Weather for a single calendar month compared against the climate normal for that month.
 *
 * @param label                e.g. "May 2025"
 * @param year                 calendar year
 * @param month                calendar month (1-12)
 * @param avgTemp               mean 2 m air temperature for the month (°C)
 * @param normalTemp            normal mean temperature for that calendar month (°C)
 * @param tempAnomaly           avgTemp minus normalTemp (°C; positive = warmer than normal)
 * @param rainfall              total precipitation for the month (mm)
 * @param normalRainfall        normal precipitation for that calendar month (mm)
 * @param rainfallAnomaly       rainfall minus normalRainfall (mm; positive = wetter than normal)
 * @param rainfallPctOfNormal   rainfall as a percentage of the normal
 */
public record MonthlyWeather(
        String label,
        int year,
        int month,
        double avgTemp,
        double normalTemp,
        double tempAnomaly,
        double rainfall,
        double normalRainfall,
        double rainfallAnomaly,
        double rainfallPctOfNormal) {
}
