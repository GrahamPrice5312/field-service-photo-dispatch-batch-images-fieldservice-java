package dev.infrai.fieldservice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class PhotoBatchService {
    private final PhotoGateway gateway;
    private final Executor executor;

    public PhotoBatchService(PhotoGateway gateway, Executor executor) {
        this.gateway = gateway;
        this.executor = executor;
    }

    public DispatchResult process(WorkOrderPhotoBatch batch) throws Exception {
        String batchKey = "work-order-" + batch.workOrderId();
        String payload = Json.object(java.util.Map.of(
                "workOrderId", batch.workOrderId(),
                "photoCount", batch.photos().size()));
        gateway.publishBatch(payload, batchKey);

        List<CompletableFuture<PhotoOutcome>> tasks = batch.photos().stream()
                .map(photo -> CompletableFuture.supplyAsync(() -> resize(batch, photo), executor))
                .toList();
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();

        List<String> processed = new ArrayList<>();
        List<String> review = new ArrayList<>();
        for (CompletableFuture<PhotoOutcome> task : tasks) {
            PhotoOutcome outcome = task.join();
            if (outcome.processedImage() != null) processed.add(outcome.processedImage());
            else review.add(outcome.filename());
        }
        DispatchResult.Status status = review.isEmpty()
                ? DispatchResult.Status.READY_FOR_TECHNICIAN_FOLLOW_UP
                : DispatchResult.Status.NEEDS_PHOTO_REVIEW;
        return new DispatchResult(batch.workOrderId(), status, processed, review);
    }

    private PhotoOutcome resize(WorkOrderPhotoBatch batch, WorkOrderPhotoBatch.Photo photo) {
        try {
            String requestId = "resize-" + batch.workOrderId() + "-" + photo.filename();
            return new PhotoOutcome(photo.filename(), gateway.resize(photo.image(), requestId));
        } catch (Exception exception) {
            return new PhotoOutcome(photo.filename(), null);
        }
    }

    private record PhotoOutcome(String filename, String processedImage) {}
}
