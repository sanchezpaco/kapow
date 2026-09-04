import json
import os
import shutil
import sys

CRITERIA = ('order', 'framing', 'harmony')
SCORE = {'good': 2, 'minor': 1, 'bad': 0}
PENALTY = {'good': 0, 'minor': 1, 'bad': 3}
WORST_COUNT = 6


def load_dir(path):
    if not os.path.isdir(path):
        return {}
    return {f[:-5]: json.load(open(os.path.join(path, f))) for f in sorted(os.listdir(path)) if f.endswith('.json')}


def page_score(verdict):
    return sum(SCORE[verdict[c]] for c in CRITERIA)


def page_cost(verdict):
    return sum(PENALTY[verdict[c]] for c in CRITERIA)


def run(comic_dir, label):
    verdicts = load_dir(os.path.join(comic_dir, 'verdicts'))
    gates = json.load(open(os.path.join(comic_dir, 'gates.json')))
    counts = {c: {'good': 0, 'minor': 0, 'bad': 0} for c in CRITERIA}
    for v in verdicts.values():
        for c in CRITERIA:
            counts[c][v[c]] += 1
    judged = len(verdicts)
    ranked = sorted(verdicts.values(), key=lambda v: (page_score(v), v['page']))
    worst = ranked[:WORST_COUNT]
    scorecard = dict(
        label=label,
        judgedPages=judged,
        gates=dict(pages=gates['pages'], passed=gates['passed']),
        criteria={c: dict(counts[c], passRate=round((counts[c]['good'] + counts[c]['minor']) / judged, 3) if judged else None,
                          goodRate=round(counts[c]['good'] / judged, 3) if judged else None) for c in CRITERIA},
        cost=sum(page_cost(v) for v in verdicts.values()),
        costPerPage=round(sum(page_cost(v) for v in verdicts.values()) / judged, 3) if judged else None,
        allGoodPages=sum(1 for v in verdicts.values() if page_score(v) == 6),
        anyBadPages=sorted(v['page'] for v in verdicts.values() if any(v[c] == 'bad' for c in CRITERIA)),
        missedDialoguePages=sorted(v['page'] for v in verdicts.values() if v.get('missed_dialogue')),
        worst=[dict(page=v['page'], score=page_score(v), **{c: v[c] for c in CRITERIA}) for v in worst],
    )
    json.dump(scorecard, open(os.path.join(comic_dir, 'scorecard.json'), 'w'), indent=1)
    write_gallery(comic_dir, worst)
    print(json.dumps({k: scorecard[k] for k in ('label', 'judgedPages', 'gates', 'cost', 'costPerPage', 'criteria', 'allGoodPages', 'anyBadPages', 'missedDialoguePages')}, indent=1))


def write_gallery(comic_dir, worst):
    gallery = os.path.join(comic_dir, 'worst')
    shutil.rmtree(gallery, ignore_errors=True)
    os.makedirs(gallery)
    lines = ['# Worst pages\n']
    for rank, v in enumerate(worst, 1):
        name = f"{rank:02d}-{v['page']}.jpg"
        shutil.copy(os.path.join(comic_dir, 'annotated', v['page'] + '.jpg'), os.path.join(gallery, name))
        lines.append(f"## {rank}. page {v['page']} (score {page_score(v)}/6) — {name}\n")
        for c in CRITERIA:
            lines.append(f"- **{c}: {v[c]}** {v.get(c + '_reason', '')} (stops {v.get(c + '_stops', [])})")
        lines.append(f"- ideal: {v.get('ideal_tour', '')}\n")
    open(os.path.join(gallery, 'index.md'), 'w').write('\n'.join(lines))


if __name__ == '__main__':
    run(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else 'round')
