"""Original multi-storey hall with open floor voids and paired stair flights.

Lower roofs are annular eaves; only the top storey gets a complete roof. Stair
core is fixed across levels and separated from the central entrance aisle.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from generate_building import column_lines
from generate_courtyard import walk_paths
from generate_hall import DEFAULT as HALL_DEFAULT, write_schematic
from roof_kernel import make_roof
from voxel_model import Model, split_state, join_state


DEFAULT = {
    "schema": "chinese-tower/1",
    "levels": [
        {"bays_x": [6, 6, 8, 6, 6], "bays_z": [6, 8, 6], "height": 10},
        {"bays_x": [6, 6, 6, 6, 6], "bays_z": [6, 6, 6], "height": 10},
        {"bays_x": [4, 6, 8, 6, 4], "bays_z": [4, 8, 4], "height": 10}
    ],
    "platform_height": 2, "platform_margin": 3, "eave_overhang": 3,
    "eave_rise": 3, "eave_curve": 1.5, "stair_width": 3,
    "top_roof": {"form": "hip_and_gable", "rise": 6, "curve": 1.5, "uplift": 1, "ridge_axis": "x", "gable_inset": 3},
    "palette": {**HALL_DEFAULT["palette"], "interior_stairs": "minecraft:dark_oak_stairs",
                "railing": "minecraft:dark_oak_fence", "floor": "minecraft:dark_oak_planks"},
    "evidence": [],
}


def config_for(user):
    if not isinstance(user, dict) or set(user) - set(DEFAULT): raise ValueError("Unknown tower configuration keys")
    c = copy.deepcopy(DEFAULT); c.update(user)
    for key in ("palette", "top_roof"):
        if not isinstance(user.get(key, {}), dict) or set(user.get(key, {})) - set(DEFAULT[key]): raise ValueError(f"Invalid {key} configuration")
        c[key] = {**DEFAULT[key], **user.get(key, {})}
    if c["schema"] != DEFAULT["schema"]: raise ValueError("Unsupported tower schema")
    if not isinstance(c["levels"], list) or not 2 <= len(c["levels"]) <= 5: raise ValueError("Tower needs 2..5 storeys")
    prior = None
    for level in c["levels"]:
        if not isinstance(level, dict) or set(level) != {"bays_x", "bays_z", "height"}: raise ValueError("Each level needs bays_x, bays_z, height")
        for axis in ("x", "z"):
            bays = level[f"bays_{axis}"]
            if not isinstance(bays, list) or not 1 <= len(bays) <= 9 or any(type(n) is not int or not 4 <= n <= 16 for n in bays):
                raise ValueError("Level bays need 1..9 spacings of 4..16")
            if sum(bays) % 2: raise ValueError("Level axis total must be even")
        if len(level["bays_x"]) % 2 != 1: raise ValueError("Tower entrance currently requires an odd number of X bays")
        dims = sum(level["bays_x"]) // 2, sum(level["bays_z"]) // 2
        if dims[0] < 10 or dims[1] < 7: raise ValueError("Minimum half-spans are X=10,Z=7 for the stair core")
        if prior and any(a > b for a, b in zip(dims, prior)): raise ValueError("Upper storeys cannot grow outward")
        prior = dims
        if type(level["height"]) is not int or not 8 <= level["height"] <= 12: raise ValueError("Floor-to-floor height must be integer 8..12")
    for key, lo, hi in (("platform_height", 1, 5), ("platform_margin", 2, 6), ("eave_overhang", 2, 5), ("eave_rise", 1, 4), ("stair_width", 2, 3)):
        if type(c[key]) is not int or not lo <= c[key] <= hi: raise ValueError(f"{key} must be integer {lo}..{hi}")
    if type(c["eave_curve"]) not in (int,float) or not np.isfinite(c["eave_curve"]) or not 1 <= c["eave_curve"] <= 2.5:
        raise ValueError("eave_curve must be finite 1..2.5")
    for role, state in c["palette"].items():
        base, props = split_state(state)
        if base.endswith(":air"): raise ValueError("Palette cannot contain air")
        suffix = "_stairs" if role in ("interior_stairs", "access_stairs", "roof_stairs") else "_slab" if role == "roof_slab" else "_fence" if role == "railing" else None
        if suffix and (props or not base.endswith(suffix)): raise ValueError(f"Invalid palette.{role}")
        c["palette"][role] = join_state(base, props)
    return c


def frame_seam_cells(levels,roof_info):
    """Explicit perimeter closure and short transfers to existing lower beams."""
    cells=set();anchors=set()
    for low,up in zip(levels,levels[1:]):
        fy,hx,hz=up['floor_y'],up['half_x'],up['half_z'];by=fy-3
        rim={(x,z) for x in range(-hx,hx+1) for z in (-hz,hz)}|{(x,z) for z in range(-hz,hz+1) for x in (-hx,hx)}
        cells.update((x,y,z) for x,z in rim for y in (fy-2,fy-1))
        for x in up['column_x']:
            for z in up['column_z']:
                if abs(x)!=hx and abs(z)!=hz:continue
                if abs(z)==hz:ax,az=x,(1 if z>0 else -1)*low['half_z']
                else:ax,az=(1 if x>0 else -1)*low['half_x'],z
                anchors.add((ax,by,az))
                cells.update((xx,by,zz) for xx in range(min(x,ax),max(x,ax)+1) for zz in range(min(z,az),max(z,az)+1))
                cells.update((x,y,z) for y in range(by,fy))
    top=levels[-1];hx,hz,fy=top['half_x'],top['half_z'],top['floor_y'];by=fy+top['height']-3
    rim={(x,z) for x in range(-hx,hx+1) for z in (-hz,hz)}|{(x,z) for z in range(-hz,hz+1) for x in (-hx,hx)}
    for x,z in rim:
        ry=roof_info['surface_y_rows_zx'][z+roof_info['half_z']][x+roof_info['half_x']]
        cells.update((x,y,z) for y in range(by+1,ry))
    return sorted(cells),sorted(anchors)


def audit_frame_seams(m,levels,roof_info,solid_blocks):
    from tier_junctions import full_cube_flags
    cells,anchors=frame_seam_cells(levels,roof_info);flags=full_cube_flags(m,solid_blocks)
    bad=[list(p) for p in cells if not flags[m.index[m.get(*p)]]]
    broken=[list(p) for p in anchors if not flags[m.index[m.get(*p)]]]
    return {'passed':not bad and not broken,'interfaces':len(levels)-1,'checked_seam_cells':len(cells),
      'checked_anchors':len(anchors),'missing_or_partial_seams':bad,'broken_lower_anchors':broken}


def make_tower(user):
    c = config_for(user); p = c["palette"]
    levels, fy = [], c["platform_height"]
    for i, entry in enumerate(c["levels"]):
        xs, zs = column_lines(entry["bays_x"]), column_lines(entry["bays_z"])
        levels.append({"index": i, "floor_y": fy, "height": entry["height"], "column_x": xs, "column_z": zs,
                       "half_x": xs[-1], "half_z": zs[-1]})
        fy += entry["height"]
    top = levels[-1]
    roof, roof_info = make_roof(half_x=top["half_x"] + c["eave_overhang"], half_z=top["half_z"] + c["eave_overhang"],
                               base_y=top["floor_y"] + top["height"] + 1, **c["top_roof"],
                               family=lambda x,z: {"block":p["roof"],"stairs":p["roof_stairs"],"slab":p["roof_slab"]},
                               trim=p["trim"], gable_block=p["wall"])
    base = levels[0]; ph = c["platform_height"]
    px, pz = base["half_x"] + c["platform_margin"], base["half_z"] + c["platform_margin"]
    bound_x = max(px, base["half_x"] + c["eave_overhang"]) + 1
    bound_z = max(pz, base["half_z"] + c["eave_overhang"]) + 1
    m = Model((-bound_x, 0, -bound_z - ph), (bound_x, int(roof.maximum[1]), bound_z))
    # Every level shares a U-stair core inset from the smallest upper wall.
    sw = c["stair_width"]; stair_x0 = -top["half_x"] + 2
    stair_x1 = stair_x0 + 2 * sw
    max_half_rise = max((level["height"] + 1) // 2 for level in levels[:-1])
    stair_z0 = -(max_half_rise // 2)
    stair_z1 = stair_z0 + max_half_rise + 1
    if stair_x1 + 2 >= 0 or stair_z1 + 2 >= top["half_z"]:
        raise ValueError("Stair core and safety clearance do not fit; enlarge upper storey")
    core = (stair_x0, stair_z0, stair_x1, stair_z1)
    def in_core(x,z): return core[0] <= x <= core[2] and core[1] <= z <= core[3]
    m.box((-px,0,-pz),(px,ph,pz),p["foundation"])
    m.box((-px+1,ph,-pz+1),(px-1,ph,pz-1),p["floor"])
    for j in range(ph+1):
        z=-pz-ph+j
        if j: m.box((-2,0,z),(2,j-1,z),p["foundation"])
        surface=p["foundation"] if j==0 else p["access_stairs"]+"[facing=south,half=bottom,shape=straight,waterlogged=false]"
        m.box((-2,j,z),(2,j,z),surface)
    columns, voids = [], []
    for level in levels:
        idx, fy, height, hx, hz = (level[k] for k in ("index","floor_y","height","half_x","half_z"))
        if idx:
            for x in range(-hx,hx+1):
                for z in range(-hz,hz+1):
                    if not in_core(x,z): m.set(x,fy,z,p["floor"])
            voids.append({"storey":idx,"floor_y":fy,"hole_xz":[*core]})
        xs, zs = level["column_x"], level["column_z"]
        y_top = fy + height - 4
        # Perimeter frame preserves the entire interior stair hall. A full
        # interior column grid would collide with the circulation core.
        perim = [(x,z) for x in xs for z in zs if abs(x)==hx or abs(z)==hz]
        for x,z in perim:
            m.box((x,fy+1,z),(x,y_top,z),p["column"])
            columns.append({"storey":idx,"x":x,"z":z,"y0":fy+1,"y1":y_top})
        for x in range(-hx,hx+1):
            for z in (-hz,hz):
                for y in range(fy+1,y_top+1):
                    if x in xs: continue
                    # Only ground-floor front has a door; upper facades stay
                    # enclosed rather than opening unsupported balcony exits.
                    if idx==0 and z==-hz and abs(x)<=2: continue
                    glass = fy+3 <= y <= y_top-1
                    divider = x%3==0 or y==fy+4
                    m.set(x,y,z,p["beam"] if glass and divider else p["window"] if glass else p["wall"])
        for z in range(-hz+1,hz):
            for x in (-hx,hx):
                for y in range(fy+1,y_top+1):
                    if z in zs: continue
                    glass = fy+3 <= y <= y_top-1
                    m.set(x,y,z,p["beam"] if glass and (z%3==0 or y==fy+4) else p["window"] if glass else p["wall"])
        for z in (-hz,hz): m.box((-hx,y_top+1,z),(hx,y_top+1,z),p["beam"])
        for x in (-hx,hx): m.box((x,y_top+1,-hz),(x,y_top+1,hz),p["beam"])
        # Joists immediately below the next floor, with the same stair void.
        if idx < len(levels)-1:
            for z in range(-hz,hz+1,4):
                for x in range(-hx,hx+1):
                    if not in_core(x,z): m.set(x,fy+height-1,z,p["beam"])

    eaves=[]
    for low, upper in zip(levels, levels[1:]):
        rx,rz=low["half_x"]+c["eave_overhang"],low["half_z"]+c["eave_overhang"]
        ix,iz=upper["half_x"],upper["half_z"]
        base_y=upper["floor_y"]-2
        span=max(1,min(rx-ix,rz-iz))
        heights={}
        for z in range(-rz,rz+1):
            for x in range(-rx,rx+1):
                if abs(x)<=ix and abs(z)<=iz: continue
                d=min(rx-abs(x),rz-abs(z))
                heights[x,z]=base_y+round(c["eave_rise"]*min(1,d/span)**c["eave_curve"])
        for (x,z),y in heights.items():
            neighbor={key:heights[(x+dx,z+dz)] for key,dx,dz in (("north",0,-1),("east",1,0),("south",0,1),("west",-1,0)) if (x+dx,z+dz) in heights}
            low_y=min([y,*neighbor.values()])-1
            m.box((x,low_y,z),(x,y,z),p["roof"])
            options=[key for key,h in neighbor.items() if h==max(neighbor.values())] if neighbor else []
            if len(options)==1 and neighbor[options[0]]>y:
                m.set(x,y,z,p["roof_stairs"]+f"[facing={options[0]},half=bottom,shape=straight,waterlogged=false]")
            if abs(x)==rx or abs(z)==rz:m.set(x,low_y,z,p["trim"])
        eaves.append({"below_storey":upper["index"],"outer_half_xz":[rx,rz],"open_inner_half_xz":[ix,iz],
                      "surface_y_range":[min(heights.values()),max(heights.values())],"roof_cells":len(heights)})
    m.paste(roof)
    # Join each perimeter frame to its ceiling or roof with non-column posts.
    for level in levels:
        idx,fy,height,hx,hz=(level[k] for k in ("index","floor_y","height","half_x","half_z"))
        for col in (v for v in columns if v["storey"]==idx):
            x,z=col["x"],col["z"]
            ceiling = fy+height if idx<len(levels)-1 else roof_info["surface_y_rows_zx"][z+roof_info["half_z"]][x+roof_info["half_x"]]-1
            for y in range(col["y1"]+2,ceiling+1):
                if m.get(x,y,z)=="minecraft:air":m.set(x,y,z,p["beam"])
        if idx==len(levels)-1:
            for z in level["column_z"]:
                for x in range(-hx,hx+1):
                    m.set(x,fy+height-1,z,p["beam"])

    # Close each outside seam before circulation. Preserve existing columns
    # and roof contours; audit again after all stairs and guard rails exist.
    seams,seam_anchors=frame_seam_cells(levels,roof_info)
    from tier_junctions import full_cube_flags
    seam_materials=list({p[k].split('[')[0] for k in ('floor','foundation','beam','column','roof','trim','wall')})
    flags=full_cube_flags(m,seam_materials)
    if any(not flags[m.index[m.get(*pt)]] for pt in seam_anchors):
        raise ValueError('A tower seam transfer lacks its existing lower perimeter beam')
    for pt in seams:
        if m.get(*pt)=='minecraft:air':m.set(*pt,p['beam'])

    flights=[]; guard_points=set()
    for level,upper in zip(levels,levels[1:]):
        fy, height = level["floor_y"],level["height"]
        first=height//2;second=height-first
        # Odd storey heights have a second flight one tread longer. Shift the
        # first run's start so both share the same back landing line.
        landing_z=stair_z0+max_half_rise
        first_start=landing_z-first
        second_end=landing_z-second
        run_cells=[]
        if first_start > stair_z0:
            m.box((stair_x0,fy,stair_z0),(stair_x0+sw-1,fy,first_start-1),p["floor"])
        if second_end > stair_z0:
            m.box((stair_x0+sw+1,upper["floor_y"],stair_z0),
                  (stair_x1,upper["floor_y"],second_end-1),p["floor"])
        for j in range(first):
            z,y=first_start+j,fy+j+1
            for x in range(stair_x0,stair_x0+sw):
                m.set(x,y-1,z,p["beam"])
                m.set(x,y,z,p["interior_stairs"]+"[facing=south,half=bottom,shape=straight,waterlogged=false]")
                run_cells.append([x,y,z])
            guard_points.add((stair_x0-1,y+1,z))
        for x in range(stair_x0,stair_x1+1):
            for z in (landing_z,landing_z+1):
                m.set(x,fy+first,z,p["floor"])
                guard_points.add((x,fy+first+1,landing_z+2))
        for x in (stair_x0-1,stair_x1+1):
            for z in (landing_z,landing_z+1):guard_points.add((x,fy+first+1,z))
        for j in range(second):
            z,y=landing_z-1-j,fy+first+j+1
            for x in range(stair_x0+sw+1,stair_x1+1):
                m.set(x,y-1,z,p["beam"])
                m.set(x,y,z,p["interior_stairs"]+"[facing=north,half=bottom,shape=straight,waterlogged=false]")
                run_cells.append([x,y,z])
            guard_points.add((stair_x1+1,y+1,z))
            # Divider next to the higher flight prevents a sideways drop.
            guard_points.add((stair_x0+sw,y+1,z))
        flights.append({"lower_storey":level["index"],"upper_storey":upper["index"],"floor_y":[fy,upper["floor_y"]],
                        "width":sw,"first_steps":first,"second_steps":second,"stair_cells_xyz":run_cells,
                        "lower_approach":[stair_x0+sw//2,fy,first_start-1],
                        "mid_landing":[stair_x0+sw,fy+first,landing_z],
                        "upper_exit":[stair_x0+sw+1+sw//2,upper["floor_y"],second_end-1]})
        # A narrow solid center spine supports the landing but stops short of
        # its crossing. Air above the two treads remains untouched.
        m.box((stair_x0+sw,fy+1,landing_z),(stair_x0+sw,fy+first-1,landing_z+1),p["beam"])
    for level in levels[1:]:
        y=level["floor_y"]+1
        for z in range(stair_z0,stair_z1+1):
            guard_points.add((stair_x0-1,y,z));guard_points.add((stair_x1+1,y,z))
        for x in range(stair_x0-1,stair_x1+2):guard_points.add((x,y,stair_z1+1))
        # Front rim must be open at the upper flight exit and, on intermediate
        # floors, at the next flight approach. Divider remains protected.
        guard_points.add((stair_x0+sw,y,stair_z0-1))
        if level["index"]==len(levels)-1:
            for x in range(stair_x0,stair_x0+sw):guard_points.add((x,y,stair_z0-1))
    # Guard posts attach vertically to the nearest floor/beam at that position.
    # Only air is filled; columns and stair treads are preserved.
    for x,y,z in sorted(guard_points):
        if m.get(x,y,z)!="minecraft:air":continue
        m.set(x,y,z,p["railing"]+"[east=false,north=false,south=false,waterlogged=false,west=false]")
        below=y-1
        while below>=ph and m.get(x,below,z)=="minecraft:air":
            m.set(x,below,z,p["beam"]);below-=1
    # Fence directions are explicit so the offline view shows continuous rails.
    for x,y,z in sorted(guard_points):
        state=m.get(x,y,z)
        if state.split('[')[0]!=p["railing"]:continue
        props={"waterlogged":"false"}
        for key,dx,dz in (("north",0,-1),("east",1,0),("south",0,1),("west",-1,0)):
            props[key]=str(m.get(x+dx,y,z+dz)!="minecraft:air").lower()
        m.set(x,y,z,join_state(p["railing"],props))
    start=[0,0,-pz-ph]
    targets={f"floor_{level['index']+1}":[2,level["floor_y"],0] for level in levels}
    for f in flights:
        targets[f"landing_{f['lower_storey']+1}"]=f["mid_landing"]
    paths=walk_paths(m,start,targets)
    from tier_junctions import audit_floor_plate
    full_materials=list({p[k].split('[')[0] for k in ('floor','foundation','beam','column')})
    floor_audits=[audit_floor_plate(m,[-v['half_x'], -v['half_z'], v['half_x'], v['half_z']],v['floor_y'],
                    solid_blocks=full_materials,void_xz=[list(core)] if v['index'] else []) for v in levels]
    seam_audit=audit_frame_seams(m,levels,roof_info,seam_materials)
    checks={"closed_perimeter_seams_and_bearings":seam_audit['passed'],
            "outside_to_every_floor_and_landing":paths["all_targets_reached"],
            "face_connected_geometry":label(m.blocks!=0)[1]==1,
            "stair_width_clearance":all(m.get(x,y+1,z)=="minecraft:air" and m.get(x,y+2,z)=="minecraft:air" for f in flights for x,y,z in f["stair_cells_xyz"]),
            "exact_continuous_column_segments":all(m.get(col["x"],y,col["z"])==p["column"] for col in columns for y in range(col["y0"],col["y1"]+1)),
            "designated_guard_positions_occupied":all(m.get(x,y,z)!="minecraft:air" for x,y,z in guard_points),
            "complete_floor_plates_except_stairwell":all(v['passed'] for v in floor_audits)}
    if not all(checks.values()):raise AssertionError(checks)
    return m,{"schema":"chinese-tower-design/1","config":c,"levels":levels,"columns":columns,"eaves":eaves,"top_roof":roof_info,
              "floor_voids":voids,"floor_plate_audits":floor_audits,"frame_seam_audit":seam_audit,"stair_core_xz":list(core),"flights":flights,"guard_posts":len(guard_points),
              "guard_positions_xyz":[list(pt) for pt in sorted(guard_points)],
              "routes":paths,"checks":checks,"source_structure_reuse":False,"historical_reconstruction":False}


def export(config,output,name="tower",render=True):
    if not name or Path(name).name!=name or any(v in name for v in '/\\:'):raise ValueError("Simple filename stem required")
    m,d=make_tower(config);out=Path(output);out.mkdir(parents=True,exist_ok=True)
    schem,npz=out/f"{name}.schem",out/f"{name}.npz"
    write_schematic(m,schem,name,generator="minecraft-chinese-architecture/chinese-tower-1")
    from import_structure import load_structure
    a,p,_,_=load_structure(schem)
    d["checks"]["schematic_state_roundtrip"]=np.array_equal(np.array(p)[a],np.array(m.palette)[m.blocks])
    if not all(d["checks"].values()):raise AssertionError(d["checks"])
    m.save(npz)
    report={"schema":"chinese-tower-report/1","name":name,"design":d,
            "dimensions_xyz":[m.blocks.shape[2],m.blocks.shape[0],m.blocks.shape[1]],"nonair_voxels":int(np.count_nonzero(m.blocks)),
            "schematic_sha256":hashlib.sha256(schem.read_bytes()).hexdigest(),
            "coordinate_convention":{"npz_origin_xyz":m.origin.tolist(),"schematic_offset_xyz":[0,0,0],"mapping":"schematic cell XYZ = design XYZ - NPZ origin"},
            "limits":["Original layered hall; not a historical tower or engineering reconstruction.",
                      "Offline conservative whole-voxel walking only; no Minecraft player collision or live stair traversal.",
                      "Floor frames and supports are game geometry, not structural load calculations. No balconies or furniture."]}
    (out/f"{name}.report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    if render:
        from render_shapes import render as preview
        for view in ("isometric","front","side"):preview(npz,out/f"{name}_{view}.png",view=view)
        # Cutaway is a separate visualization-only artifact; never the schem.
        cut=m.blocks.copy();ox,oy,oz=m.origin
        for level in d["levels"]:
            fy=level["floor_y"]
            cut[fy-oy+1:fy-oy+level["height"]+1,:d["stair_core_xz"][1]-oz,:]=0
        cut[:, :, :d["stair_core_xz"][0]-ox-1]=0
        cutpath=out/f"{name}_cutaway.npz";np.savez_compressed(cutpath,blocks=cut,palette=np.array(m.palette),origin=m.origin)
        preview(cutpath,out/f"{name}_cutaway.png",title=name+" | visualization-only cutaway",camera_turns=1)
    return report


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config",type=Path);parser.add_argument("--out-dir",type=Path,required=True)
    parser.add_argument("--name",default="tower");parser.add_argument("--no-render",action="store_true")
    args=parser.parse_args();r=export(json.loads(args.config.read_text(encoding="utf-8-sig")) if args.config else {},args.out_dir,args.name,not args.no_render)
    print(json.dumps({k:r[k] for k in ("name","dimensions_xyz","nonair_voxels")},ensure_ascii=False))
