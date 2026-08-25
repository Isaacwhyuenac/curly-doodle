package com.lightnet.weather.provider;

import com.lightnet.weather.api.WeatherResponse;
import com.lightnet.weather.exception.WeatherUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherstackProviderTest {

    @Test
    void mapsMetricTemperatureAndWindSpeed() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WeatherstackProvider provider = provider(request -> {
            captured.set(request);
            return json(HttpStatus.OK, """
                    {
                      "current": {
                        "temperature": 29,
                        "wind_speed": 20
                      }
                    }
                    """);
        });

        StepVerifier.create(provider.fetch())
                .expectNext(new WeatherResponse(20, 29))
                .verifyComplete();

        ClientRequest request = captured.get();
        assertThat(request.url().getPath()).isEqualTo("/current");
        assertThat(request.url().getQuery()).contains("access_key=test-access-key");
        assertThat(request.url().getQuery()).contains("query=Singapore");
        assertThat(request.url().getQuery()).contains("units=m");
    }

    @Test
    void treatsApiErrorPayloadAsFailure() {
        WeatherstackProvider provider = provider(request -> json(HttpStatus.OK, """
                {
                  "success": false,
                  "error": {
                    "code": 101,
                    "type": "invalid_access_key"
                  }
                }
                """));

        StepVerifier.create(provider.fetch())
                .expectError(WeatherUnavailableException.class)
                .verify();
    }

    @Test
    void treatsMissingCurrentBlockAsFailure() {
        WeatherstackProvider provider = provider(request -> json(HttpStatus.OK, "{ }"));

        StepVerifier.create(provider.fetch())
                .expectError(WeatherUnavailableException.class)
                .verify();
    }

    @Test
    void treatsHttpErrorsAsFailure() {
        WeatherstackProvider provider = provider(request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        StepVerifier.create(provider.fetch())
                .expectError(WeatherUnavailableException.class)
                .verify();
    }

    private static WeatherstackProvider provider(
            org.springframework.web.reactive.function.client.ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://weatherstack.test")
                .exchangeFunction(exchangeFunction)
                .build();
        return new WeatherstackProvider(webClient, "test-access-key", Duration.ofSeconds(2));
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
