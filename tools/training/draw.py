import json,os,sys
from PIL import Image,ImageDraw
from paths import GT,PAGES,WORK
SHEETS=WORK+'/sheets'
def draw_sheet(comic, boxes_by_file, out, W=420):
    sample=json.load(open(GT+'/sample.json'))[comic]
    ims=[]
    for f in sample:
        key=comic+'__'+os.path.splitext(f)[0]
        im=Image.open(f"{PAGES}/{key}.jpg").convert('RGB')
        im=im.resize((W,int(im.height*W/im.width))); d=ImageDraw.Draw(im)
        panels,bubbles=boxes_by_file.get(key,([],[]))
        for i,(l,t,r,b) in enumerate(panels):
            d.rectangle([l*im.width+1,t*im.height+1,r*im.width-2,b*im.height-2],outline=(0,220,0),width=3); d.text((l*im.width+5,t*im.height+4),str(i+1),fill=(255,0,255))
        for (l,t,r,b) in bubbles:
            d.rectangle([l*im.width,t*im.height,r*im.width,b*im.height],outline=(0,90,255),width=2)
        ims.append(im)
    H=max(i.height for i in ims); sheet=Image.new('RGB',(W*len(ims),H),'white')
    for i,im in enumerate(ims): sheet.paste(im,(i*W,0))
    sheet.save(out,quality=80)
if __name__=='__main__':
    src=sys.argv[1]
    os.makedirs(f"{SHEETS}/{src}",exist_ok=True)
    for comic in json.load(open(GT+'/sample.json')):
        if src=='gt':
            d=json.load(open(f"{GT}/gt_{comic}.json")); m={comic+'__'+os.path.splitext(p['file'])[0]:(p['panels'],p['bubbles']) for p in d}
        else:
            d=json.load(open(f"{PAGES}/{src}.json")); m={os.path.splitext(p['file'])[0]:(p['panels'],p['bubbles']) for p in d if p['file'].startswith(comic+'__')}
        draw_sheet(comic,m,f"{SHEETS}/{src}/{comic}.jpg")
