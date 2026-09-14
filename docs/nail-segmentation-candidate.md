# Nail segmentation candidate — YOLOv8

## Decision

The current MobileNetV2/DeepLab-style TFLite model remains the production baseline.
A nail-specific YOLOv8 segmentation checkpoint is the first research candidate,
but it is **not wired into Android** yet.

Candidate: `mnemic/nails_seg_yolov8`

- Model: `nails_seg_s_yolov8_v1.pt`
- License declared by the model card: CC-BY-4.0
- SHA-256: `99b7d1c6ceb4bde32d80fe7ae8c8eb809c27d99b55cf9db54b6692afe68f4070`
- Source: https://huggingface.co/mnemic/nails_seg_yolov8

The checkpoint is a YOLOv8 instance-segmentation model, not a drop-in replacement
for the current semantic TFLite decoder. Its mask output therefore needs a
separate decoder/adapter and an explicit mobile export benchmark.

## Why this candidate

The current production model already produces useful nail coverage, but the live
screenshots show a recurring boundary-shape error across fingers. A nail-specific
instance segmentation model is a materially different source of evidence and is
therefore a better experiment than adding another contour heuristic.

A public project also demonstrates the YOLOv8-segmentation → TFLite deployment
path for nail segmentation, but that repository does not declare a license, so
its model is **not** adopted as an application dependency.

## Benchmark protocol

For every candidate, compare against the current baseline using the same fixtures:

1. IoU — region overlap.
2. Precision — resistance to skin spill.
3. Recall — preserved nail plate.
4. Symmetric boundary error in pixels.
5. False-positive ratio.
6. Per-finger results, especially thumb.
7. Inference latency on the target device.

Acceptance gate:

- IoU >= 0.85
- mean boundary error <= 3 px
- false-positive ratio <= 2%
- no regression on thumb
- no material latency regression without a compensating accuracy gain

The Android pipeline must not switch models solely because the new mask looks
smoother. The candidate must beat the baseline on the benchmark fixtures.
