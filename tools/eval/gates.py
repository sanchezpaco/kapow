import json
import os
import sys

NEAR_DUPLICATE_OVERLAP = 0.85
NEAR_DUPLICATE_SIZE_RATIO = 0.5
MAX_STOPS = 12
COVERAGE_EPSILON = 0.005


def area(r):
    return max(0.0, r[2] - r[0]) * max(0.0, r[3] - r[1])


def overlap(a, b):
    return max(0.0, min(a[2], b[2]) - max(a[0], b[0])) * max(0.0, min(a[3], b[3]) - max(a[1], b[1]))


def centre_inside(box, stop):
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    return stop[0] - COVERAGE_EPSILON <= cx <= stop[2] + COVERAGE_EPSILON and stop[1] - COVERAGE_EPSILON <= cy <= stop[3] + COVERAGE_EPSILON


def near_duplicate(a, b):
    smaller, larger = min(area(a), area(b)), max(area(a), area(b))
    return smaller >= NEAR_DUPLICATE_SIZE_RATIO * larger and overlap(a, b) >= NEAR_DUPLICATE_OVERLAP * smaller


def gate_page(page, ml_bubbles):
    stops = page['stops']
    uncovered = [i for i, b in enumerate(ml_bubbles) if not any(centre_inside(b, s) for s in stops)]
    duplicates = [i + 1 for i in range(len(stops) - 1) if near_duplicate(stops[i], stops[i + 1])]
    out_of_bounds = [i for i, s in enumerate(stops) if s[0] < 0 or s[1] < 0 or s[2] > 1 or s[3] > 1 or area(s) <= 0]
    sane = 1 <= len(stops) <= MAX_STOPS
    return dict(
        file=page['file'],
        stops=len(stops),
        needsBubbles=page['needsBubbles'],
        mlBubbles=len(ml_bubbles),
        uncoveredBubbles=uncovered,
        nearDuplicateStops=duplicates,
        outOfBoundsStops=out_of_bounds,
        saneCount=sane,
        passed=not uncovered and not duplicates and not out_of_bounds and sane,
    )


def run(comic_dir):
    ml = {p['file']: p['bubbles'] for p in json.load(open(os.path.join(comic_dir, 'boxes.json')))}
    stops_dir = os.path.join(comic_dir, 'stops')
    results = [gate_page(json.load(open(os.path.join(stops_dir, f))), ml[json.load(open(os.path.join(stops_dir, f)))['file']])
               for f in sorted(os.listdir(stops_dir)) if f.endswith('.json')]
    summary = dict(
        pages=len(results),
        passed=sum(r['passed'] for r in results),
        coverageFailures=[r['file'] for r in results if r['uncoveredBubbles']],
        duplicateFailures=[r['file'] for r in results if r['nearDuplicateStops']],
        boundsFailures=[r['file'] for r in results if r['outOfBoundsStops']],
        countFailures=[r['file'] for r in results if not r['saneCount']],
        pages_detail=results,
    )
    json.dump(summary, open(os.path.join(comic_dir, 'gates.json'), 'w'), indent=1)
    print(f"gates: {summary['passed']}/{summary['pages']} pages pass")
    for r in results:
        if not r['passed']:
            print(f"  {r['file']}: uncovered={r['uncoveredBubbles']} dup={r['nearDuplicateStops']} oob={r['outOfBoundsStops']} count={r['stops']}")


if __name__ == '__main__':
    run(sys.argv[1])
