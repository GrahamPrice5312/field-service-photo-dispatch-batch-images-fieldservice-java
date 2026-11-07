package dev.infrai.fieldservice;

import java.util.List;
import java.util.concurrent.Executor;

public final class PhotoBatchServiceTest {
    public static void main(String[] args) throws Exception {
        checkResizeResponses();
        Executor sameThread = Runnable::run;
        WorkOrderPhotoBatch batch = new WorkOrderPhotoBatch("WO-7", "TECH-2", List.of(
                new WorkOrderPhotoBatch.Photo("door.jpg", "image-a"),
                new WorkOrderPhotoBatch.Photo("meter.jpg", "image-b")));

        DispatchResult ready = new PhotoBatchService(new FakeGateway(null), sameThread).process(batch);
        check(ready.status() == DispatchResult.Status.READY_FOR_TECHNICIAN_FOLLOW_UP,
                "complete photos should release follow-up");
        check(ready.processedImages().size() == 2, "both photos should be retained");

        DispatchResult review = new PhotoBatchService(new FakeGateway("image-b"), sameThread).process(batch);
        check(review.status() == DispatchResult.Status.NEEDS_PHOTO_REVIEW,
                "one rejected photo should hold the work order");
        check(review.reviewFilenames().equals(List.of("meter.jpg")),
                "the technician should see the exact photo to review");
        System.out.println("PhotoBatchServiceTest passed");
    }

    private static void checkResizeResponses() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var response = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = response.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var client = new InfraiClient(new InfraiConfig(java.net.URI.create(base), "test-key"));
            response.set("{\"ok\":true,\"data\":{\"url\":\"https://example.test/photo.jpg\","
                    + "\"image_id\":\"image-1\",\"ops_applied\":[\"resize\",{\"nested\":[true,null,[]]}]}}");
            check(client.resize(base + "/input", "test-url").equals("https://example.test/photo.jpg"),
                    "resize should parse arrays and return the URL, not the map");
            response.set("{\"ok\":true,\"data\":{\"image_id\":\"image-1\",\"ops_applied\":[]}}");
            check(client.resize(base + "/input", "test-id").equals("image-1"),
                    "resize should fall back to the image ID");
            response.set("{\"ok\":true,\"data\":{\"ops_applied\":[]}}");
            try {
                client.resize(base + "/input", "test-missing");
                throw new AssertionError("resize should reject missing image references");
            } catch (java.io.IOException expected) {
                check(expected.getMessage().contains("no image URL"), "missing reference error");
            }
        } finally {
            server.stop(0);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeGateway implements PhotoGateway {
        private final String rejectedImage;

        FakeGateway(String rejectedImage) { this.rejectedImage = rejectedImage; }

        @Override
        public String resize(String image, String requestId) throws Exception {
            if (image.equals(rejectedImage)) throw new Exception("Review photo");
            return "processed-" + image;
        }

        @Override
        public void publishBatch(String payload, String requestId) {
            if (!payload.contains("WO-7")) throw new AssertionError("queue payload should identify the work order");
        }
    }
}
