import os,sys,json,random
from PIL import Image,ImageDraw,ImageFont
from paths import DATA,WORK
OUT=WORK+'/review'; CH=25
series,n=sys.argv[1],int(sys.argv[2]); only=sys.argv[3] if len(sys.argv)>3 else None
random.seed(1)
files=sorted(f for f in os.listdir(DATA+'/images/train') if f.startswith(series+'_'))
if only: wanted=set(json.load(open(only))); files=[f for f in files if f in wanted]
files=files[::max(1,len(files)//n)][:n]
os.makedirs(f'{OUT}/{series}',exist_ok=True); listed=[]
for f in files:
    im=Image.open(f'{DATA}/images/train/{f}').convert('RGB'); d=ImageDraw.Draw(im); W,H=im.size
    font=ImageFont.load_default(size=max(14,W//50))
    for k,row in enumerate(r for r in open(f"{DATA}/labels/train/{os.path.splitext(f)[0]}.txt").read().split('\n') if r):
        _,cx,cy,w,h=map(float,row.split()); l,t=(cx-w/2)*W,(cy-h/2)*H
        d.rectangle([l,t,(cx+w/2)*W,(cy+h/2)*H],outline=(0,90,255),width=4); d.text((l+4,t+2),str(k),fill=(255,0,0),font=font)
    im.save(f'{OUT}/{series}/{f}',quality=85); listed.append(f'- {OUT}/{series}/{f}  (size {W}x{H})')
for k in range(0,len(listed),CH):
  tag=f'{series}_{k//CH}'
  open(f'{OUT}/{tag}_prompt.md','w').write(f"""You are reviewing pseudo-labels of a speech-bubble detector on comic pages. Every image below has BLUE rectangles drawn where the detector found a speech balloon, thought balloon or caption/narration box; each rectangle carries a red index number at its top-left corner. Open EACH image with the Read tool and report two things:
1. "add": text-bearing balloons/captions that have NO blue rectangle at all (missed detections). Do not report sound effects, title logos, signs, or boxes that are merely loose/tight.
2. "remove": the index numbers of blue rectangles that are NOT a speech/thought balloon or caption box — sound-effect lettering, title logos, signs, UI/screen graphics, plain art with no balloon. Do not remove a rectangle for being loose or tight, and do not remove rectangles that contain balloon text.
If a page needs neither, omit it.
Coordinates: [left, top, right, bottom] as fractions 0..1 of the image width/height, 3 decimals, of the balloon outline (not the tail); estimate against the size stated.
Output: write ONE file {OUT}/{tag}_corrections.json containing a JSON array: [{{"file": "<image basename>", "add": [[l,t,r,b],...], "remove": [index,...]}}, ...] (empty array if nothing to fix). No prose, no markdown fences in the file. Your final message must be only the word DONE.
Images:
"""+'\n'.join(listed[k:k+CH])+'\n')
print(series,len(files),'pages rendered')
