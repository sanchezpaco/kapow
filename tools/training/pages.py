import os,json,re
from PIL import Image
from paths import RAW,PAGES,GT
SIDE=2000
sample=json.load(open(GT+'/sample.json'))
os.makedirs(PAGES,exist_ok=True)
def series_dir(comic): return RAW+'/'+(comic if os.path.isdir(RAW+'/'+comic) else re.sub(r'\d+$','',comic))
for comic,files in sample.items():
    index={}
    for r,_,fs in os.walk(series_dir(comic)):
        for f in fs: index.setdefault(f,os.path.join(r,f))
    for f in files:
        im=Image.open(series_dir(comic)+'/'+f if '/' in f else index[f]).convert('RGB'); s=SIDE/max(im.size)
        if s<1: im=im.resize((round(im.width*s),round(im.height*s)),Image.LANCZOS)
        im.save(f"{PAGES}/{comic}__{os.path.splitext(os.path.basename(f))[0]}.jpg",quality=90)
print(len([f for f in os.listdir(PAGES) if f.endswith('.jpg')]),'pages')
