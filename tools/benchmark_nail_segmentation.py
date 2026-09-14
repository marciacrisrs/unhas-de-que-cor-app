#!/usr/bin/env python3
"""Benchmark a nail-segmentation candidate against hand-labeled masks.

This is intentionally outside the Android build. It keeps model conversion and
research dependencies out of the production app while giving us a reproducible
way to compare candidate models before replacing the current TFLite baseline.

The first candidate supported here is mnemic/nails_seg_yolov8. Its model card
is CC-BY-4.0 and the checkpoint is a YOLOv8 segmentation model. The exported
TFLite artifact must be produced separately and reviewed before app integration.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

MODEL_URL = (
    "https://huggingface.co/mnemic/nails_seg_yolov8/resolve/main/"
    "nails_seg_s_yolov8_v1.pt?download=true"
)
MODEL_SHA256 = "99b7d1c6ceb4bde32d80fe7ae8c8eb809c27d99b55cf9db54b6692afe68f4070"


def load_yolo(model_path: Path) -> Any:
    from ultralytics import YOLO

    return YOLO(str(model_path))


def mask_metrics(pred: Any, truth: Any) -> dict[str, float]:
    import numpy as np

    pred = np.asarray(pred, dtype=bool)
    truth = np.asarray(truth, dtype=bool)
    if pred.shape != truth.shape:
        raise ValueError(f"mask shape mismatch: {pred.shape} != {truth.shape}")

    intersection = np.logical_and(pred, truth).sum()
    union = np.logical_or(pred, truth).sum()
    predicted = pred.sum()
    actual = truth.sum()
    false_positive = np.logical_and(pred, np.logical_not(truth)).sum()

    return {
        "iou": float(intersection / union) if union else 1.0,
        "precision": float(intersection / predicted) if predicted else 0.0,
        "recall": float(intersection / actual) if actual else 0.0,
        "false_positive_ratio": float(false_positive / predicted) if predicted else 0.0,
    }


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def benchmark(model: Any, image_dir: Path, truth_dir: Path) -> list[dict[str, Any]]:
    import cv2
    import numpy as np

    rows: list[dict[str, Any]] = []
    for image_path in sorted(image_dir.glob("*")):
        if image_path.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
            continue
        truth_path = truth_dir / f"{image_path.stem}.png"
        if not truth_path.exists():
            continue

        result = model.predict(source=str(image_path), verbose=False)[0]
        if result.masks is None:
            rows.append({"image": image_path.name, "error": "no_masks"})
            continue

        # The benchmark fixture is one hand/nail target per image. When a
        # candidate returns multiple instances, merge them conservatively.
        masks = result.masks.data.cpu().numpy()
        prediction = np.any(masks > 0.5, axis=0).astype(np.uint8) * 255
        truth = cv2.imread(str(truth_path), cv2.IMREAD_GRAYSCALE)
        if truth is None:
            rows.append({"image": image_path.name, "error": "bad_truth"})
            continue
        prediction = cv2.resize(prediction, (truth.shape[1], truth.shape[0]), interpolation=cv2.INTER_NEAREST)
        rows.append({"image": image_path.name, **mask_metrics(prediction, truth)})
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--images", type=Path, required=True)
    parser.add_argument("--truth", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("nail-segmentation-benchmark.json"))
    args = parser.parse_args()

    actual_sha = sha256(args.model)
    if actual_sha != MODEL_SHA256:
        raise SystemExit(
            "Candidate model SHA-256 mismatch. Expected "
            f"{MODEL_SHA256}, got {actual_sha}."
        )

    model = load_yolo(args.model)
    rows = benchmark(model, args.images, args.truth)
    payload = {
        "candidate": "mnemic/nails_seg_yolov8",
        "model_sha256": actual_sha,
        "model_url": MODEL_URL,
        "results": rows,
    }
    args.output.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(json.dumps(payload, indent=2))


if __name__ == "__main__":
    main()
