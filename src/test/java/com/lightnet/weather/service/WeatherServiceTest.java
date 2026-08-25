package com.lightnet.weather.service;

import com.lightnet.weather.api.WeatherResponse;
import com.lightnet.weather.exception.UnsupportedCityException;
import com.lightnet.weather.exception.WeatherUnavailableException;
import com.lightnet.weather.provider.WeatherProvider;
import com.lightnet.weather.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class WeatherServiceTest {

    private static final WeatherResponse PRIMARY = new WeatherResponse(20, 29);
    private static final WeatherResponse FAILOVER = new WeatherResponse(15, 31);

    private MutableClock clock;
    private WeatherCache cache;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        cache = new WeatherCache(Duration.ofSeconds(3), clock);
    }

    @Test
    void returnsWeatherFromPrimaryProvider() {
        WeatherService service = service(successful("weatherstack", PRIMARY));

        StepVerifier.create(service.getWeather("singapore"))
                .expectNext(PRIMARY)
                .verifyComplete();
    }

    @Test
    void failsOverToNextProviderWhenPrimaryFails() {
        WeatherService service = service(
                failing("weatherstack"),
                successful("openweathermap", FAILOVER)
        );

        StepVerifier.create(service.getWeather("singapore"))
                .expectNext(FAILOVER)
                .verifyComplete();
    }

    @Test
    void doesNotCallLaterProvidersWhenPrimarySucceeds() {
        AtomicInteger failoverCalls = new AtomicInteger();
        WeatherProvider failover = () -> {
            failoverCalls.incrementAndGet();
            return Mono.just(FAILOVER);
        };

        WeatherService service = service(successful("weatherstack", PRIMARY), failover);

        StepVerifier.create(service.getWeather("singapore"))
                .expectNext(PRIMARY)
                .verifyComplete();
        org.assertj.core.api.Assertions.assertThat(failoverCalls.get()).isZero();
    }

    @Test
    void servesCachedResultWithinTtlWithoutCallingProviders() {
        AtomicInteger calls = new AtomicInteger();
        WeatherProvider provider = () -> {
            calls.incrementAndGet();
            return Mono.just(PRIMARY);
        };
        WeatherService service = service(provider);

        StepVerifier.create(service.getWeather("singapore")).expectNext(PRIMARY).verifyComplete();
        clock.advance(Duration.ofSeconds(2));
        StepVerifier.create(service.getWeather("singapore")).expectNext(PRIMARY).verifyComplete();

        org.assertj.core.api.Assertions.assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void refreshesFromProvidersAfterCacheTtlExpires() {
        AtomicInteger calls = new AtomicInteger();
        WeatherProvider provider = () -> {
            int attempt = calls.incrementAndGet();
            return Mono.just(attempt == 1 ? PRIMARY : FAILOVER);
        };
        WeatherService service = service(provider);

        StepVerifier.create(service.getWeather("singapore")).expectNext(PRIMARY).verifyComplete();
        clock.advance(Duration.ofSeconds(3));
        StepVerifier.create(service.getWeather("singapore")).expectNext(FAILOVER).verifyComplete();

        org.assertj.core.api.Assertions.assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void servesStaleCacheWhenAllProvidersAreDown() {
        AtomicInteger calls = new AtomicInteger();
        WeatherProvider provider = () -> {
            if (calls.incrementAndGet() == 1) {
                return Mono.just(PRIMARY);
            }
            return Mono.error(new WeatherUnavailableException("down"));
        };
        WeatherService service = service(provider);

        StepVerifier.create(service.getWeather("singapore")).expectNext(PRIMARY).verifyComplete();
        clock.advance(Duration.ofSeconds(30));
        StepVerifier.create(service.getWeather("singapore")).expectNext(PRIMARY).verifyComplete();
    }

    @Test
    void errorsWhenAllProvidersFailAndNoCachedValueExists() {
        WeatherService service = service(failing("weatherstack"), failing("openweathermap"));

        StepVerifier.create(service.getWeather("singapore"))
                .expectError(WeatherUnavailableException.class)
                .verify();
    }

    @Test
    void acceptsSingaporeRegardlessOfCase() {
        WeatherService service = service(successful("weatherstack", PRIMARY));

        StepVerifier.create(service.getWeather("Singapore")).expectNext(PRIMARY).verifyComplete();
        StepVerifier.create(service.getWeather("SINGAPORE")).expectNext(PRIMARY).verifyComplete();
    }

    @Test
    void rejectsCitiesOtherThanSingapore() {
        WeatherService service = service(successful("weatherstack", PRIMARY));

        StepVerifier.create(service.getWeather("london"))
                .expectError(UnsupportedCityException.class)
                .verify();
    }

    private WeatherService service(WeatherProvider... providers) {
        return new WeatherService(List.of(providers), cache);
    }

    private static WeatherProvider successful(String name, WeatherResponse response) {
        return new WeatherProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Mono<WeatherResponse> fetch() {
                return Mono.just(response);
            }
        };
    }

    private static WeatherProvider failing(String name) {
        return new WeatherProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Mono<WeatherResponse> fetch() {
                return Mono.error(new WeatherUnavailableException(name + " is down"));
            }
        };
    }
}
