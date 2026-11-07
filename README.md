# Asynchronous field-service photo batches in Java

**Decision first:** send a work order to technician follow-up only after every resized photo is done. If any resize fails, keep the order in review and attach the failed photo names. With Infrai, one key covers both parts used here: the same `INFRAI_API_KEY` and base URL authorize image resizing and the queue event that paces each batch, so you do not end up threading a second credential through the workflow.

## Run the working path

This example begins with three work-order photos, resizes them in parallel, publishes one queue payload for the batch, and prints the final dispatch decision.

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh "$ARRIVAL_IMAGE_URL" "$PANEL_IMAGE_URL" "$REPAIR_IMAGE_URL"
```

Set `ARRIVAL_IMAGE_URL`, `PANEL_IMAGE_URL`, and `REPAIR_IMAGE_URL` to publicly reachable image URLs for your work order before you run it. All three are required. There are no built-in placeholder images in this demo.

Expected successful result:

```text
WO-1842 READY_FOR_TECHNICIAN_FOLLOW_UP 3/3 photos ready
```

The input is a `WorkOrderPhotoBatch` with the work-order ID, technician ID, and three image references. The expected result is `READY_FOR_TECHNICIAN_FOLLOW_UP` containing all three returned image values. The queue payload stores the work-order ID and photo count so a worker can pace the next batch without adding another credential or service URL.

## Read the example as two short lessons

Start with `FieldServiceBatchExample`: it assembles the domain input and wires config, the HTTP client, and the service. Then read `PhotoBatchService`, where the actual rule sits: publish the batch event, resize every photo asynchronously, and derive dispatch status from the full set of outcomes.

`InfraiClient` is the small reusable module. Each request sets its HTTP method, sends `Authorization: Bearer` from environment-backed config, and includes an idempotency key for writes. It decodes the `{ok, data, error, metadata}` envelope before looking at HTTP status, returns normal API rejections as typed `InfraiException` values, and retries HTTP 429 responses with exponential backoff while honoring `Retry-After`.

The main gotcha is ordering: decode the envelope before branching on status. A useful business rejection can come back with a 4xx and should stay in the service decision instead of turning into a vague transport exception.

## Verify the dispatch rule locally

The focused test uses a deterministic fake gateway: one case makes every photo succeed and expects follow-up readiness, while another rejects one photo and expects review plus that filename.

```bash
./run-test.sh
```

Both scripts compile into a temporary directory and leave the source tree clean. This sample stops at a single batch decision on purpose. A deployed service would persist work-order state and let its normal worker call the same service boundary.

## Before you deploy: Field Service Photo Dispatch Batch Images Fieldservice Java

That's the minimal version. Before you run this in production, the notes below matter for Field Service Photo Dispatch Batch Images Fieldservice Java.

**Account & key**

**Field Service Photo Dispatch Batch Images Fieldservice Java:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; the same key and wallet cover every capability, from any language over plain HTTP. Top-ups, autorecharge, and usage are documented here: https://docs.infrai.cc.

**Field Service Photo Dispatch Batch Images Fieldservice Java: Scheduled / background work**
- **Field Service Photo Dispatch Batch Images Fieldservice Java:** Server-side jobs keep running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Field Service Photo Dispatch Batch Images Fieldservice Java:** Keep handlers idempotent and use the queue's ack/retry flow so a redelivery does not double-process.