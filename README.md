# Singapore Weather Service

HTTP service that reports current Singapore weather. Weatherstack is the primary source; OpenWeatherMap is the failover. Results are cached for 3 seconds, and the last successful reading is served if every provider is down.

## Prerequisites

- JDK 21
- A Weatherstack access key and/or an OpenWeatherMap API key (free-tier keys are enough)

Gradle itself must run on JDK 17+. If `java -version` shows an older JDK:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

## Configure API keys

```bash
export WEATHERSTACK_ACCESS_KEY=your_weatherstack_key
export OPENWEATHERMAP_API_KEY=your_openweathermap_key
```

Either key is enough for the service to start. If Weatherstack is missing or failing, requests fail over to OpenWeatherMap. If both are missing and there is no cached value yet, `GET /v1/weather` returns `503`.

## Build, test, and run

```bash
./gradlew test
./gradlew bootRun
```

Then:

```bash
curl "http://localhost:8080/v1/weather?city=singapore"
```

Expected shape:

```json
{
  "wind_speed": 20,
  "temperature_degrees": 29
}
```

- `temperature_degrees` is Celsius
- `wind_speed` is kilometres per hour (Weatherstack metric default; OpenWeatherMap `m/s` is converted)

The city query parameter is accepted for the specified contract, but only Singapore is supported (`400` otherwise). Omit `city` and the service defaults to Singapore.

## Design

```
GET /v1/weather
        │
        ▼
 WeatherController
        │
        ▼
  WeatherService ── fresh cache (< 3s)? return it
        │
        ▼
 Weatherstack ──► OpenWeatherMap   (skip a provider while its circuit is open)
        │
        ├── success: cache and return
        └── both failed: return last successful (stale) value, or 503
```

Adding a provider is a `WeatherProvider` implementation plus a `@Bean` in `WeatherConfig`. Failover order is `@Order` on those beans. Timeouts, cache TTL, circuit cooldown, and API keys live in `application.yaml`.

Reliability choices:

- 2-second connect/response timeouts so a hung provider fails over quickly
- Per-provider circuit that stays open for 30 seconds after a failure
- In-memory last-good cache so customers still get a reading when both APIs are down
- Reactive non-blocking I/O (the project already used WebFlux / WebClient)

## Trade-offs and follow-ups

Left as-is for a short exercise:

- **In-memory cache.** Fresh and stale data are per JVM. Several instances would each call providers after their own 3-second TTL and could serve different stale values. A shared cache (Redis) would be the next step for a multi-instance deployment.
- **No request coalescing.** Concurrent callers after TTL expiry can stampede the providers. A single in-flight fetch per city would prevent that.
- **Integer rounding.** The sample payload uses integers, so values are rounded. Keeping decimals would be more precise.
- **HTTP provider URLs.** The challenge listed `http://` endpoints; both base URLs are configurable if you want HTTPS.
- **Simple circuit, not Resilience4j.** A small `ProviderCircuit` is easy to read and test. A library would add half-open probes, bulkheads, and metrics with more moving parts.
- **Singapore only.** The spec allows a hard-coded city. Supporting more cities would need a cache key per city and provider query parameters.
- **No authentication on this API.** Fine for a take-home; not for a public deployment.

With more time I would add provider success/failure metrics, recorded HTTP contract tests, request coalescing, and a shared cache.
