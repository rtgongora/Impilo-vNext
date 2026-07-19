#!/usr/bin/env bash
# =============================================================================
# One-time vendoring of biometric matcher-engine dependencies + models.
#
# The matcher-engine's JAR deps (SourceAFIS, ONNX Runtime) are pinned in
# services/pom.xml and fetched into ~/.m2 by a normal (online) build. Face/iris
# MODELS are binary blobs — NOT committed to git. This script fetches pinned
# model files into a gitignored path the engine reads via env:
#   MATCHER_FACE_MODEL_PATH  (ArcFace / MobileFaceNet ONNX; input 1x3x112x112)
#   MATCHER_IRIS_MODEL_PATH  (optional — iris runs pure-JVM Gabor without one)
#
# Fingerprint (SourceAFIS) and iris (Gabor) need NO model — they are real with
# jars alone. Only face EXTRACTION needs a model; face MATCHING (cosine) is real
# regardless. Run once on a networked host; then builds run offline (mvn -o).
# =============================================================================
set -euo pipefail

MODELS_DIR="${MATCHER_MODELS_DIR:-vendor/models}"
mkdir -p "$MODELS_DIR"

echo "== 1/2 warm ~/.m2 with matcher-engine jar deps (SourceAFIS, ONNX Runtime)"
mvn -q -f services/pom.xml -pl matcher-engine dependency:go-offline

echo "== 2/2 face-embedding model"
FACE_MODEL="$MODELS_DIR/face-embedding.onnx"
if [ -f "$FACE_MODEL" ]; then
  echo "  already present: $FACE_MODEL"
else
  # Pin a face-recognition ONNX model here (e.g. an ArcFace/MobileFaceNet export).
  # Left unset by default: provide MATCHER_FACE_MODEL_URL to fetch, then verify
  # the checksum. Absent → face extraction fails closed (honest, not fabricated).
  if [ -n "${MATCHER_FACE_MODEL_URL:-}" ]; then
    echo "  fetching $MATCHER_FACE_MODEL_URL"
    curl -fsSL "$MATCHER_FACE_MODEL_URL" -o "$FACE_MODEL"
    if [ -n "${MATCHER_FACE_MODEL_SHA256:-}" ]; then
      echo "${MATCHER_FACE_MODEL_SHA256}  ${FACE_MODEL}" | sha256sum -c -
    fi
    echo "  saved: $FACE_MODEL"
  else
    echo "  MATCHER_FACE_MODEL_URL not set — skipping (face extraction stays fail-closed)."
  fi
fi

echo "== done. Set MATCHER_FACE_MODEL_PATH=$FACE_MODEL on the engine to enable face extraction."
