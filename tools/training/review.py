import os,sys,random
from PIL import Image,ImageDraw
from paths import DATA,WORK
OUT=WORK+'/review'
series,n,CH=sys.argv[1],int(sys.argv[2]),25; random.seed(1)
files=sorted(f for f in os.listdir(DATA+'/images/train') if f.startswith(series+'_'))
files=files[::max(1,len(files)//n)][:n]
os.makedirs(f'{OUT}/{series}',exist_ok=True); listed=[]
for f in files:
    im=Image.open(f'{DATA}/images/train/{f}').convert('RGB'); d=ImageDraw.Draw(im); W,H=im.size
    for row in open(f"{DATA}/labels/train/{os.path.splitext(f)[0]}.txt").read().split('\n'):
        if not row: continue
        _,cx,cy,w,h=map(float,row.split())
        d.rectangle([(cx-w/2)*W,(cy-h/2)*H,(cx+w/2)*W,(cy+h/2)*H],outline=(0,90,255),width=4)
    im.save(f'{OUT}/{series}/{f}',quality=85); listed.append(f'- {OUT}/{series}/{f}  (size {W}x{H})')
for k in range(0,len(listed),CH):
  tag=f'{series}_{k//CH}'
  open(f'{OUT}/{tag}_prompt.md','w').write(f"""You are reviewing pseudo-labels of a speech-bubble detector on comic pages. Every image below has BLUE rectangles drawn where the detector found a speech balloon, thought balloon or caption/narration box. Open EACH image with the Read tool and report ONLY text-bearing balloons/captions that have NO blue rectangle at all (missed detections). Do not report sound effects, title logos, signs, or boxes that are merely loose/tight. If a page has no missed bubble, omit it.
Coordinates: [left, top, right, bottom] as fractions 0..1 of the image width/height, 3 decimals, of the balloon outline (not the tail); estimate against the size stated.
Output: write ONE file {OUT}/{tag}_corrections.json containing a JSON array: [{{"file": "<image basename>", "add": [[l,t,r,b],...]}}, ...] (empty array if nothing is missed). No prose, no markdown fences in the file. Your final message must be only the word DONE.
Images:
"""+'\n'.join(listed[k:k+CH])+'\n')
print(series,len(files),'pages rendered')
