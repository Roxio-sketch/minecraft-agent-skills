#!/usr/bin/env python3
"""Behavioral checks for the original Halo authoring tool; no game launch."""
import copy
import json
import math
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from PIL import Image
import author_halo as halo
import compare_small


def basic():
    return {"schema":halo.SCHEMA,"id":"test-halo","size":64,
            "layers":[{"shape":"ring","color":"#60D0FF"}]}


class AuthoringTests(unittest.TestCase):
    def test_presets_normalize_without_mutation(self):
        for path in (Path(__file__).resolve().parents[1]/"assets/presets").glob("*.json"):
            raw=json.loads(path.read_text(encoding="utf-8")); before=copy.deepcopy(raw)
            normalized=halo.normalize(raw)
            self.assertEqual(raw,before)
            self.assertEqual(halo.normalize(normalized),normalized)

    def test_rejects_invalid_inputs(self):
        bad=[]
        for field,value in [("id","../escape"),("size",True),("size",0),("contexts",["ARMOR"]),("layers",[])]:
            raw=basic(); raw[field]=value; bad.append(raw)
        for field,value in [("radius",float("nan")),("opacity",float("inf")),("color","red"),("shape","runes"),
                            ("blend","multiply"),("segments",2.4),("scale",-1),("unknown",1)]:
            raw=basic(); raw["layers"][0][field]=value; bad.append(raw)
        raw=basic(); raw["layers"][0]["motion"]={"period_s":0}; bad.append(raw)
        for raw in bad:
            with self.subTest(raw=raw),self.assertRaises(ValueError): halo.normalize(raw)

    def test_cycle_and_quarter_cycle(self):
        raw=basic(); raw["layers"][0].update(opacity=.5,motion={"period_s":4,"scale_amplitude":.2,"alpha_amplitude":.4})
        layer=halo.normalize(raw)["layers"][0]
        p=halo.pose(layer,1)
        self.assertAlmostEqual(p["scale"],1.2)
        self.assertAlmostEqual(p["opacity"],.7)
        for t in (0,.7,1.3,3.99):
            for k,v in halo.pose(layer,t).items(): self.assertAlmostEqual(v,halo.pose(layer,t+4)[k])

    def test_rotation_sign_and_wrap(self):
        raw=basic(); raw["layers"][0]["motion"]={"speed_deg_s":90}
        layer=halo.normalize(raw)["layers"][0]
        self.assertEqual(halo.pose(layer,1)["rotation_deg"],90)
        self.assertEqual(halo.pose(layer,4)["rotation_deg"],0)
        layer["motion"]["speed_deg_s"]=-90
        self.assertEqual(halo.pose(layer,1)["rotation_deg"],270)

    def test_pose_has_no_frame_history(self):
        recipe=halo.normalize(basic()); layer=recipe["layers"][0]
        target=halo.pose(layer,2.31)
        for t in (.01,.9,9,1.1,9999): halo.pose(layer,t)
        self.assertEqual(halo.pose(layer,2.31),target)

    def test_alpha_clamp_and_positive_scale(self):
        raw=basic(); raw["layers"][0].update(opacity=.9,motion={"alpha_amplitude":1,"scale_amplitude":.45})
        layer=halo.normalize(raw)["layers"][0]
        for i in range(100):
            p=halo.pose(layer,i*.13)
            self.assertGreater(p["scale"],0)
            self.assertTrue(0<=p["opacity"]<=1)

    def test_texture_is_straight_alpha_with_transparent_corners(self):
        layer=halo.normalize(basic())["layers"][0]
        im=halo.texture(layer,64)
        self.assertEqual(im.mode,"RGBA")
        self.assertEqual(im.getpixel((0,0)),(96,208,255,0))
        values=list(im.getchannel("A").get_flattened_data()) if hasattr(im,"get_flattened_data") else list(im.getchannel("A").getdata())
        self.assertGreater(max(values),200)
        self.assertTrue(any(0<v<255 for v in values))
        self.assertEqual(im.tobytes(),halo.texture(layer,64).tobytes())

    def test_opacity_not_baked(self):
        layer=halo.normalize(basic())["layers"][0]
        before=halo.texture(layer,32).tobytes()
        layer["opacity"]=0; layer["scale"]=2
        layer["motion"]["rotation_deg"]=97
        self.assertEqual(before,halo.texture(layer,32).tobytes())

    def test_shape_differences_and_symmetry(self):
        layer=halo.normalize(basic())["layers"][0]
        self.assertLess(halo.coverage(layer,0,0),.01)
        self.assertGreater(halo.coverage(layer,layer["radius"],0),.9)
        self.assertAlmostEqual(halo.coverage(layer,.4,.2),halo.coverage(layer,-.2,.4))
        layer["shape"]="glow"
        self.assertEqual(halo.coverage(layer,0,0),1)
        self.assertEqual(halo.coverage(layer,1,0),0)

    def test_blend_contract(self):
        self.assertEqual(halo.mix_pixel((.2,.4,.6),(1,0,.5),.5,"alpha"),(.6,.2,.55))
        self.assertEqual(halo.mix_pixel((.2,.4,.6),(1,0,.5),.5,"additive"),(.7,.4,.85))
        for mode in ("alpha","additive"):
            self.assertEqual(halo.mix_pixel((.2,.4,.6),(1,0,.5),0,mode),(.2,.4,.6))

    def test_full_duty_ring_has_no_angular_seam(self):
        layer=halo.normalize(basic())["layers"][0]
        layer.update(segments=20,duty=1)
        r=layer["radius"]
        for angle in (0,.001,.2,.9,math.pi):
            self.assertAlmostEqual(halo.coverage(layer,r,0),halo.coverage(layer,r*math.cos(angle),r*math.sin(angle)))

    def test_color_encoding_roundtrip(self):
        for i in range(256): self.assertAlmostEqual(halo.linear_to_srgb(halo.srgb_to_linear(i/255)),i/255)

    def test_fixed_time_and_actual_small_preview(self):
        recipe=halo.normalize(basic())
        self.assertEqual(halo.preview(recipe,16,0).tobytes(),halo.preview(recipe,16,123).tobytes())
        small=halo.preview(recipe,16)
        self.assertEqual(small.size,(16,16))
        self.assertNotEqual(small.tobytes(),halo.preview(recipe,64).resize((16,16)).tobytes())
        recipe["layers"][0]["motion"]["scale_amplitude"]=.2
        self.assertNotEqual(halo.preview(recipe,32,0).tobytes(),halo.preview(recipe,32,1).tobytes())

    def test_export_and_overwrite_protection(self):
        with tempfile.TemporaryDirectory(prefix="halo-check-") as tmp:
            out=Path(tmp)/"new"
            contract=halo.export(halo.normalize(basic()),out)
            for record in contract["textures"]:
                with Image.open(out/record["texture"]) as im: self.assertEqual(im.size,(64,64))
            before=(out/"recipe.json").read_bytes()
            with self.assertRaises(FileExistsError): halo.export(halo.normalize(basic()),out)
            self.assertEqual((out/"recipe.json").read_bytes(),before)
            self.assertFalse(contract["minecraft_parser_included"])

    def test_invalid_cli_writes_nothing(self):
        with tempfile.TemporaryDirectory(prefix="halo-cli-check-") as tmp:
            source=Path(tmp)/"input.json"; out=Path(tmp)/"output"
            raw=basic(); raw["id"]="../escape"
            source.write_text(json.dumps(raw),encoding="utf-8")
            result=subprocess.run([sys.executable,str(Path(halo.__file__)),str(source),"--out",str(out)],capture_output=True)
            self.assertEqual(result.returncode,2)
            self.assertFalse(out.exists())

    def test_ray_inner_radius_and_validation(self):
        raw=basic(); raw["layers"][0].update(shape="rays",inner_radius=.32,segments=6)
        layer=halo.normalize(raw)["layers"][0]
        for angle in (0,.3,2,5):
            self.assertEqual(halo.coverage(layer,.31*math.cos(angle),.31*math.sin(angle)),0)
        self.assertGreater(halo.coverage(layer,.6,0),.1)
        for changes in ({"shape":"ring"},{"inner_radius":.7},{"inner_radius":float("nan")}):
            bad=copy.deepcopy(raw); bad["layers"][0].update(changes)
            with self.assertRaises(ValueError): halo.normalize(bad)

    def test_small_gold_retains_dark_gap(self):
        recipe=compare_small.load("gold-orbit-small")
        for i in range(120):
            result=compare_small.measure(recipe,i*.37)
            with self.subTest(t=result["seconds"]):
                self.assertGreater(min(result["inner_band"],result["outer_band"]),result["gap_band"]*8)
                self.assertLess(result["center"],.01)

    def test_small_red_retains_six_separate_rays(self):
        recipe=compare_small.load("crimson-rays-small")
        for i in range(120):
            result=compare_small.measure(recipe,i*.37)
            with self.subTest(t=result["seconds"]):
                self.assertEqual(result["bright_components"],6)
                self.assertLess(result["center"],.01)

    def test_explicit_display_preview_and_validation(self):
        recipe=compare_small.load("crimson-rays-small")
        with tempfile.TemporaryDirectory(prefix="halo-size-check-") as tmp:
            out=Path(tmp)/"new"
            contract=halo.export(recipe,out,display_px=16)
            with Image.open(out/"preview-target.png") as im:
                self.assertEqual(im.size,(16,16))
                self.assertEqual(im.tobytes(),halo.preview(recipe,16).tobytes())
            self.assertEqual(contract["target_preview"]["canvas_px"],16)
            for size in (True,0,513,16.5):
                bad=Path(tmp)/f"invalid-{size}"
                with self.assertRaises(ValueError): halo.export(recipe,bad,display_px=size)
                self.assertFalse(bad.exists())


if __name__=="__main__": unittest.main(verbosity=2)
