import json
import os
import sys

CRITERIA = ('order', 'framing', 'harmony')


def row(comic_dir):
    scorecard = json.load(open(os.path.join(comic_dir, 'scorecard.json')))
    cells = ['/'.join(str(scorecard['criteria'][c][k]) for k in ('good', 'minor', 'bad')) for c in CRITERIA]
    gates = scorecard['gates']
    return [os.path.basename(comic_dir), str(scorecard['judgedPages']), f"{gates['passed']}/{gates['pages']}"] + cells + \
        [str(scorecard['allGoodPages']), ' '.join(scorecard['anyBadPages']) or '-']


def run(root, comics):
    header = ['comic', 'judged', 'gates', 'order g/m/b', 'framing g/m/b', 'harmony g/m/b', 'all good', 'pages with a bad']
    rows = [row(os.path.join(root, c)) for c in comics if os.path.isfile(os.path.join(root, c, 'scorecard.json'))]
    widths = [max(len(r[i]) for r in [header] + rows) for i in range(len(header))]
    for r in [header] + rows:
        print(' | '.join(cell.ljust(widths[i]) for i, cell in enumerate(r)))


if __name__ == '__main__':
    root = sys.argv[1]
    comics = sys.argv[2:] or sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
    run(root, comics)
