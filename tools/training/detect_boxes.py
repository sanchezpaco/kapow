import json, os, sys, numpy as np, onnxruntime as ort
from PIL import Image
from paths import MODELS
DET=640
def letterbox(im):
    W,H=im.size; s=DET/max(W,H); nw,nh=round(W*s),round(H*s)
    c=Image.new('RGB',(DET,DET),(114,114,114)); ox,oy=(DET-nw)//2,(DET-nh)//2
    c.paste(im.resize((nw,nh),Image.BILINEAR),(ox,oy))
    return np.asarray(c,dtype=np.float32).transpose(2,0,1)[None]/255.0,s,ox,oy
def iou(a,b):
    ix=max(0,min(a[2],b[2])-max(a[0],b[0])); iy=max(0,min(a[3],b[3])-max(a[1],b[1])); i=ix*iy
    u=(a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-i
    return i/u if u>0 else 0
def merged(boxes):
    boxes=[list(b) for b in boxes]; changed=True
    while changed:
        changed=False
        for i in range(len(boxes)):
            for j in range(i+1,len(boxes)):
                if iou(boxes[i],boxes[j])>=0.5:
                    a,b=boxes[i],boxes[j]; boxes[i]=[min(a[0],b[0]),min(a[1],b[1]),max(a[2],b[2]),max(a[3],b[3])]; del boxes[j]; changed=True; break
            if changed: break
    return boxes
d=sys.argv[1]; sess=ort.InferenceSession(sys.argv[2] if len(sys.argv)>2 else MODELS+'/bubbles.onnx'); out=[]
for f in sorted(x for x in os.listdir(d) if x.lower().endswith(('.jpg','.png'))):
    im=Image.open(f'{d}/{f}').convert('RGB'); W,H=im.size; x,s,ox,oy=letterbox(im)
    y=sess.run(None,{'images':x})[0][0]; y=y[(y[:,4]>=0.25)&(y[:,5]==0)]
    out.append(dict(file=f,bubbles=merged([[round(float(max(0,(r[0]-ox)/s/W)),4),round(float(max(0,(r[1]-oy)/s/H)),4),round(float(min(1,(r[2]-ox)/s/W)),4),round(float(min(1,(r[3]-oy)/s/H)),4)] for r in y])))
json.dump(out,open(f'{d}/boxes.json','w'))
print(sum(len(p['bubbles']) for p in out),'boxes on',len(out),'pages')
