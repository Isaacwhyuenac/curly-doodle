package com.lightnet.weather.service;

import com.lightnet.weather.api.WeatherResponse;
import com.lightnet.weather.exception.UnsupportedCityException;
import com.lightnet.weather.exception.WeatherUnavailableException;
import com.lightnet.weather.provider.WeatherProvider;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

@Slf4j
public class WeatherService {

    private static final String SUPPORTED_CITY = "singapore";

    private final List<WeatherProvider> providers;
    private final WeatherCache cache;

    public WeatherService(List<WeatherProvider> providers, WeatherCache cache) {
        this.providers = List.copyOf(providers);
        this.cache = cache;
    }

    public Mono<WeatherResponse> getWeather(String city) {
        if (!isSingapore(city)) {
            return Mono.error(new UnsupportedCityException(city));
        }

        return cache.getFresh()
                .map(Mono::just)
                .orElseGet(this::fetchFromProvidersOrStale);
    }

    private Mono<WeatherResponse> fetchFromProvidersOrStale() {
        return fetchFromProviders()
                .doOnNext(cache::put)
                .onErrorResume(error -> cache.getStale()
                        .map(stale -> {
                            log.warn("All weather providers failed; serving stale cached weather", error);
                            return Mono.just(stale);
                        })
                        .orElseGet(() -> Mono.error(new WeatherUnavailableException(
                                "All weather providers failed", error))));
    }

    private Mono<WeatherResponse> fetchFromProviders() {
        Mono<WeatherResponse> result = Mono.error(
                new WeatherUnavailableException("No weather providers configured"));
        for (WeatherProvider provider : providers) {
            result = result.onErrorResume(ignored -> provider.fetch());
        }
        return result;
    }

    private static boolean isSingapore(String city) {
        return city != null && SUPPORTED_CITY.equals(city.trim().toLowerCase(Locale.ROOT));
    }
}
