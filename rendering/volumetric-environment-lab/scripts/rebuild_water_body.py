"""Validate the canonical continuous water body without rewriting renderer code.

Historical revisions generated fragments here. The renderer is now authored in
workbench/vortex/preview.js; an old fragment must never roll it back silently.
"""
from pathlib import Path
source = Path(__file__).resolve().parents[1] / 'workbench/vortex/preview.js'
text = source.read_text(encoding='utf-8')
required = ('struct LiquidPoint', 'liquidBasisAt(', 'liquidWorldFromLocal(',
            'liquidLocalPoint(', 'liquidPatternOmega(', 'liquidCrestPhase(',
            'float vortexLiquidDistance(', 'function makeWaterDrops(){')
missing = [entry for entry in required if entry not in text]
if missing:
    raise SystemExit('Canonical water body is incomplete: ' + ', '.join(missing))
print('Canonical continuous water body verified; renderer left unchanged.')
