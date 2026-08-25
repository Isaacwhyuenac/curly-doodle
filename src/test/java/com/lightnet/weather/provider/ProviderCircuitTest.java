package com.lightnet.weather.provider;

import com.lightnet.weather.api.WeatherResponse;
import com.lightnet.weather.exception.WeatherUnavailableException;
import com.lightnet.weather.support.MutableClock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCircuitTest {

    @Test
    void skipsDelegateWhileCircuitIsOpenThenRetriesAfterCooldown() {
        MutableClock  clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        WeatherProvider delegate = () -> {
            int attempt = calls.incrementAndGet();
            if (attempt == 1) {
                return Mono.error(new WeatherUnavailableException("down"));
            }
            return Mono.just(new WeatherResponse(10, 25));
        };
        WeatherProvider provider = new CircuitBreakingWeatherProvider(
                delegate,
                new ProviderCircuit(Duration.ofSeconds(30), clock)
        );

        StepVerifier.create(provider.fetch()).expectError(WeatherUnavailableException.class).verify();
        StepVerifier.create(provider.fetch()).expectError(WeatherUnavailableException.class).verify();
        assertThat(calls.get()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(30));
        StepVerifier.create(provider.fetch())
                .expectNext(new WeatherResponse(10, 25))
                .verifyComplete();
        assertThat(calls.get()).isEqualTo(2);
    }
}
