import json, os, sys
from device_pipeline import DeviceDetector, device_pages, in_content

directory = sys.argv[1]
detector = DeviceDetector(*sys.argv[2:3])
out = []
for f in sorted(x for x in os.listdir(directory) if x.lower().endswith(('.jpg', '.png'))):
    (_, page, content), = device_pages(f'{directory}/{f}', split_wide=False)
    boxes = [in_content(b, content) for b in detector.detect(page)]
    out.append(dict(file=f, bubbles=boxes))
json.dump(out, open(f'{directory}/boxes.json', 'w'))
print(sum(len(p['bubbles']) for p in out), 'boxes on', len(out), 'pages')
