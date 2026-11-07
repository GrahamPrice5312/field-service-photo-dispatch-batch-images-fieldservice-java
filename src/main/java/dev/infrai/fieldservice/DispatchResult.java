package dev.infrai.fieldservice;

import java.util.List;

public record DispatchResult(
        String workOrderId,
        Status status,
        List<String> processedImages,
        List<String> reviewFilenames) {

    public DispatchResult {
        processedImages = List.copyOf(processedImages);
        reviewFilenames = List.copyOf(reviewFilenames);
    }

    public enum Status {
        READY_FOR_TECHNICIAN_FOLLOW_UP,
        NEEDS_PHOTO_REVIEW
    }
}
