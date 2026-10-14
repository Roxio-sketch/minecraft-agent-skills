from PIL import Image, ImageDraw
from pathlib import Path
p=Path(__file__).parent/'evidence'
frames=sorted(p.glob('overview-*.jpg'))
for page in range(3):
    group=frames[page*16:(page+1)*16]
    if not group: continue
    canvas=Image.new('RGB',(1920,4*300),(14,17,23))
    d=ImageDraw.Draw(canvas)
    for i,f in enumerate(group):
        x=(i%4)*480;y=(i//4)*300
        canvas.paste(Image.open(f),(x,y))
        sec=(int(f.stem.split('-')[-1])-1)*4+2
        d.text((x+10,y+275),f'{sec//60:02}:{sec%60:02}  sample',fill='white')
    canvas.save(p/f'contact-{page+1}.jpg')
