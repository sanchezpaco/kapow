import numpy as np
import onnxruntime as ort
from PIL import Image
from paths import REPO

ANALYSIS_SIDE = 1000
TARGET_WIDTH = 2160
INPUT_SIDE = 640
LETTERBOX_GRAY = 114
DUPLICATE_IOU = 0.5
BUNDLED_MODEL = REPO + '/app/src/main/assets/models/bubbles.ort'
BUBBLE_CONFIDENCE = 0.25
WIDE_PAGE_ASPECT = 1.0

def in_sample_size(source_width, target_width=TARGET_WIDTH):
    sample = 1
    while source_width // (sample * 2) >= target_width:
        sample *= 2
    return sample

def decoded(path):
    im = Image.open(path).convert('RGB')
    sample = in_sample_size(im.width)
    return im.reduce(sample) if sample > 1 else im

def at_analysis_size(im):
    scale = ANALYSIS_SIDE / min(im.size)
    if scale >= 1:
        return im
    return im.resize((round(im.width * scale), round(im.height * scale)), Image.BILINEAR)


CORNER_TOLERANCE = 24
COLOR_TOLERANCE = 20
BORDER_LINE_FRACTION = 0.99
MIN_MARGIN_FRACTION = 0.01
MIN_CONTENT_FRACTION = 0.5

def _close(a, b, tol):
    return abs(a[0]-b[0]) <= tol and abs(a[1]-b[1]) <= tol and abs(a[2]-b[2]) <= tol

def margin_crop(im):
    import numpy as np
    a = np.asarray(im)
    H, W, _ = a.shape
    corners = [tuple(int(v) for v in a[0,0]), tuple(int(v) for v in a[0,W-1]),
               tuple(int(v) for v in a[H-1,0]), tuple(int(v) for v in a[H-1,W-1])]
    ref = tuple(sum(c[k] for c in corners)//4 for k in range(3))
    if not all(_close(c, ref, CORNER_TOLERANCE) for c in corners):
        return (0.0, 0.0, 1.0, 1.0)
    close = (np.abs(a.astype(int) - np.array(ref)) <= COLOR_TOLERANCE).all(2)
    row_border = close.sum(1) >= np.ceil(BORDER_LINE_FRACTION * W)
    col_border = close.sum(0) >= np.ceil(BORDER_LINE_FRACTION * H)
    top = 0
    while top < H and row_border[top]: top += 1
    if top >= H: return (0.0, 0.0, 1.0, 1.0)
    bottom = H
    while bottom > top and row_border[bottom-1]: bottom -= 1
    left = 0
    while left < W and col_border[left]: left += 1
    right = W
    while right > left and col_border[right-1]: right -= 1
    widest = max(top, H-bottom, left, W-right)
    if widest < MIN_MARGIN_FRACTION * min(W, H): return (0.0, 0.0, 1.0, 1.0)
    if right-left < MIN_CONTENT_FRACTION * W or bottom-top < MIN_CONTENT_FRACTION * H:
        return (0.0, 0.0, 1.0, 1.0)
    return (left/W, top/H, right/W, bottom/H)

def letterboxed(im):
    scale = INPUT_SIDE / max(im.size)
    w, h = round(im.width * scale), round(im.height * scale)
    canvas = Image.new('RGB', (INPUT_SIDE, INPUT_SIDE), (LETTERBOX_GRAY,) * 3)
    ox, oy = (INPUT_SIDE - w) // 2, (INPUT_SIDE - h) // 2
    canvas.paste(im.resize((w, h), Image.BILINEAR), (ox, oy))
    x = np.asarray(canvas, dtype=np.float32).transpose(2, 0, 1)[None] / 255.0
    return x, scale, ox, oy

def iou(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    i = ix * iy
    u = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - i
    return i / u if u > 0 else 0

def merged_duplicates(boxes):
    boxes = [list(b) for b in boxes]
    changed = True
    while changed:
        changed = False
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                if iou(boxes[i], boxes[j]) >= DUPLICATE_IOU:
                    a, b = boxes[i], boxes[j]
                    boxes[i] = [min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3])]
                    del boxes[j]
                    changed = True
                    break
            if changed:
                break
    return boxes

class DeviceDetector:
    def __init__(self, model=BUNDLED_MODEL, confidence=BUBBLE_CONFIDENCE):
        self.session = ort.InferenceSession(model, providers=['CPUExecutionProvider'])
        self.confidence = confidence

    def detect(self, analysis_image):
        x, scale, ox, oy = letterboxed(analysis_image)
        W, H = analysis_image.size
        y = self.session.run(None, {'images': x})[0][0]
        y = y[(y[:, 4] >= self.confidence) & (y[:, 5] == 0)]
        boxes = [[
            round(float(max(0, (r[0] - ox) / scale / W)), 4),
            round(float(max(0, (r[1] - oy) / scale / H)), 4),
            round(float(min(1, (r[2] - ox) / scale / W)), 4),
            round(float(min(1, (r[3] - oy) / scale / H)), 4),
        ] for r in y]
        return merged_duplicates(boxes)

def _cropped_analysis(im):
    l, t, r, b = margin_crop(im)
    W, H = im.size
    box = (round(l*W), round(t*H), round(r*W), round(b*H))
    cropped = im.crop(box) if (l, t, r, b) != (0.0, 0.0, 1.0, 1.0) else im
    return at_analysis_size(cropped), (l, t, r, b)

def device_pages(path, split_wide):
    source = decoded(path)
    if split_wide and source.width / source.height > WIDE_PAGE_ASPECT:
        half = source.width // 2
        left = source.crop((0, 0, half, source.height))
        right = source.crop((half, 0, source.width, source.height))
        la, lc = _cropped_analysis(left)
        ra, rc = _cropped_analysis(right)
        return [('left', la, lc), ('right', ra, rc)]
    a, c = _cropped_analysis(source)
    return [(None, a, c)]

def in_content(box, content):
    l, t, r, b = content
    cw, ch = (r - l), (b - t)
    if cw <= 0 or ch <= 0: return box
    return [round(l + box[0]*cw, 4), round(t + box[1]*ch, 4), round(l + box[2]*cw, 4), round(t + box[3]*ch, 4)]

def to_source_coords(box, side):
    if side is None:
        return box
    offset = 0.0 if side == 'left' else 0.5
    return [round(box[0] / 2 + offset, 4), box[1], round(box[2] / 2 + offset, 4), box[3]]
