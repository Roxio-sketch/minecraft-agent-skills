"""Contract checks for nine shared ramps across both actual recipe files."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest
from palettes import load_catalog, validate_catalog, validate_palette, apply_palette_id, get_palette

ROOT = Path(__file__).resolve().parent
LABS = (ROOT.parent / 'alchemy-vfx-lab', ROOT.parent / 'fire-coil-lab')

class PaletteContract(unittest.TestCase):
    def setUp(self):
        self.catalog = load_catalog()

    def test_all_nine_preserve_non_colour_parameters_and_pass_target_validation(self):
        for lab in LABS:
            recipe = json.loads((lab / 'current-recipe.json').read_text(encoding='utf-8'))
            spec = importlib.util.spec_from_file_location('target_recipe', lab / 'recipe.py')
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            for palette in self.catalog['palettes']:
                before = copy.deepcopy(recipe)
                changed = apply_palette_id(recipe, self.catalog, palette['id'])
                self.assertEqual(before, recipe, 'Applying colours must not mutate input')
                keys = {'fireCore', 'fireMid', 'fireEdge'} if recipe['schema'].startswith('alchemy-vfx/') else {'coreColor', 'midColor', 'edgeColor'}
                self.assertEqual({k:v for k,v in before.items() if k not in keys}, {k:v for k,v in changed.items() if k not in keys})
                module.validate(changed)

    def test_invalid_catalogs_are_rejected(self):
        cases = []
        for operation in (
            lambda c:c.update(schema='flame-palettes/2'),
            lambda c:c.update(extra=True),
            lambda c:c.update(defaultId='slot10'),
            lambda c:c['palettes'].pop(),
            lambda c:c['palettes'][1].update(id='slot1'),
            lambda c:c['palettes'][1].update(name=' '),
            lambda c:c['palettes'][1].update(fireCore='#fff'),
            lambda c:c['palettes'][1].update(fireEdge=c['palettes'][1]['fireCore']),
            lambda c:c['palettes'][1].update(extra=True),
        ):
            c = copy.deepcopy(self.catalog)
            operation(c)
            cases.append(c)
        for c in cases:
            with self.assertRaises(ValueError):
                validate_catalog(c)
        with self.assertRaises(ValueError):
            get_palette(self.catalog, 'slot10')

    def test_unknown_schema_and_missing_colour_are_rejected(self):
        for recipe in ({'schema':'unrelated/1'}, {'schema':'fire-coil/2', 'coreColor':'#ffffff'}):
            with self.assertRaises(ValueError):
                apply_palette_id(recipe, self.catalog, 'slot1')

    def test_clean_copy_and_canonical_order(self):
        c = copy.deepcopy(self.catalog)
        c['palettes'].reverse()
        c['palettes'][0]['fireCore'] = c['palettes'][0]['fireCore'].upper()
        cleaned = validate_catalog(c)
        self.assertEqual([p['id'] for p in cleaned['palettes']], [f'slot{i}' for i in range(1, 10)])
        cleaned['palettes'][0]['name'] = 'changed'
        self.assertNotEqual(c['palettes'][-1]['name'], 'changed')

if __name__ == '__main__':
    unittest.main()
