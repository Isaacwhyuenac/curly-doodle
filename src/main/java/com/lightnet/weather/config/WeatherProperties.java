package com.lightnet.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "weather")
public record WeatherProperties(
        @DefaultValue("3s") Duration cacheTtl,
        @DefaultValue("30s") Duration circuitOpenDuration,
        Providers providers
) {

    public record Providers(ProviderConfig weatherstack, ProviderConfig openweathermap) {
    }

    public record ProviderConfig(
            @DefaultValue("true") boolean enabled,
            String baseUrl,
            String accessKey,
            String apiKey,
            @DefaultValue("2s") Duration timeout
    ) {
    }
}
