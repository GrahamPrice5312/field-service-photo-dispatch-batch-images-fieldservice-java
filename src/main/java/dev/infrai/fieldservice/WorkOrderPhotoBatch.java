package dev.infrai.fieldservice;

import java.util.List;

public record WorkOrderPhotoBatch(String workOrderId, String technicianId, List<Photo> photos) {
    public WorkOrderPhotoBatch {
        photos = List.copyOf(photos);
        if (workOrderId.isBlank() || technicianId.isBlank() || photos.isEmpty()) {
            throw new IllegalArgumentException("A work order, technician, and photos are required");
        }
    }

    public record Photo(String filename, String image) {}
}
