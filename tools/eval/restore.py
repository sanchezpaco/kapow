import json, os, shutil, sys
def R(path): return [[round(x, 3) for x in s] for s in json.load(open(path))['stops']]
root = sys.argv[1]
for comic in sys.argv[2:]:
    restored = []
    rounds = sorted(d for d in os.listdir(f'{root}/{comic}/rounds')) if os.path.isdir(f'{root}/{comic}/rounds') else []
    for f in sorted(os.listdir(f'{root}/{comic}/stops')):
        page = f[:-5]
        judged = f'{root}/{comic}/judged/{f}'
        verdict = f'{root}/{comic}/verdicts/{f}'
        current = R(f'{root}/{comic}/stops/{f}')
        if os.path.exists(verdict) and os.path.exists(judged) and R(judged) == current:
            continue
        for r in reversed(rounds):
            old_stops = f'{root}/{comic}/rounds/{r}/stops/{f}'
            old_verdict = f'{root}/{comic}/rounds/{r}/verdicts/{f}'
            if os.path.exists(old_stops) and os.path.exists(old_verdict) and R(old_stops) == current:
                shutil.copy(old_verdict, verdict); shutil.copy(old_stops, judged); restored.append(f'{page}<{r}'); break
    print(comic, 'restored', len(restored), ' '.join(restored))
