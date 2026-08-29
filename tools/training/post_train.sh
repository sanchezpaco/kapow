#!/bin/bash
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=${KAPOW_TRAINING_WORK:-$HERE/work}
NAME=${1:?usage: post_train.sh <run name> <tag>}
TAG=${2:?usage: post_train.sh <run name> <tag>}
WEIGHTS=$WORK/runs/$NAME/weights
python -c "from ultralytics import YOLO; YOLO('$WEIGHTS/best.pt').export(format='onnx',imgsz=640,nms=True)"
mkdir -p "$WORK/models"
cp "$WEIGHTS/best.pt" "$WORK/models/bubbles_$TAG.pt"
cp "$WEIGHTS/best.onnx" "$WORK/models/bubbles_$TAG.onnx"
cp "$WEIGHTS/best.onnx" "$WORK/models/bubbles.onnx"
python "$HERE/run_onnx.py" bubbles.onnx && cp "$WORK/pages/onnx.json" "$WORK/pages/student_$TAG.json"
python "$HERE/score.py" "student_$TAG"
echo POSTDONE
