import json, os, sys, time
from device_pipeline import DeviceDetector, device_pages, to_source_coords, in_content
from paths import PAGES
from PIL import Image

name = sys.argv[1] if len(sys.argv) > 1 else 'device'
detector = DeviceDetector(*sys.argv[2:3])
out = []
for f in sorted(x for x in os.listdir(PAGES) if x.endswith('.jpg')):
    path = f'{PAGES}/{f}'
    started = time.time()
    boxes = [to_source_coords(in_content(b, content), side)
             for side, page, content in device_pages(path, split_wide=True)
             for b in detector.detect(page)]
    W, H = Image.open(path).size
    out.append(dict(file=f, width=W, height=H, panels_ms=0, bubbles_ms=int((time.time()-started)*1000), panels=[], bubbles=boxes))
json.dump(out, open(f'{PAGES}/{name}.json', 'w'))
print(len(out), 'pages ->', f'{PAGES}/{name}.json')
