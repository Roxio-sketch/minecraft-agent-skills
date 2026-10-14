"""Behavioural checks for generated Forge projects and permissive creative inputs."""
import copy, json, pathlib, tempfile
from author_effect import ROOT, validate, validate_path, create

def main():
    count=0
    for p in (ROOT/'assets/presets').glob('*.json'):
        validate(json.loads(p.read_text(encoding='utf8')));count+=1
    default_path=ROOT/'assets/forge-1.20.1/src/main/resources/assets/trailbeam/paths/custom.json'
    validate_path(json.loads(default_path.read_text(encoding='utf8')));count+=1
    seed=json.loads((ROOT/'assets/presets/beam.json').read_text(encoding='utf8'))
    custom=copy.deepcopy(seed);custom['section']='tube';custom['sides']=24
    custom['layers'][0]['colors']=[{'at':0,'color':'#127355','alpha':.4},{'at':.23,'color':'#ff1166','alpha':.8},{'at':1,'color':'#bada55','alpha':.1}]
    custom['layers'][0]['scroll']=-3.75
    custom['layers'][0]['taper']=[{'at':0,'value':0},{'at':.1,'value':2},{'at':.8,'value':.3},{'at':1,'value':0}]
    validate(custom);count+=1
    mutations=[lambda x:x.update({'unparsed_magic':True}),lambda x:x['sampling'].update({'maxPoints':3.2}),lambda x:x['layers'][0].update({'scroll':float('nan')}),lambda x:x['layers'][0].update({'texture':'../bad'}),lambda x:x['layers'][0]['colors'].reverse(),lambda x:x['sampling'].update({'maxStep':0.001}),lambda x:x['layers'][0].update({'fadePower':True})]
    for change in mutations:
        bad=copy.deepcopy(seed);change(bad)
        try:validate(bad)
        except ValueError:count+=1
        else:raise AssertionError('Invalid recipe accepted')
    with tempfile.TemporaryDirectory(prefix='trailbeam-check-') as folder:
        root=pathlib.Path(folder);recipe=root/'input.json';recipe.write_text(json.dumps(custom),encoding='utf8')
        motion=root/'path.json';motion.write_text(default_path.read_text(encoding='utf8'),encoding='utf8')
        project=create(recipe,root/'project',motion)
        assert json.loads((project/'design-recipe.json').read_text(encoding='utf8'))==custom;count+=1
        assert json.loads((project/'design-path.json').read_text(encoding='utf8'))==json.loads(motion.read_text(encoding='utf8'));count+=1
        assert (project/'workbench/index.html').is_file();count+=1
        for name in ['Geometry.java','Effects.java','Recipe.java','BeamRender.java','ClientLab.java']:
            assert (project/'src/main/java/dev/trailbeam'/name).is_file();count+=1
        assert (project/'gradle/wrapper/gradle-wrapper.jar').is_file();count+=1
        keep=project/'user-file.txt';keep.write_text('preserved')
        try:create(recipe,project)
        except FileExistsError:pass
        else:raise AssertionError('Overwrite allowed')
        assert keep.read_text()=='preserved';count+=1
        default_project=create(recipe,root/'default-project')
        assert json.loads((default_project/'design-path.json').read_text(encoding='utf8'))==json.loads(default_path.read_text(encoding='utf8'));count+=1
        invalid=json.loads(motion.read_text(encoding='utf8'));invalid['branches']=5
        try:validate_path(invalid)
        except ValueError:count+=1
        else:raise AssertionError('Invalid path budget accepted')
    print(json.dumps({'status':'passed','checks':count,'scope':'Authoring, schema, project generation; not runtime rendering'}))

if __name__=='__main__':main()
