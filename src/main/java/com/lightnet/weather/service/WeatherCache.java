package com.lightnet.weather.service;

import com.lightnet.weather.api.WeatherResponse;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public class WeatherCache {

    private final Duration ttl;
    private final Clock clock;
    private final AtomicReference<Entry> entry = new AtomicReference<>();

    public Optional<WeatherResponse> getFresh() {
        Entry current = entry.get();
        if (current == null) {
            return Optional.empty();
        }
        Instant expiresAt = current.fetchedAt().plus(ttl);
        if (clock.instant().isBefore(expiresAt)) {
            return Optional.of(current.weather());
        }
        return Optional.empty();
    }

    public Optional<WeatherResponse> getStale() {
        Entry current = entry.get();
        return current == null ? Optional.empty() : Optional.of(current.weather());
    }

    public void put(WeatherResponse weather) {
        entry.set(new Entry(weather, clock.instant()));
    }

    private record Entry(WeatherResponse weather, Instant fetchedAt) {
    }
}
