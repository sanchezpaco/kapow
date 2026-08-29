import json,os,sys
from paths import DATA
L=DATA+'/labels/train'
added=0
for path in sys.argv[1:]:
    for p in json.load(open(path)):
        rows=[f"0 {(l+r)/2:.5f} {(t+b)/2:.5f} {r-l:.5f} {b-t:.5f}" for l,t,r,b in p['add']]
        if not rows: continue
        f=f"{L}/{os.path.splitext(p['file'])[0]}.txt"; cur=open(f).read().rstrip('\n')
        open(f,'w').write((cur+'\n' if cur else '')+'\n'.join(rows)); added+=len(rows)
print('added',added)
