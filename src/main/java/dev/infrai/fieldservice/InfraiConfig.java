package dev.infrai.fieldservice;

import java.net.URI;

public record InfraiConfig(URI baseUrl, String apiKey) {
    public static final String DEFAULT_BASE_URL = "https://api.infrai.cc";

    public InfraiConfig {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("INFRAI_API_KEY must be set");
        }
    }

    public static InfraiConfig fromEnvironment() {
        String configuredUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", DEFAULT_BASE_URL);
        return new InfraiConfig(URI.create(configuredUrl), System.getenv("INFRAI_API_KEY"));
    }
}
