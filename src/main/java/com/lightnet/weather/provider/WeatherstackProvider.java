package com.lightnet.weather.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lightnet.weather.api.WeatherResponse;
import com.lightnet.weather.exception.WeatherUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RequiredArgsConstructor
public class WeatherstackProvider implements WeatherProvider {

    private final WebClient webClient;
    private final String accessKey;
    private final Duration timeout;

    @Override
    public String name() {
        return "weatherstack";
    }

    @Override
    public Mono<WeatherResponse> fetch() {
        if (accessKey == null || accessKey.isBlank()) {
            return Mono.error(new WeatherUnavailableException("Weatherstack access key is not configured"));
        }
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/current")
                        .queryParam("access_key", accessKey)
                        .queryParam("query", "Singapore")
                        .queryParam("units", "m")
                        .build())
                .retrieve()
                .bodyToMono(ApiResponse.class)
                .timeout(timeout)
                .map(this::map)
                .onErrorMap(this::shouldWrap, error ->
                        new WeatherUnavailableException("Weatherstack request failed", error));
    }

    private WeatherResponse map(ApiResponse response) {
        if (Boolean.FALSE.equals(response.success())) {
            throw new WeatherUnavailableException("Weatherstack returned an error payload");
        }
        if (response.current() == null
                || response.current().temperature() == null
                || response.current().windSpeed() == null) {
            throw new WeatherUnavailableException("Weatherstack response was missing weather data");
        }
        return new WeatherResponse(
                (int) Math.round(response.current().windSpeed()),
                (int) Math.round(response.current().temperature())
        );
    }

    private boolean shouldWrap(Throwable error) {
        return !(error instanceof WeatherUnavailableException);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse(Boolean success, Current current) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Current(
                Double temperature,
                @JsonProperty("wind_speed") Double windSpeed
        ) {
        }
    }
}
