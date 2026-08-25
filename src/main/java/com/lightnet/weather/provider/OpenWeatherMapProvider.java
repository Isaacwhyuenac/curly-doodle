package com.lightnet.weather.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lightnet.weather.api.WeatherResponse;
import com.lightnet.weather.exception.WeatherUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RequiredArgsConstructor
public class OpenWeatherMapProvider implements WeatherProvider {

    private static final double METRES_PER_SECOND_TO_KM_PER_HOUR = 3.6;

    private final WebClient webClient;
    private final String    apiKey;
    private final Duration  timeout;

    @Override
    public String name() {
        return "openweathermap";
    }

    @Override
    public Mono<WeatherResponse> fetch() {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new WeatherUnavailableException("OpenWeatherMap API key is not configured"));
        }
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/data/2.5/weather")
                        .queryParam("q", "singapore,SG")
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .build())
                .retrieve()
                .bodyToMono(ApiResponse.class)
                .timeout(timeout)
                .map(this::map)
                .onErrorMap(this::shouldWrap, error ->
                        new WeatherUnavailableException("OpenWeatherMap request failed", error));
    }

    private WeatherResponse map(ApiResponse response) {
        if (response.main() == null || response.main().temp() == null
            || response.wind() == null || response.wind().speed() == null) {
            throw new WeatherUnavailableException("OpenWeatherMap response was missing weather data");
        }
        int windSpeedKmh = (int) Math.round(response.wind().speed() * METRES_PER_SECOND_TO_KM_PER_HOUR);
        int temperature  = (int) Math.round(response.main().temp());
        return new WeatherResponse(windSpeedKmh, temperature);
    }

    private boolean shouldWrap(Throwable error) {
        return !(error instanceof WeatherUnavailableException);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse(Main main, Wind wind) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Main(Double temp) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Wind(Double speed) {
        }
    }
}
