import argparse
import json
import os
import shutil

STOPS_PRECISION = 3


def rounded_stops(path):
    return [[round(v, STOPS_PRECISION) for v in stop] for stop in json.load(open(path))['stops']]


def pending_pages(comic_dir, skip):
    stops_dir = os.path.join(comic_dir, 'stops')
    judged_dir = os.path.join(comic_dir, 'judged')
    verdicts_dir = os.path.join(comic_dir, 'verdicts')
    os.makedirs(judged_dir, exist_ok=True)
    os.makedirs(verdicts_dir, exist_ok=True)
    pending = []
    for name in sorted(f for f in os.listdir(stops_dir) if f.endswith('.json')):
        page = name[:-5]
        if page in skip:
            continue
        current = os.path.join(stops_dir, name)
        verdict = os.path.join(verdicts_dir, name)
        judged = os.path.join(judged_dir, name)
        if os.path.exists(verdict) and os.path.exists(judged) and rounded_stops(judged) == rounded_stops(current):
            continue
        if os.path.exists(verdict):
            os.remove(verdict)
        shutil.copy(current, judged)
        pending.append((page, len(rounded_stops(current))))
    return pending


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='List pages whose tour has no verdict for its current stops; stale verdicts are removed and the stops snapshotted as judged/')
    parser.add_argument('root')
    parser.add_argument('comics', nargs='+')
    parser.add_argument('--skip', nargs='*', default=[], help='comic:page never to judge (covers)')
    args = parser.parse_args()
    skipped = {}
    for entry in args.skip:
        comic, page = entry.split(':')
        skipped.setdefault(comic, set()).add(page)
    total = 0
    for comic in args.comics:
        for page, count in pending_pages(os.path.join(args.root, comic), skipped.get(comic, set())):
            print(f'{comic}:{page}:{count}')
            total += 1
    print(f'{total} pages to judge')
