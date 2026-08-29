import json,os,sys,glob,statistics as st
from paths import GT,PAGES
def iou(a,b):
    l,t=max(a[0],b[0]),max(a[1],b[1]); r,bt=min(a[2],b[2]),min(a[3],b[3])
    if r<=l or bt<=t: return 0.0
    i=(r-l)*(bt-t); return i/((a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-i)
def match(pred,gt,thr):
    used=set(); tp=0
    for p in sorted(pred,key=lambda r:-(r[2]-r[0])*(r[3]-r[1])):
        best=max(((iou(p,g),i) for i,g in enumerate(gt) if i not in used),default=(0,-1))
        if best[0]>=thr: used.add(best[1]); tp+=1
    return tp
def load_gt():
    gt={}
    for f in glob.glob(GT+'/gt_*.json'):
        comic=os.path.basename(f)[3:-5]
        for p in json.load(open(f)): gt[comic+'__'+os.path.splitext(p['file'])[0]]=p
    return gt
def score(name,thr_p=0.5,thr_b=0.3):
    gt=load_gt(); pred={os.path.splitext(p['file'])[0]:p for p in json.load(open(f"{PAGES}/{name}.json"))}
    rows={}
    for key,g in sorted(gt.items()):
        comic=key.split('__')[0]; p=pred.get(key,{'panels':[],'bubbles':[]})
        r=rows.setdefault(comic,dict(pages=0,pg=0,pp=0,ptp=0,bg=0,bp=0,btp=0,pms=[],bms=[],exact=0))
        r['pages']+=1; r['pg']+=len(g['panels']); r['pp']+=len(p['panels']); tp=match(p['panels'],g['panels'],thr_p); r['ptp']+=tp
        if tp==len(g['panels'])==len(p['panels']): r['exact']+=1
        r['bg']+=len(g['bubbles']); r['bp']+=len(p['bubbles']); r['btp']+=match(p['bubbles'],g['bubbles'],thr_b)
        if 'panels_ms' in p: r['pms'].append(p['panels_ms'])
        if 'bubbles_ms' in p: r['bms'].append(p['bubbles_ms'])
    tot=dict(pages=0,pg=0,pp=0,ptp=0,bg=0,bp=0,btp=0,pms=[],bms=[],exact=0)
    for r in rows.values():
        for k in tot: tot[k]=tot[k]+r[k]
    rows['TOTAL']=tot
    def f1(tp,p,g):
        pr=tp/p if p else 0; rc=tp/g if g else 0; return pr,rc,(2*pr*rc/(pr+rc) if pr+rc else 0)
    out=[]
    for comic,r in rows.items():
        pp,pr,pf=f1(r['ptp'],r['pp'],r['pg']); bp,br,bf=f1(r['btp'],r['bp'],r['bg'])
        out.append(dict(comic=comic,pages=r['pages'],exact_pages=r['exact'],panel_P=pp,panel_R=pr,panel_F1=pf,bubble_P=bp,bubble_R=br,bubble_F1=bf,
            panels_ms=st.median(r['pms']) if r['pms'] else None,bubbles_ms=st.median(r['bms']) if r['bms'] else None))
    return out
if __name__=='__main__':
    for name in sys.argv[1:]:
        print(f"== {name}")
        print(f"{'comic':14}{'pg':>3}{'ok':>3} | panelP panelR panelF1 | bubP bubR bubF1 | ms_p ms_b")
        for o in score(name):
            print(f"{o['comic']:14}{o['pages']:3}{o['exact_pages']:3} | {o['panel_P']:.2f}   {o['panel_R']:.2f}   {o['panel_F1']:.2f}   | {o['bubble_P']:.2f} {o['bubble_R']:.2f} {o['bubble_F1']:.2f} | {o['panels_ms']} {o['bubbles_ms']}")
