import json,os,sys,time,numpy as np,onnxruntime as ort
from PIL import Image
from paths import MODELS,PAGES
SZ=640
def letterbox(im):
    W,H=im.size; s=SZ/max(W,H); nw,nh=round(W*s),round(H*s)
    canvas=Image.new('RGB',(SZ,SZ),(114,114,114)); ox,oy=(SZ-nw)//2,(SZ-nh)//2
    canvas.paste(im.resize((nw,nh),Image.BILINEAR),(ox,oy))
    x=np.asarray(canvas,dtype=np.float32).transpose(2,0,1)[None]/255.0
    return x,s,ox,oy
def nms(boxes,scores,thr=0.7):
    order=np.argsort(-scores); keep=[]
    while len(order):
        i=order[0]; keep.append(i)
        if len(order)==1: break
        b=boxes[order[1:]]; xx1=np.maximum(boxes[i,0],b[:,0]); yy1=np.maximum(boxes[i,1],b[:,1]); xx2=np.minimum(boxes[i,2],b[:,2]); yy2=np.minimum(boxes[i,3],b[:,3])
        inter=np.clip(xx2-xx1,0,None)*np.clip(yy2-yy1,0,None); a=(boxes[i,2]-boxes[i,0])*(boxes[i,3]-boxes[i,1]); ab=(b[:,2]-b[:,0])*(b[:,3]-b[:,1])
        order=order[1:][inter/(a+ab-inter)<thr]
    return keep
END_TO_END_DETECTIONS=300
def run(sess,x,conf):
    y=sess.run(None,{'images':x})[0]
    if y.shape[1]==END_TO_END_DETECTIONS:
        d=y[0]; d=d[(d[:,4]>=conf)&(d[:,5]==0)]; return d[:,:4]
    d=y[0].T
    scores=d[:,4:].max(1); cls=d[:,4:].argmax(1); m=(scores>=conf)&(cls==0); d=d[m]; scores=scores[m]
    boxes=np.stack([d[:,0]-d[:,2]/2,d[:,1]-d[:,3]/2,d[:,0]+d[:,2]/2,d[:,1]+d[:,3]/2],1)
    return boxes[nms(boxes,scores)] if len(boxes) else boxes
sp=ort.InferenceSession(f'{MODELS}/panels.onnx'); sb=ort.InferenceSession(f'{MODELS}/'+(sys.argv[1] if len(sys.argv)>1 else 'bubbles.onnx'))
out=[]
for f in sorted(x for x in os.listdir(PAGES) if x.endswith('.jpg')):
    im=Image.open(f"{PAGES}/{f}").convert('RGB'); W,H=im.size
    x,s,ox,oy=letterbox(im)
    def norm(b): return [[round(float(max(0,(r[0]-ox)/s/W)),4),round(float(max(0,(r[1]-oy)/s/H)),4),round(float(min(1,(r[2]-ox)/s/W)),4),round(float(min(1,(r[3]-oy)/s/H)),4)] for r in b]
    t=time.time(); p=run(sp,x,0.35); tp=int((time.time()-t)*1000); t=time.time(); b=run(sb,x,0.25); tb=int((time.time()-t)*1000)
    out.append(dict(file=f,width=W,height=H,panels_ms=tp,bubbles_ms=tb,panels=norm(p),bubbles=norm(b)))
json.dump(out,open(f"{PAGES}/onnx.json",'w'))
