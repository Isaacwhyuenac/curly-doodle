package com.lightnet.weather.api;

import com.lightnet.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/v1/weather")
    public Mono<WeatherResponse> weather(@RequestParam(defaultValue = "singapore") String city) {
        return weatherService.getWeather(city);
    }
}
