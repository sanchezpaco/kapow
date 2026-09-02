import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'training'))

from device_pipeline import DeviceDetector, REPO, device_pages  # noqa: E402

PANELS_MODEL = REPO + '/app/src/main/assets/models/panels.ort'
BUBBLES_MODEL = REPO + '/app/src/main/assets/models/bubbles.ort'
PANEL_CONFIDENCE = 0.35
BUBBLE_CONFIDENCE = 0.25


def detect_comic(comic_dir):
    pages_dir = os.path.join(comic_dir, 'pages')
    panels = DeviceDetector(PANELS_MODEL, PANEL_CONFIDENCE)
    bubbles = DeviceDetector(BUBBLES_MODEL, BUBBLE_CONFIDENCE)
    out = []
    for name in sorted(f for f in os.listdir(pages_dir) if f.lower().endswith(('.jpg', '.png'))):
        (_, analysis, content), = device_pages(os.path.join(pages_dir, name), split_wide=False)
        out.append(dict(
            file=name,
            width=analysis.width,
            height=analysis.height,
            content=[round(v, 4) for v in content],
            panels=panels.detect(analysis),
            bubbles=bubbles.detect(analysis),
        ))
        print(f"{name}: {len(out[-1]['panels'])} panels, {len(out[-1]['bubbles'])} bubbles")
    with open(os.path.join(comic_dir, 'boxes.json'), 'w') as f:
        f.write('[\n' + ',\n'.join(json.dumps(page) for page in out) + '\n]\n')


if __name__ == '__main__':
    detect_comic(sys.argv[1])
