"""启动程序化星空工作台。仅绑定本机，支持配方与 Forge 资源导出。"""
from pathlib import Path
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlencode, urlsplit
import argparse
import io
import json
import math
import re
import threading
import time
import webbrowser
import zipfile

# Portable copies discover adjacent assets. The desktop copy supplies its local path.
DEFAULT_WORKBENCH = None
REQUIRED = ('index.html', 'style.css', 'app.js', 'recipe.js', 'sky-core.glsl', 'sky-extra.glsl',
            'atmosphere-physical.glsl','atmosphere-luts.js','weather-core.glsl','weather-data.js',
            'environment-core.glsl', 'environment-preview.js', 'schema.json', 'default.json')


def strict_json(text):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError(f'重复 JSON 字段：{key}')
            result[key] = value
        return result
    def invalid_constant(value):
        raise ValueError(f'JSON 包含非法数值：{value}')
    return json.loads(text, object_pairs_hook=pairs, parse_constant=invalid_constant)


def load_schema(root):
    return strict_json((root / 'schema.json').read_text(encoding='utf-8-sig'))


def validate_recipe(recipe, spec):
    if not isinstance(recipe, dict) or set(recipe) != {'schema', 'parameters'} or recipe['schema'] != spec['schema']:
        raise ValueError(f"需要完整的 {spec['schema']} 配方")
    p = recipe['parameters']
    if not isinstance(p, dict) or set(p) != {f['key'] for f in spec['fields']}:
        raise ValueError('参数存在缺失或未知字段')
    clean = {}
    for f in spec['fields']:
        value = p[f['key']]
        if f.get('color'):
            if not isinstance(value, str) or not re.fullmatch(r'#[0-9a-fA-F]{6}', value):
                raise ValueError(f"{f['label']} 应为 #RRGGBB")
            value = value.lower()
        elif type(value) not in (int, float) or not f['min'] <= value <= f['max'] or not math.isfinite(value) or (f.get('integer') and value != int(value)):
            raise ValueError(f"{f['label']} 应在 {f['min']}–{f['max']} 范围内")
        clean[f['key']] = value
    return {'schema': spec['schema'], 'parameters': clean}


def uniforms(recipe, spec):
    p = recipe['parameters']
    values = {'Quality': p['quality'], 'Seed': p['seed']}
    for name, keys in spec['packs'].items():
        values[name] = [p[k] * math.pi / 180 if k in spec['angles'] else p[k] for k in keys]
    for name, key in spec['colors'].items():
        values[name] = [int(p[key][i:i+2], 16) / 255 for i in (1, 3, 5)]
    return values


def forge_files(root, recipe):
    spec = load_schema(root)
    recipe = validate_recipe(recipe, spec)
    values = uniforms(recipe, spec)
    declarations = [
        {'name': 'InverseProjection', 'type': 'matrix4x4', 'count': 16, 'values': [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]},
        {'name': 'InverseViewRotation', 'type': 'matrix3x3', 'count': 9, 'values': [1,0,0,0,1,0,0,0,1]},
        {'name': 'Mode', 'type': 'int', 'count': 1, 'values': [2]},
        {'name': 'Time', 'type': 'float', 'count': 1, 'values': [0]},
        {'name': 'WorldWeather', 'type': 'float', 'count': 2, 'values': [0,0]},
        {'name': 'PhysicalObserverHeight', 'type': 'float', 'count': 1, 'values': [0.07]},
        {'name': 'AtmosphereLutReady', 'type': 'int', 'count': 1, 'values': [0]},
        {'name': 'AtmosphereSkyViewReady', 'type': 'int', 'count': 1, 'values': [0]},
        {'name': 'AtmosphereLutSun', 'type': 'float', 'count': 3, 'values': [0,1,0]},
        {'name': 'EnvironmentStage', 'type': 'int', 'count': 1, 'values': [0]},
        {'name': 'WorldPrecipOrigin', 'type': 'float', 'count': 3, 'values': [0,0,0]},
        {'name': 'WorldPrecipPresence', 'type': 'float', 'count': 2, 'values': [0,0]},
        {'name': 'WorldPrecipGeometry', 'type': 'float', 'count': 4, 'values': [8,16,4,0]},
        {'name': 'WorldSurfaceGrid', 'type': 'float', 'count': 4, 'values': [0,0,1,128]},
    ]
    for name, value in values.items():
        vector = value if isinstance(value, list) else [value]
        declarations.append({'name': name, 'type': 'int' if name == 'Quality' else 'float', 'count': len(vector), 'values': vector})
    core = '\n'.join((root / name).read_text(encoding='utf-8') for name in
                     ('sky-core.glsl', 'sky-extra.glsl','atmosphere-physical.glsl','weather-core.glsl', 'environment-core.glsl'))
    vertex = '#version 150\nin vec3 Position;\nout vec2 screenUV;\nvoid main(){screenUV=Position.xy*0.5+0.5;gl_Position=vec4(Position.xy,1.0,1.0);}\n'
    fragment = '#version 150\nuniform mat4 InverseProjection;\nuniform mat3 InverseViewRotation;\nuniform int Mode;\nin vec2 screenUV;\nout vec4 fragColor;\n' + core + '''
void main(){
    vec4 p=InverseProjection*vec4(screenUV*2.0-1.0,-1.0,1.0);
    vec4 eyeH=InverseProjection*vec4(0.0,0.0,1.0,0.0);
    vec3 eyeView=abs(eyeH.w)>1e-7?eyeH.xyz/eyeH.w:vec3(0.0);
    vec3 d=normalize(InverseViewRotation*(p.xyz/p.w-eyeView));
    if(Mode==0){fragColor=vec4(d*0.5+0.5,1.0);return;}
    fragColor=vec4(Mode==1?renderStarsOnly(d):renderSky(d),1.0);
}
'''
    auxiliary_samplers=[{'name':n} for n in ('AtmosphereTransmittance','AtmosphereMultipleScattering','AtmosphereSkyViewSampler','WeatherVolumeSampler','SurfaceSampler')]
    shader = {'vertex': 'cosmiclab:sky', 'fragment': 'cosmiclab:sky', 'attributes': ['Position'], 'samplers': auxiliary_samplers, 'uniforms': declarations}
    environment_declarations = [d for d in declarations if d['name'] != 'Mode'] + [
        {'name': 'CameraPosition', 'type': 'float', 'count': 3, 'values': [0,64,0]},
        {'name': 'InverseViewTranslation', 'type': 'float', 'count': 3, 'values': [0,0,0]},
        {'name': 'OutputDeltaEncoding', 'type': 'int', 'count': 1, 'values': [0]},
    ]
    environment_fragment = '#version 150\nuniform mat4 InverseProjection;\nuniform mat3 InverseViewRotation;\nuniform vec3 InverseViewTranslation;\nuniform int OutputDeltaEncoding;\nuniform vec3 CameraPosition;\nuniform sampler2D SceneSampler;\nuniform sampler2D DepthSampler;\nin vec2 screenUV;\nout vec4 fragColor;\n' + core + '''
void main(){
    vec3 scene=texture(SceneSampler,screenUV).rgb;
    float depth=texture(DepthSampler,screenUV).r;
    vec4 viewFar=InverseProjection*vec4(screenUV*2.0-1.0,-1.0,1.0);
    vec4 eyeH=InverseProjection*vec4(0.0,0.0,1.0,0.0);
    vec3 eyeView=abs(eyeH.w)>1e-7?eyeH.xyz/eyeH.w:vec3(0.0);
    vec3 eyeWorld=InverseViewRotation*eyeView+InverseViewTranslation;
    vec3 direction=normalize(InverseViewRotation*(viewFar.xyz/viewFar.w-eyeView));
    vec4 viewPoint=InverseProjection*vec4(screenUV*2.0-1.0,depth*2.0-1.0,1.0);
    bool hit=depth<0.999999;
    vec3 relative=InverseViewRotation*(viewPoint.xyz/viewPoint.w)+InverseViewTranslation-eyeWorld;
    float distance=hit?length(relative):max(VolumeCloudGeometry.z,max(FogMotion.w,LightingDetail.y));
    if(hit)direction=normalize(relative);
    vec3 color=skyComposeEnvironment(scene,CameraPosition+eyeWorld,direction,distance,hit);
    if(OutputDeltaEncoding==1)color-=scene;
    else if(OutputDeltaEncoding==2)color=(color-scene)*(127./255.)+128./255.;
    fragColor=vec4(color,1.0);
}
'''
    environment_shader = {'vertex': 'cosmiclab:environment', 'fragment': 'cosmiclab:environment',
                          'attributes': ['Position'], 'samplers': [{'name':'SceneSampler'},{'name':'DepthSampler'}]+auxiliary_samplers,
                          'uniforms': environment_declarations}
    dump = lambda value: json.dumps(value, ensure_ascii=False, indent=2) + '\n'
    result = {
        'assets/cosmiclab/shaders/core/sky.vsh': vertex,
        'assets/cosmiclab/shaders/core/sky.fsh': fragment,
        'assets/cosmiclab/shaders/core/sky.json': dump(shader),
        'assets/cosmiclab/sky/default.json': dump(recipe),
        'assets/cosmiclab/shaders/core/environment.vsh': vertex,
        'assets/cosmiclab/shaders/core/environment.fsh': environment_fragment,
        'assets/cosmiclab/shaders/core/environment.json': dump(environment_shader),
    }
    support=root/'forge-support'
    if support.is_dir():
        for file in sorted(support.rglob('*')):
            if file.is_file() and file.suffix in ('.fsh','.vsh','.json'):
                content=file.read_text(encoding='utf-8')
                if file.suffix=='.json':
                    data=strict_json(content)
                    for uniform in data.get('uniforms',[]):
                        name='FogColor' if uniform['name']=='EnvironmentFogColor' else uniform['name']
                        if name in values:
                            value=values[name]
                            vector=value if isinstance(value,list) else [value]
                            if len(vector)==uniform['count']:
                                uniform['values']=vector
                    content=dump(data)
                result[file.relative_to(support).as_posix()]=content
    return result


def resource_zip(root, recipe):
    spec = load_schema(root)
    recipe = validate_recipe(recipe, spec)
    files = forge_files(root, recipe)
    files.update({
        'pack.mcmeta': json.dumps({'pack': {'pack_format': 15, 'description': '原创程序化星空 · Cosmic Sky Lab'}}, ensure_ascii=False),
        'cosmic-sky.json': json.dumps(recipe, ensure_ascii=False, indent=2),
        'uniforms.json': json.dumps(uniforms(recipe, spec), ensure_ascii=False, indent=2),
        '使用说明.txt': '此包配套 Forge 1.20.1 Cosmic Sky Lab 0.6.0 MOD。\n启用资源包后F3+T重载，再/cosmiclab reset使用包内默认配方；或将cosmic-sky.json复制到实例config/cosmic-sky.json，/cosmiclab load读取完整配方。\n严格cosmic-sky/6共188字段，旧/1、/2、/3、/4、/5可先在网页显式升级。\n含球形大气透射、多次散射近似与天空缓存，以及世界体积云、雾、天光、按生物群系和屋顶判断的程序雨雪。二维电影黑洞是方向天幕，不是科学相对论模拟。\n原版玻璃/水可启用分层环境合成；其他透明物体及第三方渲染需按所选实现验证。\n自动日月不改变世界时间；跟随天气仅读取游戏雨雷，不修改服务器天气。\n资源ZIP不是独立Oculus/OptiFine光影包；启用外部光影时自动交由该光影包渲染天空环境。\n',
    })
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, content in files.items():
            archive.writestr(name, content.encode('utf-8'))
    return data.getvalue()


def make_handler(root):
    spec = load_schema(root)
    class Handler(SimpleHTTPRequestHandler):
        extensions_map = {**SimpleHTTPRequestHandler.extensions_map, '.js': 'text/javascript', '.glsl': 'text/plain; charset=utf-8'}
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(root), **kwargs)
        def end_headers(self):
            self.send_header('Cache-Control', 'no-store')
            super().end_headers()
        def send_json(self, status, body):
            data = json.dumps(body, ensure_ascii=False).encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        def do_GET(self):
            if urlsplit(self.path).path == '/api/health':
                self.send_json(200, {'app': 'cosmic-sky-workbench', 'schema': spec['schema'], 'root': str(root)})
            else:
                super().do_GET()
        def do_POST(self):
            endpoint = urlsplit(self.path).path
            if endpoint not in ('/api/export', '/api/resources'):
                self.send_error(404)
                return
            port = self.server.server_port
            if self.headers.get('Origin') not in (f'http://127.0.0.1:{port}', f'http://localhost:{port}'):
                self.send_error(403)
                return
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 65536:
                    raise ValueError('参数文件大小无效')
                recipe = validate_recipe(strict_json(self.rfile.read(length).decode('utf-8')), spec)
                folder = root / 'exports'
                folder.mkdir(exist_ok=True)
                stamp = time.time_ns()
                if endpoint == '/api/export':
                    target = folder / f'cosmic-sky-{stamp}.json'
                    target.write_text(json.dumps(recipe, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
                else:
                    target = folder / f'cosmic-sky-resources-{stamp}.zip'
                    target.write_bytes(resource_zip(root, recipe))
                self.send_json(200, {'path': str(target), 'url': f'/exports/{target.name}'})
            except (ValueError, OSError, UnicodeError) as error:
                self.send_json(400, {'error': str(error)})
    return Handler


def main():
    parser = argparse.ArgumentParser(description='启动程序化星空工作台')
    parser.add_argument('--no-browser', action='store_true')
    parser.add_argument('--root', type=Path, help='指定工作台资源目录')
    parser.add_argument('--port', type=int, default=0, help='端口；0 自动分配')
    parser.add_argument('--check', action='store_true', help='只检查资源与默认配方，不启动服务')
    parser.add_argument('--generate-forge', type=Path, help='生成默认 Forge shader 与配方到指定 resources 目录')
    args = parser.parse_args()
    adjacent = Path(__file__).resolve().parent
    candidates = (adjacent, adjacent / 'workbench', DEFAULT_WORKBENCH)
    discovered = next((p for p in candidates if p is not None and all((p/n).is_file() for n in REQUIRED)), adjacent)
    root = (args.root or discovered).resolve()
    missing = [name for name in REQUIRED if not (root/name).is_file()]
    if missing:
        raise RuntimeError(f'工作台资源不完整：{root}\n缺少：{", ".join(missing)}')
    recipe = validate_recipe(strict_json((root/'default.json').read_text(encoding='utf-8-sig')), load_schema(root))
    if args.generate_forge:
        for name, content in forge_files(root, recipe).items():
            target = args.generate_forge / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding='utf-8')
        print(f'Forge 资源已生成：{args.generate_forge}', flush=True)
        return
    if args.check:
        print(json.dumps({'ok': True, 'root': str(root), 'parameters': len(recipe['parameters'])}, ensure_ascii=False), flush=True)
        return
    server = ThreadingHTTPServer(('127.0.0.1', args.port), make_handler(root))
    server.daemon_threads = True
    url = f'http://127.0.0.1:{server.server_port}/index.html?{urlencode({"_boot": time.time_ns()})}'
    print('程序化星空 · 玩家创作工作台', flush=True)
    print(f'工作台目录：{root}\n浏览器地址：{url}', flush=True)
    print('预设、实时调参、暂停重放、PNG 与配方/着色器资源导出。\n导出保存在工作台 exports 目录。请保留窗口；Ctrl+C 关闭。', flush=True)
    if not args.no_browser:
        threading.Thread(target=lambda: webbrowser.open_new_tab(url), daemon=True).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\n星空工作台已关闭。')
    finally:
        server.server_close()


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'启动失败：{error}')
        try:
            input('按回车退出……')
        except EOFError:
            pass
        raise SystemExit(1)
