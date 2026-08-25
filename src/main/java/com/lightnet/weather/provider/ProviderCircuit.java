package com.lightnet.weather.provider;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public class ProviderCircuit {

    private final Duration                 openDuration;
    private final Clock                    clock;
    private final AtomicReference<Instant> openUntil = new AtomicReference<>();

    public boolean isOpen() {
        Instant until = openUntil.get();
        return until != null && clock.instant().isBefore(until);
    }

    public void recordSuccess() {
        openUntil.set(null);
    }

    public void recordFailure() {
        openUntil.set(clock.instant().plus(openDuration));
    }
}
