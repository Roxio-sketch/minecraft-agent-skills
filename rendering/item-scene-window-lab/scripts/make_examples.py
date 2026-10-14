"""Original editable scene graphs; examples are starting points, not theme limits."""
from pathlib import Path
import json, math

def box(pos, size, color, **more):
    return dict(type="box", position=pos, scale=size, color=color, **more)

def scene(nodes, **more):
    return dict(version=1, background=[.025,.035,.065], frame=[.8,.57,.24],
                camera="offaxis", eye=[0,0,2.5], resolution=384,
                aperture="rectangle", nodes=nodes, **more)

def crystal():
    # An eight-face triangle mesh, deliberately not assembled from boxes.
    ring=[[.6,0,0],[0,0,.6],[-.6,0,0],[0,0,-.6]]
    faces=[]
    for i in range(4):
        for tip,color in [([0,.85,0],[.12+i*.04,.52+i*.06,.95]),([0,-.85,0],[.1,.25+i*.05,.65])]:
            faces.append(dict(type='mesh',color=color,
                vertices=tip+ring[i]+ring[(i+1)%4]))
    return dict(type='group',position=[.25,0,-3],spin=[0,25,0],children=faces)

def write_examples(destination):
    destination=Path(destination); destination.mkdir(parents=True,exist_ok=True)
    nodes=[box([0,-1,-3],[7,.14,9],[.16,.23,.3])]
    # Foreground gate visibly occludes a distant moving object.
    for x in [-.8,.8]: nodes.append(box([x,0,-.8],[.12,1.9,.15],[.75,.55,.25]))
    nodes.append(box([0,.94,-.8],[1.72,.13,.15],[.75,.55,.25]))
    ring=[box([math.cos(i*math.tau/24)*.85,math.sin(i*math.tau/24)*.85,0],[.1,.1,.1],[.15,.7,.9]) for i in range(24)]
    nodes.append(dict(type="group",position=[0,.1,-3],spin=[0,12,8],children=ring))
    nodes.append(box([0,.1,-3],[.65,.65,.65],[.85,.2,.45],spin=[8,25,0]))
    for x in range(-3,4):nodes.append(box([x*.7,-.88,-3],[.04,.02,8],[.2,.42,.55]))
    obs=scene(nodes)
    garden=scene([box([0,-.85,-2],[6,.2,7],[.16,.3,.18])])
    garden.update(background=[.045,.09,.12],frame=[.2,.7,.45],aperture="circle")
    for x,z,h in [(-.8,-1.5,1),(.75,-2.4,1.6),(-1.4,-4,2),(.05,-3.6,.8)]:
        garden['nodes'] += [box([x,-.8+h/2,z],[.12,h,.12],[.5,.26,.12]),box([x,-.7+h,z],[.65,.55,.65],[.22,.7,.38],spin=[0,6,0])]
    gallery=scene([box([0,-.85,-3],[7,.15,8],[.18,.14,.27]),box([-.3,0,-.65],[.25,1.8,.15],[.96,.7,.2]),crystal()])
    gallery.update(aperture="diamond",frame=[.7,.35,.85])
    custom=json.loads(json.dumps(obs));custom.update(aperture="mask",mask="scenewindow:textures/mask/keyhole.png")
    for name,data in [('observatory',obs),('garden',garden),('gallery',gallery),('keyhole',custom)]:
        (destination/(name+'.json')).write_text(json.dumps(data,indent=2),encoding='utf8')

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('destination');args=parser.parse_args()
    write_examples(args.destination)
