import json,os,sys,time,re,statistics
from PIL import Image
from ultralytics import YOLO
from paths import PAGES
repo,fname,tag=sys.argv[1],sys.argv[2],sys.argv[3]
imgsz=int(sys.argv[4]) if len(sys.argv)>4 else 1024
conf=float(sys.argv[5]) if len(sys.argv)>5 else 0.25
if os.path.exists(repo): path=repo
else:
    from huggingface_hub import hf_hub_download
    path=hf_hub_download(repo,fname)
model=YOLO(path)
names=model.names; print('classes',names,flush=True)
def kind(n):
    n=n.lower()
    if re.search(r'panel|frame|^comic$',n): return 'panels'
    if re.search(r'bubble|balloon|speech',n): return 'bubbles'
    return None
model.predict(Image.new('RGB',(imgsz,imgsz)),imgsz=imgsz,verbose=False,device='cpu')
out=[]
for f in sorted(x for x in os.listdir(PAGES) if x.endswith('.jpg')):
    im=Image.open(f"{PAGES}/{f}").convert('RGB'); W,H=im.size
    t=time.time(); r=model.predict(im,imgsz=imgsz,conf=conf,verbose=False,device='cpu')[0]; ms=int((time.time()-t)*1000)
    rec=dict(file=f,width=W,height=H,panels_ms=ms,bubbles_ms=0,panels=[],bubbles=[])
    for b,c in zip(r.boxes.xyxy.tolist(),r.boxes.cls.tolist()):
        k=kind(names[int(c)])
        if k: rec[k].append([round(b[0]/W,4),round(b[1]/H,4),round(b[2]/W,4),round(b[3]/H,4)])
    out.append(rec)
json.dump(out,open(f"{PAGES}/{tag}.json",'w'))
print(tag,'median ms',statistics.median(p['panels_ms'] for p in out),'size MB',round(os.path.getsize(path)/1e6,1))
