package com.lightnet.weather.provider;

import com.lightnet.weather.api.WeatherResponse;
import com.lightnet.weather.exception.WeatherUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class CircuitBreakingWeatherProvider implements WeatherProvider {

    private final WeatherProvider delegate;
    private final ProviderCircuit circuit;

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public Mono<WeatherResponse> fetch() {
        if (circuit.isOpen()) {
            return Mono.error(new WeatherUnavailableException("Circuit open for " + name()));
        }
        return delegate.fetch()
                .doOnNext(ignored -> circuit.recordSuccess())
                .doOnError(error -> {
                    circuit.recordFailure();
                    log.warn("Weather provider {} failed: {}", name(), error.toString());
                });
    }
}
