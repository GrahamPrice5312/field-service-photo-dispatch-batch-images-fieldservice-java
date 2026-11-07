package dev.infrai.fieldservice;

public interface PhotoGateway {
    String resize(String image, String requestId) throws Exception;

    void publishBatch(String payload, String requestId) throws Exception;
}
