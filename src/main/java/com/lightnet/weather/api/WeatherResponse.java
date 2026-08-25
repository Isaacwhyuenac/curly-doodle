package com.lightnet.weather.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WeatherResponse(
        @JsonProperty("wind_speed") int windSpeed,
        @JsonProperty("temperature_degrees") int temperatureDegrees
) {
}
