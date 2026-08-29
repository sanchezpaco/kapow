import os,sys
from ultralytics import YOLO
from paths import DATA,MODELS,DEVICE
model=YOLO(sys.argv[1] if len(sys.argv)>1 else MODELS+'/teacher.pt')
for split in ('train','val'):
    imgs=DATA+f'/images/{split}'; labs=DATA+f'/labels/{split}'; os.makedirs(labs,exist_ok=True)
    files=sorted(f for f in os.listdir(imgs) if not os.path.exists(f'{labs}/{os.path.splitext(f)[0]}.txt')); total=0
    for i in range(0,len(files),16):
        batch=[imgs+'/'+f for f in files[i:i+16]]
        for f,r in zip(files[i:i+16],model.predict(batch,imgsz=640,conf=0.25,verbose=False,device=DEVICE)):
            rows=[f"0 {b[0]:.5f} {b[1]:.5f} {b[2]:.5f} {b[3]:.5f}" for b,c in zip(r.boxes.xywhn.tolist(),r.boxes.cls.tolist()) if int(c)==0]
            open(f"{labs}/{os.path.splitext(f)[0]}.txt",'w').write('\n'.join(rows)); total+=len(rows)
        if i%320==0: print(split,i,flush=True)
    print(split,'boxes',total)
