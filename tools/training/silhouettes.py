import sys, os
MODE=sys.argv[1]
if MODE=='candidates':
    import json
    import numpy as np, cv2
    from PIL import Image
    S=sys.argv[2]
    PAGES=[('corpus','benreilly01__Ben Reilly - Spider-Man (2022) 01 (of 05)-014.jpg'),
           ('corpus','onepiece__07.jpg'),
           ('corpus','defensores__Defensores Día D - 090.jpg'),
           ('corpus','spiderman2099__7.jpg'),
           ('corpus','drmuerte__064.jpg'),
           ('doomviz','Doctor Doom 009-016.jpg'),
           ("doomviz","Doctor Doom 009-004.jpg"),
           ("doomviz","Doctor Doom 009-005.jpg")]
    def grown_through_pale(filled,L,A,B,pa,pb,inkD,boxRect):
        bx0,by0,bx1,by1=boxRect
        pale=((L>=165)&(np.abs(A-pa)<=14)&(np.abs(B-pb)<=18)&(inkD==0)).astype(np.uint8)
        inside=np.zeros_like(filled); inside[by0+1:by1-1,bx0+1:bx1-1]=1
        pale&=inside
        seed=filled.copy(); ker=np.ones((3,3),np.uint8)
        while True:
            grown=cv2.dilate(seed,ker)&(pale|filled)
            if (grown==seed).all(): break
            seed=grown
        added=seed&(filled==0)
        ring=np.zeros_like(filled); ring[by0:by1,bx0:bx1]=1; ring[by0+2:by1-2,bx0+2:bx1-2]=0
        if (added&ring).any(): return filled
        return seed
    def border_rect(img, box):
        h,w=img.shape[:2]
        x0,y0,x1,y1=[int(round(v)) for v in (box[0]*w,box[1]*h,box[2]*w,box[3]*h)]
        lab=cv2.cvtColor(img[y0:y1,x0:x1],cv2.COLOR_BGR2LAB).astype(np.int16)
        green=((lab[...,0]<200)&(lab[...,1]<115)).astype(np.uint8)
        green=cv2.dilate(green,np.ones((5,5),np.uint8))
        n,lab_,stats,_=cv2.connectedComponentsWithStats(green)
        i=1+int(np.argmax(stats[1:,cv2.CC_STAT_AREA]))
        gx,gy,gw,gh=stats[i,:4]
        pts=[(gx,gy),(gx+gw,gy),(gx+gw,gy+gh),(gx,gy+gh)]
        return [[round((px+x0)/w,4),round((py+y0)/h,4)] for px,py in pts]
    def reclaimed_thin_parts(filled,mask,ink,boxRect):
        bx0,by0,bx1,by1=boxRect
        thinInk=cv2.dilate(ink,np.ones((3,3),np.uint8))
        paper=mask.copy(); paper[thinInk>0]=0
        inside=np.zeros_like(filled); inside[by0+1:by1-1,bx0+1:bx1-1]=1
        paper&=inside
        seed=filled.copy(); ker=np.ones((3,3),np.uint8)
        while True:
            grown=cv2.dilate(seed,ker)&(paper|filled)
            if (grown==seed).all(): break
            seed=grown
        added=seed&(filled==0)
        ring=np.zeros_like(filled); ring[by0:by1,bx0:bx1]=1; ring[by0+2:by1-2,bx0+2:bx1-2]=0
        if (added&ring).any(): return filled
        return seed
    def candidate(img, box, convex=False, clips=()):
        h,w=img.shape[:2]
        x0,y0,x1,y1=[int(round(v)) for v in (box[0]*w,box[1]*h,box[2]*w,box[3]*h)]
        side=min(x1-x0,y1-y0)
        m=int(0.03*max(x1-x0,y1-y0))+3
        cx0,cy0,cx1,cy1=max(0,x0-m),max(0,y0-m),min(w,x1+m),min(h,y1+m)
        crop=img[cy0:cy1,cx0:cx1]
        lab=cv2.cvtColor(crop,cv2.COLOR_BGR2LAB).astype(np.int16)
        L=lab[...,0]; A=lab[...,1]; B=lab[...,2]
        inner=lab[y0-cy0:y1-cy0, x0-cx0:x1-cx0]
        iL=inner[...,0]
        bright=iL[iL>=np.percentile(iL,60)]
        paper=int(np.median(bright)) if bright.size else 240
        sel=iL>=paper-12
        pa=int(np.median(inner[...,1][sel])); pb=int(np.median(inner[...,2][sel]))
        tol=max(14, int((255-paper)*0.5)+8)
        mask=((L>=paper-tol)&(np.abs(A-pa)<=12)&(np.abs(B-pb)<=16)).astype(np.uint8)
        ink=(L<paper-70).astype(np.uint8)
        r=max(2,int(side*0.012))
        inkD=cv2.dilate(ink,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*r+1,2*r+1)))
        k=max(3,int(side*0.05)|1)
        ker=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(k,k))
        closed=cv2.morphologyEx(mask,cv2.MORPH_CLOSE,ker)
        closed[inkD>0]=0
        bx0,by0,bx1,by1=x0-cx0,y0-cy0,x1-cx0,y1-cy0
        boxOnly=np.zeros_like(closed); boxOnly[by0:by1,bx0:bx1]=1
        closed&=boxOnly
        n,lab_,stats,_=cv2.connectedComponentsWithStats(closed)
        best,bestA=0,0
        for i in range(1,n):
            inside=(lab_[by0:by1,bx0:bx1]==i).sum()
            if inside>bestA: best,bestA=i,inside
        comp=(lab_==best).astype(np.uint8)
        cnts,_=cv2.findContours(comp,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
        filled=np.zeros_like(comp); cv2.drawContours(filled,[max(cnts,key=cv2.contourArea)],-1,1,-1)
        kb=max(5,int(side*0.2)|1)
        bays=cv2.morphologyEx(filled,cv2.MORPH_CLOSE,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(kb,kb)))
        allowed=(mask|ink)
        allowed=cv2.morphologyEx(allowed,cv2.MORPH_CLOSE,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*r+1,2*r+1)))
        filled=filled|(bays&allowed)
        n2,lab2=cv2.connectedComponents(filled)
        sizes=[(lab2==i).sum() for i in range(1,n2)]
        filled=(lab2==(1+int(np.argmax(sizes)))).astype(np.uint8)
        cnts,_=cv2.findContours(filled,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
        filled=np.zeros_like(comp); cv2.drawContours(filled,[max(cnts,key=cv2.contourArea)],-1,1,-1)
        filled=grown_through_pale(filled,L,A,B,pa,pb,inkD,(bx0,by0,bx1,by1))
        filled=reclaimed_thin_parts(filled,mask,ink,(bx0,by0,bx1,by1))
        filled=cv2.dilate(filled,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*r+1,2*r+1)))
        for l,t,rr,bb in clips:
            filled[max(0,by0+int(t*(by1-by0))):max(0,by0+int(bb*(by1-by0))), max(0,bx0+int(l*(bx1-bx0))):max(0,bx0+int(rr*(bx1-bx0)))]=0
        cnts,_=cv2.findContours(filled,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
        c=max(cnts,key=cv2.contourArea)
        if convex: c=cv2.convexHull(c)
        hull=cv2.approxPolyDP(c,1.5,True).reshape(-1,2)
        return [[round((px+cx0)/w,4),round((py+cy0)/h,4)] for px,py in hull]
    CONVEX={('corpus','benreilly01__Ben Reilly - Spider-Man (2022) 01 (of 05)-014.jpg',13),('corpus','benreilly01__Ben Reilly - Spider-Man (2022) 01 (of 05)-014.jpg',14),('corpus','benreilly01__Ben Reilly - Spider-Man (2022) 01 (of 05)-014.jpg',19),('corpus','drmuerte__064.jpg',0)}
    EXCLUDE={('corpus','onepiece__07.jpg',7),('corpus','onepiece__07.jpg',12),('corpus','onepiece__07.jpg',13),('corpus','spiderman2099__7.jpg',17)}
    CLIPS={('doomviz','Doctor Doom 009-004.jpg',9):[(-0.2,-0.2,0.55,0.12)],
    ('corpus','benreilly01__Ben Reilly - Spider-Man (2022) 01 (of 05)-014.jpg',3):[(-0.1,-0.2,0.5,0.03)],
    ('corpus','benreilly01__Ben Reilly - Spider-Man (2022) 01 (of 05)-014.jpg',6):[(0.55,0.9,1.1,1.2)],
    ('corpus','benreilly01__Ben Reilly - Spider-Man (2022) 01 (of 05)-014.jpg',23):[(0.5,-0.2,1.1,0.06)]}
    HAND={('corpus','benreilly01__Ben Reilly - Spider-Man (2022) 01 (of 05)-014.jpg',12):[(0.13,0.2),(0.2,0.12),(0.3,0.08),(0.5,0.06),(0.7,0.06),(0.85,0.09),(0.95,0.16),(0.99,0.3),(0.99,0.5),(0.95,0.65),(0.87,0.78),(0.75,0.85),(0.65,0.87),(0.62,0.9),(0.59,1.0),(0.55,0.88),(0.45,0.87),(0.3,0.82),(0.2,0.73),(0.14,0.6),(0.12,0.4)],
    }
    def hand_polygon(img, box, rel):
        h,w=img.shape[:2]
        return [[round((box[0]+fx*(box[2]-box[0]))*1,4),round((box[1]+fy*(box[3]-box[1]))*1,4)] for fx,fy in rel]
    RECT={('doomviz','Doctor Doom 009-005.jpg',0),('doomviz','Doctor Doom 009-005.jpg',3)}
    CLIPS[('corpus','defensores__Defensores Día D - 090.jpg',2)]=[(0.35,-0.2,0.65,0.02),(0.3,0.975,0.7,1.2),(0.97,0.25,1.2,0.55),(-0.2,0.4,0.01,0.6)]
    out=[]
    for d,f in PAGES:
        boxes=[p for p in json.load(open(f'{S}/{d}/boxes.json')) if p['file']==f][0]['bubbles']
        img=cv2.imread(f'{S}/{d}/{f}')
        for i,b in enumerate(boxes):
            if (d,f,i) in EXCLUDE: continue
            if (d,f,i) in HAND: poly=hand_polygon(img,b,HAND[(d,f,i)])
            elif (d,f,i) in RECT: poly=border_rect(img,b)
            else: poly=candidate(img,b,(d,f,i) in CONVEX,CLIPS.get((d,f,i),()))
            out.append({'dir':d,'file':f,'box':i,'polygon':poly})
    json.dump(out,open(os.path.join(S,'silhouette_candidates.json'),'w'))
    print(len(out))
else:
    import json, sys, math
    from PIL import Image, ImageDraw, ImageFont
    S=sys.argv[2]
    src=sys.argv[3]; prefix=sys.argv[4]
    items=json.load(open(src))
    boxes={}
    imgs={}
    T=300
    for it in items:
        key=(it['dir'],it['file'])
        if key not in boxes:
            boxes[key]=[p for p in json.load(open(f"{S}/{it['dir']}/boxes.json")) if p['file']==it['file']][0]['bubbles']
            imgs[key]=Image.open(f"{S}/{it['dir']}/{it['file']}").convert('RGB')
    tiles=[]
    for n,it in enumerate(items):
        key=(it['dir'],it['file']); im=imgs[key]; w,h=im.size
        b=boxes[key][it['box']]
        x0,y0,x1,y1=b[0]*w,b[1]*h,b[2]*w,b[3]*h
        m=0.2*max(x1-x0,y1-y0)+8
        cx0,cy0,cx1,cy1=max(0,x0-m),max(0,y0-m),min(w,x1+m),min(h,y1+m)
        crop=im.crop((int(cx0),int(cy0),int(cx1),int(cy1)))
        d=ImageDraw.Draw(crop)
        d.rectangle((x0-cx0,y0-cy0,x1-cx0,y1-cy0),outline=(0,90,255),width=2)
        poly=[(px*w-cx0,py*h-cy0) for px,py in it['polygon']]
        if len(poly)>2: d.polygon(poly,outline=(0,200,0),width=3)
        s=T/max(crop.size); crop=crop.resize((max(1,int(crop.width*s)),max(1,int(crop.height*s))))
        tile=Image.new('RGB',(T,T+20),(40,40,40)); tile.paste(crop,(0,20))
        ImageDraw.Draw(tile).text((4,3),f"#{n} {it['file'][:18]} b{it['box']}",fill=(255,255,0))
        tiles.append(tile)
    cols=5; per=25
    for s in range(0,len(tiles),per):
        chunk=tiles[s:s+per]; rows=math.ceil(len(chunk)/cols)
        sheet=Image.new('RGB',(cols*T,rows*(T+20)),(0,0,0))
        for i,t in enumerate(chunk): sheet.paste(t,((i%cols)*T,(i//cols)*(T+20)))
        sheet.save(f"{prefix}{s//per}.png")
    print(len(tiles))
