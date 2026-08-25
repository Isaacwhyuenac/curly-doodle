package com.lightnet.weather.api;

import com.lightnet.weather.exception.UnsupportedCityException;
import com.lightnet.weather.exception.WeatherUnavailableException;
import com.lightnet.weather.service.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeatherControllerTest {

    private WeatherService weatherService;
    private WebTestClient  client;

    @BeforeEach
    void setUp() {
        weatherService = mock(WeatherService.class);
        client = WebTestClient.bindToController(new WeatherController(weatherService))
                .controllerAdvice(new WeatherExceptionHandler())
                .build();
    }

    @Test
    void returnsUnifiedWeatherJson() {
        when(weatherService.getWeather("singapore"))
                .thenReturn(Mono.just(new WeatherResponse(20, 29)));

        client.get()
                .uri("/v1/weather?city=singapore")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("""
                        {"wind_speed":20,"temperature_degrees":29}
                        """);
    }

    @Test
    void defaultsCityToSingapore() {
        when(weatherService.getWeather("singapore"))
                .thenReturn(Mono.just(new WeatherResponse(12, 27)));

        client.get()
                .uri("/v1/weather")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.wind_speed").isEqualTo(12)
                .jsonPath("$.temperature_degrees").isEqualTo(27);
    }

    @Test
    void returnsBadRequestForUnsupportedCity() {
        when(weatherService.getWeather("london"))
                .thenReturn(Mono.error(new UnsupportedCityException("london")));

        client.get()
                .uri("/v1/weather?city=london")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists();
    }

    @Test
    void returnsServiceUnavailableWhenWeatherCannotBeFetched() {
        when(weatherService.getWeather("singapore"))
                .thenReturn(Mono.error(new WeatherUnavailableException("down")));

        client.get()
                .uri("/v1/weather?city=singapore")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error").exists();
    }
}
