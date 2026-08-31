import json,os,sys
from paths import DATA
L=DATA+'/labels/train'
added=removed=0
for path in sys.argv[1:]:
    for p in json.load(open(path)):
        remove=set(p.get('remove',[]))
        rows=[f"0 {(l+r)/2:.5f} {(t+b)/2:.5f} {r-l:.5f} {b-t:.5f}" for l,t,r,b in p.get('add',[])]
        if not rows and not remove: continue
        f=f"{L}/{os.path.splitext(p['file'])[0]}.txt"; cur=[r for r in open(f).read().split('\n') if r]
        kept=[r for k,r in enumerate(cur) if k not in remove]; removed+=len(cur)-len(kept)
        open(f,'w').write('\n'.join(kept+rows)); added+=len(rows)
print('added',added,'removed',removed)
