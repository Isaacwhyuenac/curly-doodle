package com.lightnet.weather.provider;

import com.lightnet.weather.api.WeatherResponse;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface WeatherProvider {

    Mono<WeatherResponse> fetch();

    default String name() {
        return getClass().getSimpleName();
    }
}
