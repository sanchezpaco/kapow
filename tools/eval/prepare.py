import argparse
import json
import os
import re
import shutil
import subprocess
import tempfile

from PIL import Image

IMAGE_EXTENSIONS = ('.jpg', '.jpeg', '.png', '.webp')
WIDE_PAGE_ASPECT = 1.0
HALF_SUFFIXES = ('a', 'b')


def natural_key(path):
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r'(\d+)', path)]


def extract(archive, into):
    subprocess.run(['unar', '-q', '-o', into, archive], check=True)
    files = [os.path.join(root, f) for root, _, names in os.walk(into) for f in names if f.lower().endswith(IMAGE_EXTENSIONS)]
    return sorted(files, key=lambda f: natural_key(os.path.relpath(f, into)))


def halves(image, direction):
    half = image.width // 2
    left = image.crop((0, 0, half, image.height))
    right = image.crop((half, 0, image.width, image.height))
    return [left, right] if direction == 'ltr' else [right, left]


def prepare(archive, slug, direction, first, last, repo):
    out = os.path.join(repo, 'eval', slug)
    pages_dir = os.path.join(out, 'pages')
    os.makedirs(pages_dir, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        files = extract(archive, tmp)
        manifest = []
        for index in range(first, min(last, len(files) - 1) + 1):
            image = Image.open(files[index]).convert('RGB')
            parts = halves(image, direction) if image.width / image.height > WIDE_PAGE_ASPECT else [image]
            for part, suffix in zip(parts, HALF_SUFFIXES if len(parts) > 1 else ('',)):
                name = f'{index:03d}{suffix}.jpg'
                part.save(os.path.join(pages_dir, name), quality=92)
                manifest.append(dict(file=name, sourceIndex=index, source=os.path.basename(files[index]), split=bool(suffix)))
        json.dump(dict(slug=slug, archive=os.path.basename(archive), direction=direction, sourcePages=len(files), pages=manifest),
                  open(os.path.join(out, 'manifest.json'), 'w'), indent=1)
    print(f'{slug}: {len(manifest)} pages from {first}-{last} of {len(files)} ({direction})')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('archive')
    parser.add_argument('slug')
    parser.add_argument('--direction', choices=('ltr', 'rtl'), default='ltr')
    parser.add_argument('--pages', required=True, help='first-last source page indices, 0-based inclusive')
    args = parser.parse_args()
    first, last = (int(v) for v in args.pages.split('-'))
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    prepare(args.archive, args.slug, args.direction, first, last, repo)
