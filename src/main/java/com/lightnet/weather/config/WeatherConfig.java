package com.lightnet.weather.config;

import com.lightnet.weather.provider.CircuitBreakingWeatherProvider;
import com.lightnet.weather.provider.OpenWeatherMapProvider;
import com.lightnet.weather.provider.ProviderCircuit;
import com.lightnet.weather.provider.WeatherProvider;
import com.lightnet.weather.provider.WeatherstackProvider;
import com.lightnet.weather.service.WeatherCache;
import com.lightnet.weather.service.WeatherService;
import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(WeatherProperties.class)
public class WeatherConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    WeatherCache weatherCache(WeatherProperties properties, Clock clock) {
        return new WeatherCache(properties.cacheTtl(), clock);
    }

    @Bean
    WeatherService weatherService(List<WeatherProvider> providers, WeatherCache cache) {
        return new WeatherService(providers, cache);
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(prefix = "weather.providers.weatherstack", name = "enabled", havingValue = "true", matchIfMissing = true)
    WeatherProvider weatherstackProvider(WeatherProperties properties, Clock clock, WebClient.Builder webClientBuilder) {
        WeatherProperties.ProviderConfig config = properties.providers().weatherstack();
        WeatherProvider provider = new WeatherstackProvider(
                webClient(webClientBuilder, config.baseUrl(), config.timeout()),
                config.accessKey(),
                config.timeout()
        );
        return new CircuitBreakingWeatherProvider(provider, new ProviderCircuit(properties.circuitOpenDuration(), clock));
    }

    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "weather.providers.openweathermap", name = "enabled", havingValue = "true", matchIfMissing = true)
    WeatherProvider openWeatherMapProvider(WeatherProperties properties, Clock clock, WebClient.Builder webClientBuilder) {
        WeatherProperties.ProviderConfig config = properties.providers().openweathermap();
        WeatherProvider provider = new OpenWeatherMapProvider(
                webClient(webClientBuilder, config.baseUrl(), config.timeout()),
                config.apiKey(),
                config.timeout()
        );
        return new CircuitBreakingWeatherProvider(provider, new ProviderCircuit(properties.circuitOpenDuration(), clock));
    }

    private WebClient webClient(WebClient.Builder builder, String baseUrl, Duration timeout) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()));
        return builder.clone()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
