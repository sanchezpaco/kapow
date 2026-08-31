import os,random,json
from PIL import Image
from paths import RAW,DATA,GT
HELD_OUT={os.path.splitext(os.path.basename(f))[0] for files in json.load(open(GT+'/sample.json')).values() for f in files}
MAXP=250; SIDE=1024; VAL=0.08
IMAGE_EXT=('.jpg','.jpeg','.png','.webp')
os.makedirs(DATA+'/images/train',exist_ok=True); os.makedirs(DATA+'/images/val',exist_ok=True)
built={f.split('_')[0] for split in ('train','val') for f in os.listdir(f'{DATA}/images/{split}')}
n=0
for series in sorted(os.listdir(RAW)):
    if series in built: print(series,'already built'); continue
    rng=random.Random(series)
    files=sorted(os.path.join(r,f) for r,_,fs in os.walk(RAW+'/'+series) for f in fs if f.lower().endswith(IMAGE_EXT))
    files=[f for f in files if os.path.splitext(os.path.basename(f))[0] not in HELD_OUT]
    before=len(files)
    if len(files)>MAXP: files=files[::len(files)//MAXP][:MAXP]
    for i,f in enumerate(files):
        im=Image.open(f).convert('RGB'); s=SIDE/max(im.size)
        if s<1: im=im.resize((round(im.width*s),round(im.height*s)),Image.LANCZOS)
        split='val' if rng.random()<VAL else 'train'
        im.save(f"{DATA}/images/{split}/{series}_{i:04d}_{os.path.splitext(os.path.basename(f))[0]}.jpg",quality=88); n+=1
    print(series,before,'->',len(files),flush=True)
print('added',n)
open(DATA+'/data.yaml','w').write(f"path: {DATA}\ntrain: images/train\nval: images/val\nnames:\n  0: bubble\n")
