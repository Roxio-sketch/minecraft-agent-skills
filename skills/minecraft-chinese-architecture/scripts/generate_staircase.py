"""Editable broad stairs with deep flat treads and intermediate landings."""
from stair_kernel import make_staircase
from scene_output import export_scene

def export(config,output,name='staircase',render=True):
    model,design=make_staircase(config)
    return export_scene(model,design,output,name,'chinese-staircase',render)
