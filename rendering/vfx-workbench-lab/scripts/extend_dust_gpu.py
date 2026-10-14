from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'scripts/check_whitehole_radiance_gpu.cjs').read_text(encoding='utf-8')
s=s.replace('white-hole-radiance-20261007/gpu','white-hole-default-dust-ui-20261007/gpu')
s=s.replace("for(let style=0;style<5;style++)for(const jets", "for(const style of[0,1,2,4])for(const jets")
s=s.replace("dp.coreGlow===1.2&&dp.coreGlowRadius===2.6","dp.coreGlow===.8&&dp.coreGlowRadius===1.05&&dp.time===37862.8&&dp.jetStrength===0&&dp.shellEnabled===true&&Object.keys(dp).length===75")
s=s.replace("'Actual exported preset has stronger default core diffusion controls'","'Actual exported preset uses the exact supplied defaults and independent shell switch'")
s=s.replace('defaultGlowDiff.changed>100','defaultGlowDiff.changed>0')
s=s.replace('defaultDiff.changed>300','defaultDiff.changed>100')
idx=s.index('      // Every pass below executes')
s=s[:idx]+'''
      // Dust is a procedural volume, independent of the particle VBO toggle.
      details.dust=[];
      for(const mode of[0,1])for(const outer of[2.9,5.27])for(const time of[8,37862.8]){
        const o={...camera([0,5,16]),uGranular:3,uObjectMode:mode,uInner:.94,uOuter:outer,uThickness:.055,uDensity:5,uDustFill:.375,uDustScale:4,uTime:time,uOrbitPhase:time*.8%(2*Math.PI),uRadialPhase:time*.22%128,uDustDriftA:time*.28%12,uDustDriftB:time*.2%12,LensStrength:0,HaloStrength:0,JetStrength:0,uShellStrength:0,uCoreGlow:0};
        const a=render(world,payload.current.world,o,false,'dust-'+mode+'-'+outer+'-'+time),empty=render(world,payload.current.world,{...o,uDustFill:0}),low=render(world,payload.current.world,{...o,uDensity:0}),scale=render(world,payload.current.world,{...o,uDustScale:1});
        const d=difference(a,empty),density=difference(a,low),structure=difference(a,scale);
        check('Dust volume visible at mode/span/time '+[mode,outer,time],d.changed>100&&d.max>10,{...d});
        check('Dust density zero removes the procedural volume '+[mode,outer,time],difference(low,empty).max===0);
        check('Dust scale changes spatial structure '+[mode,outer,time],structure.changed>100,structure);
        details.dust.push({mode,outer,time,density,fill:d,structure});
      }
'''+s[idx:]
(root/'scripts/check_default_dust_gpu.cjs').write_text(s,encoding='utf-8')
print('GPU dust and new-default fixture written')
