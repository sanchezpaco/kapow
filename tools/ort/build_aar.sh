#!/bin/sh
set -eu
ORT_VERSION=1.20.0
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=${1:?usage: build_aar.sh <work dir>}
ANDROID_SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
ANDROID_NDK=${ANDROID_NDK_HOME:-$ANDROID_SDK/ndk/26.1.10909125}
mkdir -p "$WORK"
[ -d "$WORK/venv" ] || python3 -m venv "$WORK/venv"
"$WORK/venv/bin/pip" install -q "onnxruntime==$ORT_VERSION" onnx numpy packaging "cmake>=3.28,<4" ninja flatbuffers
[ -d "$WORK/src" ] || git clone --depth 1 --branch "v$ORT_VERSION" --recursive --shallow-submodules https://github.com/microsoft/onnxruntime.git "$WORK/src"
EIGEN_URL=$(grep '^eigen;' "$WORK/src/cmake/deps.txt" | cut -d';' -f2)
curl -sL -o "$WORK/eigen.zip" "$EIGEN_URL"
EIGEN_SHA=$(shasum -a 1 "$WORK/eigen.zip" | cut -d' ' -f1)
sed -i '' "s#^eigen;\(.*\);.*#eigen;\1;$EIGEN_SHA#" "$WORK/src/cmake/deps.txt"
export PATH="$WORK/venv/bin:$PATH"
cd "$WORK/src"
python tools/ci_build/github/android/build_aar_package.py \
  --android_sdk_path "$ANDROID_SDK" --android_ndk_path "$ANDROID_NDK" \
  --build_dir "$WORK/build" --config MinSizeRel \
  --include_ops_by_config "$HERE/required_operators.config" "$HERE/aar_build_settings.json"
cp "$WORK"/build/aar_out/MinSizeRel/com/microsoft/onnxruntime/onnxruntime-android/*/onnxruntime-android-*.aar "$HERE/../../app/libs/onnxruntime-minimal-$ORT_VERSION.aar"
