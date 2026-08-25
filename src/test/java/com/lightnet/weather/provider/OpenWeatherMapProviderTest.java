package com.lightnet.weather.provider;

import com.lightnet.weather.api.WeatherResponse;
import com.lightnet.weather.exception.WeatherUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenWeatherMapProviderTest {

    @Test
    void mapsCelsiusTemperatureAndConvertsWindSpeedToKilometresPerHour() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        OpenWeatherMapProvider provider = provider(request -> {
            captured.set(request);
            return json(HttpStatus.OK, """
                    {
                      "main": { "temp": 28.7 },
                      "wind": { "speed": 5.55 }
                    }
                    """);
        });

        StepVerifier.create(provider.fetch())
                .expectNext(new WeatherResponse(20, 29))
                .verifyComplete();

        ClientRequest request = captured.get();
        String        query   = URLDecoder.decode(request.url().getQuery(), StandardCharsets.UTF_8);
        assertThat(request.url().getPath()).isEqualTo("/data/2.5/weather");
        assertThat(query).contains("q=singapore,SG");
        assertThat(query).contains("appid=test-api-key");
        assertThat(query).contains("units=metric");
    }

    @Test
    void treatsMissingWeatherFieldsAsFailure() {
        OpenWeatherMapProvider provider = provider(request -> json(HttpStatus.OK, "{ \"cod\": 200 }"));

        StepVerifier.create(provider.fetch())
                .expectError(WeatherUnavailableException.class)
                .verify();
    }

    @Test
    void treatsHttpErrorsAsFailure() {
        OpenWeatherMapProvider provider = provider(request ->
                Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .body("{\"cod\":401}")
                        .build()));

        StepVerifier.create(provider.fetch())
                .expectError(WeatherUnavailableException.class)
                .verify();
    }

    private static OpenWeatherMapProvider provider(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://openweathermap.test")
                .exchangeFunction(exchangeFunction)
                .build();
        return new OpenWeatherMapProvider(webClient, "test-api-key", Duration.ofSeconds(2));
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
