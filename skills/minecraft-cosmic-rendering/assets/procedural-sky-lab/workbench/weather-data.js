// Original analytic preview data. These demo biomes are not Minecraft world data.
// Atlas layout and heightmap semantics match the Forge world-data producer.
const CLIMATE_SIZE=16, CLIMATE_CELL=8, CLIMATE_TILES=4, SURFACE_SIZE=128;
const BOXES=[[-22,36,-4,56,79],[15,54,36,83,70],[-52,98,-33,130,88]];

export function previewSurfaceHeight(x,z) {
  let height=60;
  for(const [x0,z0,x1,z1,roof] of BOXES)if(x>=x0&&x<x1&&z>=z0&&z<z1)height=Math.max(height,roof);
  return height;
}

export function previewPrecipitationType(x,y,z,preset='mixed') {
  if(preset==='rain')return [1,0];
  if(preset==='snow')return [0,1];
  if(preset==='dry')return [0,0];
  if(preset==='altitude')return y>=140?[0,1]:[1,0];
  // +X is a cold biome, -X a rainy biome; a southern dry band demonstrates NONE.
  if(z<-24)return [0,0];
  if(x>=12||y>=140)return [0,1];
  return [1,0];
}

export function createWeatherData(gl) {
  const climate=new Float32Array(64*64*4),surface=new Float32Array(SURFACE_SIZE*SURFACE_SIZE*4);
  function texture(width,height,data) {
    const value=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,value);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA32F,width,height,0,gl.RGBA,gl.FLOAT,data);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
    return value;
  }
  const climateTexture=texture(64,64,climate),surfaceTexture=texture(SURFACE_SIZE,SURFACE_SIZE,surface);
  let climateKey='',surfaceKey='',ready=0,origin=[0,0,0],surfaceOrigin=[0,0],preset='mixed',presence=[0,0];
  let climateUploads=0,surfaceUploads=0,lastWork={climateSamples:0,surfaceSamples:0};
  function update({parameters={},position=[0,64,0],biomePreset='mixed'}={}) {
    lastWork={climateSamples:0,surfaceSamples:0};
    if(!position.every(Number.isFinite))throw new Error('天气数据观察位置需要有限数字');
    const active=Number(parameters.precipitationMode||0)!==0;
    if(!active){ready=0;return telemetry();}
    preset=['rain','snow','dry','altitude','mixed'].includes(biomePreset)?biomePreset:'mixed';
    origin=position.map(value=>Math.floor(value/CLIMATE_CELL)*CLIMATE_CELL-CLIMATE_SIZE*CLIMATE_CELL/2);
    surfaceOrigin=[Math.floor(position[0])-SURFACE_SIZE/2,Math.floor(position[2])-SURFACE_SIZE/2];
    const ck=origin.join(',')+'/'+preset,sk=surfaceOrigin.join(',');
    if(ck!==climateKey) {
      presence=[0,0];
      for(let y=0;y<CLIMATE_SIZE;y++)for(let z=0;z<CLIMATE_SIZE;z++)for(let x=0;x<CLIMATE_SIZE;x++) {
        const px=x+(y%CLIMATE_TILES)*CLIMATE_SIZE,py=z+Math.floor(y/CLIMATE_TILES)*CLIMATE_SIZE,index=(py*64+px)*4;
        const type=previewPrecipitationType(origin[0]+(x+.5)*CLIMATE_CELL,origin[1]+(y+.5)*CLIMATE_CELL,origin[2]+(z+.5)*CLIMATE_CELL,preset);
        climate[index]=type[0];climate[index+1]=type[1];climate[index+2]=0;climate[index+3]=1;
        presence[0]=Math.max(presence[0],type[0]);presence[1]=Math.max(presence[1],type[1]);
      }
      gl.bindTexture(gl.TEXTURE_2D,climateTexture);gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,64,64,gl.RGBA,gl.FLOAT,climate);
      climateKey=ck;climateUploads++;lastWork.climateSamples=CLIMATE_SIZE**3;
    }
    if(sk!==surfaceKey) {
      for(let z=0;z<SURFACE_SIZE;z++)for(let x=0;x<SURFACE_SIZE;x++) {
        const index=(z*SURFACE_SIZE+x)*4;
        surface[index]=previewSurfaceHeight(surfaceOrigin[0]+x+.5,surfaceOrigin[1]+z+.5);
        surface[index+1]=1;surface[index+2]=0;surface[index+3]=1;
      }
      gl.bindTexture(gl.TEXTURE_2D,surfaceTexture);gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,SURFACE_SIZE,SURFACE_SIZE,gl.RGBA,gl.FLOAT,surface);
      surfaceKey=sk;surfaceUploads++;lastWork.surfaceSamples=SURFACE_SIZE**2;
    }
    ready=1;return telemetry();
  }
  function bind(locations,firstUnit=2) {
    gl.activeTexture(gl.TEXTURE0+firstUnit);gl.bindTexture(gl.TEXTURE_2D,climateTexture);gl.uniform1i(locations.WeatherVolumeSampler,firstUnit);
    gl.activeTexture(gl.TEXTURE0+firstUnit+1);gl.bindTexture(gl.TEXTURE_2D,surfaceTexture);gl.uniform1i(locations.SurfaceSampler,firstUnit+1);
    gl.uniform3fv(locations.WorldPrecipOrigin,origin);gl.uniform4f(locations.WorldPrecipGeometry,CLIMATE_CELL,CLIMATE_SIZE,CLIMATE_TILES,ready);
    gl.uniform4f(locations.WorldSurfaceGrid,...surfaceOrigin,1,SURFACE_SIZE);
    gl.uniform2fv(locations.WorldPrecipPresence,presence);
  }
  function telemetry() {return {source:'analytic demo biomes and exact toy-scene roof heights',ready,biomePreset:preset,presence:[...presence],origin:[...origin],climateGeometry:[CLIMATE_CELL,CLIMATE_SIZE,CLIMATE_TILES,ready],surfaceGrid:[...surfaceOrigin,1,SURFACE_SIZE],climateUploads,surfaceUploads,lastWork:{...lastWork}};}
  return {update,bind,telemetry,dispose(){gl.deleteTexture(climateTexture);gl.deleteTexture(surfaceTexture);}};
}
