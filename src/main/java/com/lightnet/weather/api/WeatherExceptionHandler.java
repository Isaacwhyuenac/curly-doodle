package com.lightnet.weather.api;

import com.lightnet.weather.exception.UnsupportedCityException;
import com.lightnet.weather.exception.WeatherUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class WeatherExceptionHandler {

    @ExceptionHandler(UnsupportedCityException.class)
    public ResponseEntity<Map<String, String>> unsupportedCity(UnsupportedCityException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(WeatherUnavailableException.class)
    public ResponseEntity<Map<String, String>> unavailable(WeatherUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Weather data is currently unavailable"));
    }
}
