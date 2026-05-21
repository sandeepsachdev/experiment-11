package com.example.weather;

/** Raised when a weather report cannot be produced (e.g. the upstream API is unreachable). */
public class WeatherException extends RuntimeException {

    public WeatherException(String message) {
        super(message);
    }

    public WeatherException(String message, Throwable cause) {
        super(message, cause);
    }
}
