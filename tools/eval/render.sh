#!/bin/bash
# Renders the guided tour (stops, annotated pages, crops) for the given eval comics.
set -e
cd "$(dirname "$0")/../.."
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
for comic in "$@"; do
  dir="$PWD/eval/$comic"
  direction=ltr
  [ -f "$dir/manifest.json" ] && direction=$(tools/eval/venv/bin/python -c "import json,sys;print(json.load(open('$dir/manifest.json'))['direction'])")
  echo "== $comic ($direction)"
  KAPOW_GUIDED_EVAL_DIR="$dir" KAPOW_GUIDED_EVAL_DIRECTION="$direction" \
    ./gradlew --quiet :app:testDebugUnitTest --tests '*GuidedTourVisualizer*' --rerun
done
