package dev.infrai.fieldservice;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FieldServiceBatchExample {
    private FieldServiceBatchExample() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: ./run-example.sh <arrival-image-url> <panel-image-url> <repair-image-url>");
        }
        InfraiConfig config = InfraiConfig.fromEnvironment();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            PhotoBatchService service = new PhotoBatchService(new InfraiClient(config), executor);
            WorkOrderPhotoBatch batch = new WorkOrderPhotoBatch(
                    "WO-1842",
                    "TECH-27",
                    List.of(
                            new WorkOrderPhotoBatch.Photo("arrival.jpg", args[0]),
                            new WorkOrderPhotoBatch.Photo("panel.jpg", args[1]),
                            new WorkOrderPhotoBatch.Photo("repair.jpg", args[2])));
            DispatchResult result = service.process(batch);
            System.out.printf("%s %s %d/%d photos ready%n",
                    result.workOrderId(), result.status(), result.processedImages().size(), batch.photos().size());
        } finally {
            executor.shutdown();
        }
    }
}
