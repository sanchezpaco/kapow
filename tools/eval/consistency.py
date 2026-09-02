import itertools
import json
import os
import sys
from collections import Counter

CRITERIA = ('order', 'framing', 'harmony')


def load_runs(root):
    runs = []
    for name in sorted(os.listdir(root)):
        path = os.path.join(root, name)
        if os.path.isdir(path):
            runs.append({f[:-5]: json.load(open(os.path.join(path, f))) for f in sorted(os.listdir(path)) if f.endswith('.json')})
    return runs


def run(root):
    runs = load_runs(root)
    pages = sorted(set.intersection(*(set(r) for r in runs)))
    print(f"{len(runs)} runs, {len(pages)} pages judged in every run")
    report = {}
    for c in CRITERIA:
        unanimous = 0
        pairwise = []
        rows = []
        for page in pages:
            labels = [r[page][c] for r in runs]
            unanimous += len(set(labels)) == 1
            pairwise += [a == b for a, b in itertools.combinations(labels, 2)]
            rows.append((page, labels))
        report[c] = dict(unanimous=unanimous, pairwise=round(sum(pairwise) / len(pairwise), 3), rows=rows)
        print(f"{c:8s} unanimous {unanimous}/{len(pages)}  pairwise agreement {report[c]['pairwise']:.2f}")
        for page, labels in rows:
            if len(set(labels)) > 1:
                print(f"    {page}: {' / '.join(labels)}")
    stops_named = Counter()
    for page in pages:
        for c in CRITERIA:
            offending = [tuple(sorted(r[page].get(c + '_stops', []))) for r in runs]
            stops_named[len(set(offending)) == 1] += 1
    print(f"offending-stop lists identical across runs: {stops_named[True]}/{sum(stops_named.values())}")
    json.dump(report, open(os.path.join(root, 'consistency.json'), 'w'), indent=1)


if __name__ == '__main__':
    run(sys.argv[1])
