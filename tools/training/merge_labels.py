import os,sys,json
from paths import DATA
MATCH_IOU=0.5
def parse(path):
    if not os.path.exists(path): return []
    return [tuple(map(float,r.split()[1:])) for r in open(path).read().split('\n') if r]
def iou(a,b):
    al,at,ar,ab=a[0]-a[2]/2,a[1]-a[3]/2,a[0]+a[2]/2,a[1]+a[3]/2
    bl,bt,br,bb=b[0]-b[2]/2,b[1]-b[3]/2,b[0]+b[2]/2,b[1]+b[3]/2
    w=max(0,min(ar,br)-max(al,bl)); h=max(0,min(ab,bb)-max(at,bt)); i=w*h
    return i/(a[2]*a[3]+b[2]*b[3]-i) if i else 0
def unmatched(src,against): return [b for b in src if not any(iou(b,o)>=MATCH_IOU for o in against)]
a_dir,b_dir,series=sys.argv[1],sys.argv[2],sys.argv[3:]
disagree={}; merged=0
for split in ('train','val'):
    out=f'{DATA}/labels/{split}'; os.makedirs(out,exist_ok=True)
    for img in sorted(os.listdir(f'{DATA}/images/{split}')):
        if img.split('_')[0] not in series: continue
        stem=os.path.splitext(img)[0]
        a=parse(f'{DATA}/{a_dir}/{split}/{stem}.txt'); b=parse(f'{DATA}/{b_dir}/{split}/{stem}.txt')
        only_b=unmatched(b,a); only_a=unmatched(a,b)
        rows=[f"0 {x:.5f} {y:.5f} {w:.5f} {h:.5f}" for x,y,w,h in a+only_b]
        open(f'{out}/{stem}.txt','w').write('\n'.join(rows)); merged+=len(rows)
        if split=='train' and (only_a or only_b): disagree[img]=dict(only_a=len(only_a),only_b=len(only_b))
json.dump(disagree,open(f'{DATA}/disagreements.json','w'),indent=0)
print('merged boxes',merged,'disagreement pages',len(disagree))
