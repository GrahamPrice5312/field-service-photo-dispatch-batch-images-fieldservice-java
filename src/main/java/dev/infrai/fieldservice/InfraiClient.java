package dev.infrai.fieldservice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiClient implements PhotoGateway {
    private static final int MAX_ATTEMPTS = 4;
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    InfraiClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public String resize(String image, String requestId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        HttpRequest imageRequest = HttpRequest.newBuilder(URI.create(image))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> imageResponse = http.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (imageResponse.statusCode() < 200 || imageResponse.statusCode() >= 300) {
            throw new IOException("Image download status " + imageResponse.statusCode());
        }
        body.put("image", Map.of("base64", Base64.getEncoder().encodeToString(imageResponse.body())));
        body.put("width", 1600);
        body.put("height", 1200);
        body.put("fit", "inside");
        body.put("enlarge", false);
        body.put("format", "jpeg");
        body.put("store", true);
        Object data = post("/v1/image/resize", body, requestId);
        if (data instanceof Map<?, ?> result) {
            for (String field : new String[] {"url", "image_id"}) {
                if (result.get(field) instanceof String reference && !reference.isBlank()) {
                    return reference;
                }
            }
        }
        throw new IOException("Resize response contained no image URL or image ID");
    }

    @Override
    public void publishBatch(String payload, String requestId) throws Exception {
        post("/v1/queue/publish", Map.of("queue", "work-order-photos", "payload", Json.parse(payload)), requestId);
    }

    private Object post(String path, Map<String, ?> body, String requestId) throws Exception {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", requestId)
                    .method("POST", HttpRequest.BodyPublishers.ofString(Json.object(body)))
                    .build();
            HttpResponse<String> response = send(request);
            Map<String, Object> envelope = envelope(response.body());
            if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                throw apiError(envelope.get("error"), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport status " + response.statusCode());
            }
            return envelope.get("data");
        }
        throw new IOException("Retry attempts exhausted");
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI resolve(String path) {
        return URI.create(config.baseUrl().toString().replaceAll("/+$", "") + path);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelope(String body) throws IOException {
        try {
            Object parsed = Json.parse(body);
            if (parsed instanceof Map<?, ?> map) return (Map<String, Object>) map;
        } catch (RuntimeException exception) {
            throw new IOException("Response was not a JSON envelope", exception);
        }
        throw new IOException("Response was not a JSON envelope");
    }

    private static InfraiException apiError(Object errorValue, int statusCode) {
        if (errorValue instanceof Map<?, ?> error) {
            String code = String.valueOf(error.containsKey("code") ? error.get("code") : "unknown");
            String message = String.valueOf(error.containsKey("message") ? error.get("message") : "Request rejected");
            return new InfraiException(code, message, statusCode);
        }
        return new InfraiException("unknown", "Request rejected", statusCode);
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiClient::retryAfterMillis)
                .orElse(250L * (1L << attempt));
    }

    private static long retryAfterMillis(String value) {
        try {
            return Math.max(0L, Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException ignored) {
            return 1000L;
        }
    }
}
