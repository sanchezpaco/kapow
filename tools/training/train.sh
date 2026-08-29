#!/bin/bash
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=${KAPOW_TRAINING_WORK:-$HERE/work}
BASE=${1:?usage: train.sh <base weights .pt> <run name>}
NAME=${2:?usage: train.sh <base weights .pt> <run name>}
cd "$WORK"
yolo detect train model="$BASE" data="$WORK/data/data.yaml" imgsz=640 epochs=60 batch=16 \
  device="${KAPOW_TRAINING_DEVICE:-mps}" project="$WORK/runs" name="$NAME" exist_ok=True seed=0 deterministic=True
