"""Offline cube previews for blocks[y,z,x] NPZ models, not in-game screenshots."""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


COLORS = {
    "air": (245, 243, 237), "stone_bricks": (133, 139, 139),
    "stone_brick_stairs": (146, 151, 148), "smooth_stone": (172, 174, 165),
    "stripped_dark_oak_log": (92, 62, 43), "dark_oak_planks": (77, 51, 36),
    "white_terracotta": (203, 178, 153), "brown_stained_glass": (114, 113, 100),
    "deepslate_tiles": (57, 69, 74), "deepslate_tile_stairs": (66, 79, 83),
    "deepslate_tile_slab": (79, 91, 92), "cut_copper": (181, 114, 78),
    "oxidized_cut_copper": (79, 142, 122), "prismarine_bricks": (91, 160, 147),
    "prismarine_brick_stairs": (89, 150, 141), "prismarine_brick_slab": (110, 166, 152),
    "red_terracotta": (157, 72, 51), "stripped_spruce_log": (122, 92, 56),
    "spruce_planks": (115, 86, 49), "sandstone": (217, 204, 149),
    "blackstone": (62, 57, 66), "blackstone_stairs": (65, 59, 69), "blackstone_slab": (66, 61, 71),
    "polished_blackstone_bricks": (50, 49, 57), "polished_blackstone_brick_stairs": (53, 51, 60), "polished_blackstone_brick_slab": (55, 53, 62),
    "polished_blackstone": (69, 63, 73), "polished_blackstone_stairs": (72, 66, 77), "polished_blackstone_slab": (74, 68, 78),
    "stripped_mangrove_log": (109, 45, 35), "mangrove_log": (92, 72, 58), "mangrove_planks": (117, 53, 45),
    "calcite": (218, 215, 205), "polished_tuff": (117, 125, 117),
    "polished_andesite": (143, 145, 142), "stripped_spruce_wood": (122, 92, 56),
    "grass_block": (91, 134, 61), "dirt": (134, 96, 67), "coarse_dirt": (121, 90, 63),
    "water": (64, 124, 176), "white_concrete": (220, 222, 216),
    "smooth_quartz": (222, 219, 210), "quartz_block": (220, 217, 208),
    "mud_bricks": (133, 105, 79), "bamboo": (119, 151, 65),
    "packed_mud": (145, 117, 87), "white_wool": (229, 229, 224), "orange_wool": (224, 114, 32),
    "gold_block": (242, 207, 60), "cyan_terracotta": (86, 91, 91), "orange_terracotta": (160, 83, 37),
    "smooth_sandstone": (220, 207, 160), "cut_sandstone": (216, 202, 151),
    "bricks": (151, 97, 81), "brick_stairs": (151, 97, 81), "brick_slab": (151, 97, 81),
    "light_blue_stained_glass": (123, 182, 211), "gray_stained_glass": (90, 96, 105),
}

# Mean RGB of the named vanilla 1.21.4 plank textures; only color samples are
# stored, never the client textures. Related shapes still use approximate color.
PLANK_FAMILY_COLORS = {
    "cherry": (227,179,173), "bamboo": (193,173,80), "dark_oak": (67,43,20),
    "oak": (162,131,79), "spruce": (115,85,49), "jungle": (160,115,81),
    "acacia": (168,90,50), "birch": (192,175,121),
}


def color(state):
    key = state.split(":")[-1].split("[")[0]
    if key in COLORS:
        return COLORS[key]
    # Authored approximate landscape colors, not biome-tinted texture samples.
    if key == 'cherry_leaves': return (218, 153, 170)
    if key == 'flowering_azalea_leaves': return (99, 139, 73)
    if key.endswith('_leaves'): return (75, 120, 55)
    if key in ('short_grass','tall_grass','fern','large_fern','vine','lily_pad'): return (89, 133, 55)
    if any(part in key for part in ("planks", "stairs", "slab", "fence", "trapdoor", "door")):
        for family, rgb in PLANK_FAMILY_COLORS.items():
            if family + "_" in key: return rgb
    if "nether_brick" in key: return (44,22,26)
    if "deepslate_brick" in key: return (63, 68, 77)
    if "polished_deepslate" in key: return (75, 80, 88)
    if "cobbled_deepslate" in key: return (71, 75, 83)
    if "deepslate" in key: return (57, 69, 74)
    if "tuff" in key: return (117, 125, 117)
    if "andesite" in key: return (143, 145, 142)
    if "red_sandstone" in key: return (180, 102, 48)
    if "sandstone" in key: return (217, 204, 149)
    if "glass" in key: return (125,160,165)
    if "copper" in key: return (173,112,78)
    if "stone" in key: return (135,139,132)
    if "wood" in key or "log" in key or "plank" in key: return (123,85,49)
    return (166,151,130)


def shade(rgb, amount):
    return tuple(max(0,min(255,round(c*amount))) for c in rgb)


def font(size):
    for name in ["C:/Windows/Fonts/segoeui.ttf", "DejaVuSans.ttf"]:
        try: return ImageFont.truetype(name,size)
        except OSError: pass
    return ImageFont.load_default()


def canvas(title, size=(1400,1000)):
    im = Image.new("RGB",size,(244,242,235))
    d=ImageDraw.Draw(im)
    d.text((36,24),title,fill=(38,52,52),font=font(28))
    d.text((36,size[1]-52),"Offline geometry preview / cube approximation / not an in-game screenshot",fill=(100,107,104),font=font(17))
    return im


def render_iso(blocks,palette,title,path):
    h,l,w=blocks.shape
    im=canvas(title)
    drawing=ImageDraw.Draw(im)
    points=[]
    # Camera looks from +x, -z and above; sort by depth in that direction.
    ys,zs,xs=np.nonzero(blocks)
    order=np.argsort(xs-zs+ys,kind="stable")
    coords=list(zip(ys[order].tolist(),zs[order].tolist(),xs[order].tolist()))
    scale=min(1260/(w+l),800/(h+(w+l)*0.5))
    xmin=0; xmax=(w+l)*scale
    ymin=(-l*.5-h)*scale; ymax=w*.5*scale
    ox=(im.width-(xmax-xmin))/2-xmin
    oy=(im.height-100-(ymax-ymin))/2-ymin+35
    def project(v):
        x,y,z=v
        return (ox+(x+z)*scale,oy+((x-z)*.5-y)*scale)
    for y,z,x in coords:
        rgb=color(palette[int(blocks[y,z,x])])
        outline=shade(rgb,.68)
        # Back faces omitted; adjacent occupied voxels occlude a whole face.
        if z==0 or blocks[y,z-1,x]==0:
            drawing.polygon([project(v) for v in [(x,y,z),(x+1,y,z),(x+1,y+1,z),(x,y+1,z)]],fill=shade(rgb,.82),outline=outline)
        if x==w-1 or blocks[y,z,x+1]==0:
            drawing.polygon([project(v) for v in [(x+1,y,z),(x+1,y,z+1),(x+1,y+1,z+1),(x+1,y+1,z)]],fill=shade(rgb,.62),outline=outline)
        if y==h-1 or blocks[y+1,z,x]==0:
            drawing.polygon([project(v) for v in [(x,y+1,z),(x+1,y+1,z),(x+1,y+1,z+1),(x,y+1,z+1)]],fill=shade(rgb,1.1),outline=outline)
    im.save(path)


def render_ortho(blocks,palette,title,path,view):
    if view=="front":
        # Nearest non-air block along z, looking into the front doorway.
        count=np.any(blocks!=0,axis=1)
        indexes=np.argmax(blocks!=0,axis=1)
        ys,xs=np.indices(indexes.shape)
        screen=blocks[ys,indexes,xs]
    else:
        rev=blocks[:,:,::-1]
        count=np.any(rev!=0,axis=2)
        indexes=np.argmax(rev!=0,axis=2)
        ys,zs=np.indices(indexes.shape)
        screen=rev[ys,zs,indexes]
    screen=np.where(count,screen,0)
    height,width=screen.shape
    im=canvas(title,(1400,850))
    drawing=ImageDraw.Draw(im)
    scale=min(1250/width,660/height)
    ox=(im.width-width*scale)/2; oy=(im.height-height*scale)/2+6
    for y,x in zip(*np.nonzero(screen)):
        rgb=color(palette[int(screen[y,x])])
        left=ox+x*scale; top=oy+(height-1-y)*scale
        drawing.rectangle((left,top,left+scale,top+scale),fill=rgb,outline=shade(rgb,.81))
    im.save(path)


def render_views(npz,out_dir,name):
    with np.load(npz,allow_pickle=False) as model:
        blocks=model["blocks"]
        palette=model["palette"].tolist()
    out_dir=Path(out_dir);out_dir.mkdir(parents=True,exist_ok=True)
    dims=f"{blocks.shape[2]} x {blocks.shape[0]} x {blocks.shape[1]} blocks"
    render_iso(blocks,palette,f"{name} | isometric | {dims}",out_dir/f"{name}_isometric.png")
    render_ortho(blocks,palette,f"{name} | front | {dims}",out_dir/f"{name}_front.png","front")
    render_ortho(blocks,palette,f"{name} | side | {dims}",out_dir/f"{name}_side.png","side")


if __name__=="__main__":
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("model",type=Path)
    ap.add_argument("--out-dir",type=Path,required=True)
    ap.add_argument("--name",default="hall")
    args=ap.parse_args()
    render_views(args.model,args.out_dir,args.name)
