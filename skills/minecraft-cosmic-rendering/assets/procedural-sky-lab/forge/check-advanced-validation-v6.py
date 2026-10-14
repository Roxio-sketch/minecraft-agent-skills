#!/usr/bin/env python3
"""Independently check real /6 Forge readbacks, decoded images and pinned ownership.

This is an evidence checker, not a renderer simulator. A partial run never passes.
It intentionally requires numeric GPU data and actual image differences rather
than accepting renderer-written success flags.
"""
import argparse
import hashlib
import json
import math
import re
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

NATIVE = ("physical-day physical-sunset physical-night physical-multi-off physical-multi-on "
          "physical-observer-high physical-observer-outside physical-density physical-yaw physical-reload "
          "transparent-clear transparent-clear-fabulous legacy-fog native-fog-fancy native-fog-fabulous "
          "native-fog-bob native-fog-yaw45 native-fog-fov100 native-fog-resize legacy-rain native-rain-fancy "
          "native-rain-fabulous native-snow-fancy native-snow-fabulous weather-rain-time0 weather-rain-time2 "
          "weather-snow-time0 weather-snow-time2 weather-roof-off weather-roof-on weather-biome-rain "
          "weather-biome-snow weather-biome-dry weather-tiny-snow-half performance-low performance-medium "
          "performance-high performance-half performance-threequarter performance-zero-full performance-zero-half").split()
COMPAT = "compat-off compat-on compat-reload compat-off-again".split()
NATIVE_HOOK = "transparent-clear transparent-clear-fabulous native-fog-fancy native-fog-fabulous native-fog-bob".split()
CELESTIAL = ("giant-off giant-gas-time0 giant-gas-time2 giant-ice-time0 giant-ice-time2 "
             "giant-fragment-time0 giant-fragment-time2 giant-dark-time0 giant-dark-time2 "
             "giant-binary-time0 giant-binary-time2 giant-flare-time0 giant-flare-time2 "
             "giant-vortex-time0 giant-vortex-time2 giant-gas-yaw45 giant-gas-reload "
             "giant-gas-fov100 giant-gas-resize giant-gas-backside giant-gas-translation "
             "cinematic-lens-off cinematic-lens-on cinematic-lens-time2 cinematic-lens-yaw45 cinematic-lens-reload").split()
CELESTIAL_PRESETS = {"gas": "gasgiant", "ice": "icegiant", "fragment": "fragmentmoon", "dark": "glowingdarkdisk",
                     "binary": "binarystars", "flare": "flaringstar", "vortex": "vortexdarkstar"}
RUNTIME_CLASSES = {"dev/cosmiclab/" + name + ".class" for name in (
    "CosmicLab", "SkyRecipe", "LabSky", "LabEnvironment", "LabAtmosphere", "LabPrecipitation", "LabTranslucency",
    "LabRenderOwner", "LabGpuTimings", "LabAdvancedValidation", "mixin/NativeTranslucentMixin", "mixin/NativePipelineMixinPlugin")}
RUNTIME_RESOURCES = {"assets/cosmiclab/shaders/core/" + program + "." + extension
                     for program in ("sky", "environment", "surface", "environment_composite", "atmosphere_transmittance", "atmosphere_multiple", "atmosphere_sky_view")
                     for extension in ("vsh", "fsh", "json")} | {"cosmiclab.mixins.json", "cosmiclab-vanilla-contract.properties",
                                                               "assets/cosmiclab/sky/default.json", "assets/cosmiclab/sky/validation-presets.json"}
STATE_INTS = {"drawFramebuffer", "readFramebuffer", "program", "activeTexture", "renderSystemShader"}
STATE_BOOLS = {"depthTest", "depthWrite", "blend", "cull", "scissor"}
STATE_ARRAYS = {"viewport": 4, "blendFactors": 4, "textureBindings": 16, "shaderTextures": 12}
STATE_FIELDS = STATE_INTS | STATE_BOOLS | set(STATE_ARRAYS) | {"colorMask"}
PACKS = {
    "StarSettings": "starDensity starBrightness starSize twinkleAmount",
    "MotionSettings": "twinkleSpeed nebulaSpeed auroraSpeed meteorSpeed",
    "GalaxySettings": "galacticYaw galacticTilt bandWidth galaxyStrength",
    "CloudSettings": "nebulaStrength nebulaScale dustStrength exposure",
    "AuroraSettings": "auroraStrength auroraHeight auroraWidth auroraCurtains",
    "MeteorSettings": "meteorRate meteorBrightness meteorLength horizonGlow",
    "BodySettings": "celestialStrength celestialSize celestialYaw celestialPitch",
    "BodySurface": "celestialType celestialPhase celestialDetail celestialSpeed",
    "GiantBodySettings": "giantBodyStyle giantBodyScale giantBodySeparation giantBodyAtmosphere",
    "RingSettings": "ringStrength ringTilt ringWidth",
    "BlackHoleSettings": "blackHoleStrength blackHoleSize blackHoleYaw blackHolePitch",
    "DiskSettings": "diskTilt diskWidth diskSpeed lensingStrength",
    "AtmosphereSettings": "atmosphereStrength turbidity rayleighStrength mieStrength",
    "SunSettings": "sunBrightness sunElevation sunYaw sunSize",
    "DaySettings": "cycleSpeed mieAnisotropy nightAmbient twilightStrength",
    "MoonSettings": "moonBrightness moonYaw moonElevation moonSize",
    "MoonSurface": "moonPhase moonDetail moonHalo moonSpeed",
    "ThinCloudSettings": "thinCloudStrength thinCloudCoverage thinCloudScale thinCloudHeight",
    "ThinCloudMotion": "thinCloudSpeed thinCloudWind thinCloudEvolution thinCloudSilver",
    "WeatherSettings": "rainAmount thunderAmount weatherSpeed stormDarkening",
    "RainSettings": "rainDensity rainSpeed rainLength rainBrightness",
    "WeatherMotion": "rainWind weatherFollowWorld lightningFrequency lightningDuration",
    "FogSettings": "fogStrength fogDensity fogHeightFalloff fogBaseHeight",
    "FogMotion": "fogNoise fogScale fogSpeed fogDistance",
    "VolumeCloudSettings": "volumeCloudStrength volumeCloudCoverage volumeCloudDensity volumeCloudScale",
    "VolumeCloudGeometry": "volumeCloudBase volumeCloudThickness volumeCloudDistance volumeCloudErosion",
    "VolumeCloudMotion": "volumeCloudSpeed volumeCloudWind volumeCloudEvolution volumeCloudAnisotropy",
    "CloudLighting": "volumeCloudAmbient volumeCloudSunlight cloudShadowStrength lightShaftStrength",
    "LightingDetail": "lightShaftDensity lightShaftDistance cloudShadowSoftness lightShaftAnisotropy",
    "RainbowSettings": "rainbowStrength rainbowWidth secondaryRainbow rainbowWetness",
    "HaloSettings": "haloStrength haloWidth haloIce haloMoon",
    "SpecialSettings": "specialStrength specialStyle specialScale specialSpeed",
    "SpecialShape": "specialDistortion specialBandWidth birdDensity birdSpeed",
    "BlackHoleAppearance": "blackHoleStyle blackHoleRingBrightness blackHoleRingWidth blackHoleGlow",
    "BlackHoleCinematic": "blackHoleArc blackHoleStreak blackHoleContrast blackHoleNoise",
    "AtmosphereGeometry": "planetRadius atmosphereHeight rayleighHeight mieHeight",
    "AtmosphereComposition": "rayleighDensity mieDensity ozoneDensity groundAlbedo",
    "AtmospherePhysical": "atmosphereStyle observerAltitude multipleScattering groundLevel",
    "SnowSettings": "snowDensity snowSize snowSpeed snowBrightness",
    "PrecipitationSettings": "precipitationMode precipitationRoof snowFlutter snowWindStrength",
    "PipelineSettings": "nativeTransparentMode environmentRenderScale environmentDepthTolerance",
}
PACKS = {k: v.split() for k, v in PACKS.items()}
COLORS = {
    "BackgroundColor": "backgroundColor", "StarCoolColor": "starCoolColor", "StarWarmColor": "starWarmColor",
    "GalaxyColor": "galaxyColor", "NebulaColorA": "nebulaColorA", "NebulaColorB": "nebulaColorB",
    "AuroraColorA": "auroraColorA", "AuroraColorB": "auroraColorB", "BodyColorA": "celestialColorA",
    "BodyColorB": "celestialColorB", "RingColor": "ringColor", "DiskColorA": "diskColorA",
    "DiskColorB": "diskColorB", "SkyTint": "skyTint", "SunColor": "sunColor", "MoonColor": "moonColor",
    "ThinCloudColor": "thinCloudColor", "FogColor": "fogColor", "EnvironmentFogColor": "fogColor",
    "VolumeCloudColor": "volumeCloudColor", "RainColor": "rainColor", "LightningColor": "lightningColor",
    "SpecialColorA": "specialColorA", "SpecialColorB": "specialColorB", "SnowColor": "snowColor",
}
ANGLES = set(("galacticYaw galacticTilt celestialSize celestialYaw celestialPitch celestialPhase ringTilt "
              "blackHoleSize blackHoleYaw blackHolePitch diskTilt sunElevation sunYaw sunSize moonYaw "
              "moonElevation moonSize moonPhase thinCloudWind rainWind volumeCloudWind rainbowWidth haloWidth").split())
RECIPE_UNIFORMS = set(PACKS) | set(COLORS) | {"Quality", "Seed"}
SAMPLERS = {"AtmosphereTransmittance", "AtmosphereMultipleScattering", "AtmosphereSkyViewSampler",
            "WeatherVolumeSampler", "SurfaceSampler", "SceneSampler", "DepthSampler", "Sampler0", "Sampler2"}


def strict_text(text):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("duplicate JSON key " + key)
            result[key] = value
        return result
    return json.loads(text, object_pairs_hook=pairs,
                      parse_constant=lambda v: (_ for _ in ()).throw(ValueError("nonfinite " + v)))


def strict(path):
    return strict_text(path.read_text(encoding="utf-8-sig"))


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def vector(value, size):
    return isinstance(value, list) and len(value) == size and all(map(finite, value))


def close(actual, expected, tolerance=1e-4):
    return vector(actual, len(expected)) and np.allclose(actual, expected, rtol=tolerance, atol=tolerance)


def matrix(values, size=4):
    return np.asarray(values, dtype=np.float64).reshape((size, size), order="F")


def optical_eye(record):
    sky = record["skyDraw"]
    inverse = np.linalg.inv(matrix(sky["projection"]))
    point = inverse @ np.asarray([0., 0., 1., 0.])
    if abs(point[3]) < 1e-8:
        raise ValueError("projection optical eye is not finite")
    eye = point[:3] / point[3]
    return matrix(sky["inverseViewRotation"], 3) @ eye + np.asarray(record["inverseViewTranslation"])


def source_hashes():
    root = Path(__file__).resolve().parents[1]
    paths = list((root / "workbench").glob("*.glsl"))
    paths += [root / "workbench" / name for name in ("recipe.js", "schema.json", "presets.json", "default.json", "launch.py",
                                                      "atmosphere-luts.js", "environment-preview.js", "weather-data.js")]
    paths += list((root / "forge" / "src" / "main" / "resources" / "assets" / "cosmiclab" / "shaders").rglob("*"))
    paths += list((root / "forge" / "src" / "main" / "resources" / "assets" / "cosmiclab" / "sky").glob("*.json"))
    paths += list((root / "forge" / "src" / "main" / "java" / "dev" / "cosmiclab").rglob("*.java"))
    paths += [root / "forge" / "src" / "main" / "resources" / name
              for name in ("cosmiclab.mixins.json", "cosmiclab-vanilla-contract.properties", "META-INF/mods.toml")]
    return {path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(set(paths)) if path.is_file()}


def check(folder, mode="native", manifest=None, runtime_artifact=None, production_artifact=None):
    folder = Path(folder).resolve()
    errors, comparisons, state_comparisons, checked, raw_arrays, coverage = [], [], [], 0, {}, set()
    names = NATIVE if mode == "native" else NATIVE_HOOK if mode == "native-hook" else CELESTIAL if mode == "celestial" else COMPAT
    validation_presets = {}
    runtime_binding = {"required": True, "status": "not-verified"}

    def require(condition, message):
        nonlocal checked
        checked += 1
        if not condition:
            errors.append(message)

    try:
        records, summary = strict(folder / "results.json"), strict(folder / "summary.json")
        schema_path = Path(__file__).resolve().with_name("schema-v6-validation.json")
        fields = {f["key"]: f for f in strict(schema_path)["fields"]}
        require(isinstance(records, list), "results must be an array")
        by = {r["name"]: r for r in records}
        require(len(records) == len(by) == len(names) and set(by) == set(names),
                "expected exactly " + str(len(names)) + " unique " + mode + " cases")
        require(summary.get("protocol") == "cosmic-advanced-sky-forge/1", "wrong validation protocol")
        require(summary.get("version") == "0.6.0" and summary.get("schema") == "cosmic-sky/6", "wrong mod/schema")
        require(summary.get("fields") == len(fields) == 188 and summary.get("cases") == len(names), "wrong fields/cases")
        require(summary.get("caseNames") == names, "summary case order or scope differs")
        require(summary.get("minecraft") == "1.20.1" and summary.get("forge") == "47.4.22", "wrong game/loader")
        require(isinstance(summary.get("gpu"), str) and bool(summary["gpu"]) and
                summary.get("world", "").startswith("COSMIC_ADVANCED_"), "missing real GPU/isolated world")
        require(not (folder / "failure.txt").exists(), "run contains failure.txt")
        if mode in ("native", "native-hook", "celestial"):
            require(summary.get("rendererMods") == {}, "native run has renderer mods")
    except (OSError, ValueError, TypeError, KeyError) as error:
        return {"ok": False, "mode": mode, "errors": errors + [str(error)]}

    if runtime_binding["required"]:
        classes, resources = summary.get("classHashes", {}), summary.get("resourceHashes", {})
        require(isinstance(classes, dict) and set(classes) == RUNTIME_CLASSES, "actual classloader byte manifest differs from required 12 classes")
        require(isinstance(resources, dict) and set(resources) == RUNTIME_RESOURCES, "actual ResourceManager byte manifest differs from required 25 resources")
        require(all(isinstance(v, str) and bool(re.fullmatch(r"[0-9a-f]{64}", v)) for v in [*classes.values(), *resources.values()]), "invalid runtime hash values")
        require(runtime_artifact is not None and production_artifact is not None, "final runtime requires mapped compiled artifact and reobfuscated production JAR")
        if runtime_artifact and production_artifact:
            try:
                mapped, production = Path(runtime_artifact).resolve(), Path(production_artifact).resolve()
                compared_classes = compared_resources = 0
                with zipfile.ZipFile(mapped) as archive:
                    require(archive.testzip() is None, "mapped compiled artifact CRC failure")
                    validation_presets = strict_text(archive.read("assets/cosmiclab/sky/validation-presets.json").decode("utf-8-sig"))
                    require(isinstance(validation_presets, dict) and set(validation_presets) == set(CELESTIAL_PRESETS.values()) | {"cinematicblackhole"},
                            "actual validation preset resource differs from required eight authored works")
                    mixin_config = json.loads(archive.read("cosmiclab.mixins.json"))
                    require(mixin_config.get("plugin") == "dev.cosmiclab.mixin.NativePipelineMixinPlugin" and
                            mixin_config.get("package") == "dev.cosmiclab.mixin" and
                            mixin_config.get("client") == ["NativeTranslucentMixin"], "actual bound mixin configuration lacks final ownership plugin / native hook")
                    for key, value in {**classes, **resources}.items():
                        require(hashlib.sha256(archive.read(key)).hexdigest() == value, "actual runtime bytes differ from mapped artifact: " + key)
                        compared_classes += key in classes
                with zipfile.ZipFile(production) as archive:
                    require(archive.testzip() is None, "reobfuscated production JAR CRC failure")
                    for key, value in resources.items():
                        require(hashlib.sha256(archive.read(key)).hexdigest() == value, "actual ResourceManager resource differs from production JAR: " + key)
                        compared_resources += 1
                runtime_binding.update({"status": "compared", "classCount": compared_classes, "resourceCount": compared_resources,
                                        "mappedArtifact": {"path": str(mapped), "sha256": hashlib.sha256(mapped.read_bytes()).hexdigest(), "mapping": "ForgeGradle official development"},
                                        "productionArtifact": {"path": str(production), "sha256": hashlib.sha256(production.read_bytes()).hexdigest(), "mapping": "Forge reobfuscated", "classesCompared": False},
                                        "classHashProvenance": "actual classloader input bytes compared to frozen mapped compiled artifact",
                                        "resourceHashProvenance": "actual ResourceManager shader bytes / classloader configuration bytes compared to mapped artifact and production JAR",
                                        "mappingLimitation": "Development-mapped class bytes cannot be claimed byte-identical to the reobfuscated production JAR."})
            except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
                errors.append("runtime artifact binding failure: " + str(error))

    def texture(record, data, tag, width, height, internal_format):
        name = record["name"]
        require(isinstance(data, dict), name + ": missing texture " + tag)
        if not isinstance(data, dict):
            return None
        require(type(data.get("texture")) is int and data["texture"] > 0, name + ": invalid texture " + tag)
        require((data.get("width"), data.get("height"), data.get("format")) == (width, height, internal_format),
                name + ": wrong float texture format/dimensions " + tag)
        require(data.get("nonfinite") == 0 and all(finite(data.get(k)) for k in ("minimum", "maximum", "mean")),
                name + ": nonfinite texture aggregate " + tag)
        filename = data.get("readbackFile")
        require(isinstance(filename, str) and bool(filename), name + ": missing raw GPU float readback " + tag)
        if not isinstance(filename, str) or not filename:
            return None
        path = (folder / filename).resolve()
        require(path.is_relative_to(folder), name + ": readback outside evidence folder " + tag)
        if not path.is_relative_to(folder):
            return None
        try:
            raw = path.read_bytes()
            require(data.get("readbackType") == "float32-le-rgba", name + ": raw float encoding " + tag)
            require(len(raw) == width * height * 16, name + ": wrong raw byte count " + tag)
            require(hashlib.sha256(raw).hexdigest() == data.get("readbackSha256"), name + ": raw hash mismatch " + tag)
            if len(raw) != width * height * 16:
                return None
            values = np.frombuffer(raw, dtype="<f4").reshape(height, width, 4)
            require(np.isfinite(values).all(), name + ": raw float nonfinite " + tag)
            for key, value in (("minimum", values.min()), ("maximum", values.max()), ("mean", values.mean(dtype=np.float64))):
                require(finite(data.get(key)) and math.isclose(data[key], float(value), rel_tol=2e-6, abs_tol=2e-7),
                        name + ": independently calculated texture " + key + " differs " + tag)
            raw_arrays[(name, tag)] = values.copy()
            return values
        except (OSError, ValueError) as error:
            errors.append(name + ": raw texture failure " + tag + ": " + str(error))
            return None

    def expected_uniform(record, draw, uniform, size, role=None):
        p = record["recipe"]["parameters"]
        if uniform in PACKS:
            coverage.update(PACKS[uniform])
            return [math.radians(float(np.float32(p[k]))) if k in ANGLES else p[k] for k in PACKS[uniform]]
        if uniform in COLORS:
            # Surface FogColor is Mojang's vec4 fog; custom fog is EnvironmentFogColor.
            if uniform == "FogColor" and size == 4:
                return None
            coverage.add(COLORS[uniform])
            value = p[COLORS[uniform]][1:]
            return [int(value[i:i + 2], 16) / 255 for i in (0, 2, 4)]
        if uniform in ("Quality", "Seed"):
            key = "quality" if uniform == "Quality" else "seed"
            coverage.add(key)
            return [p[key]]
        if uniform == "Time":
            return [draw.get("actualTime", record.get("actualTime")) if role == "lut" else record.get("actualTime")]
        if uniform == "Mode":
            return [2]
        if uniform == "CameraPosition":
            return draw.get("cameraPosition", record.get("cameraPosition")) if role == "lut" else record.get("cameraPosition")
        if uniform == "EnvironmentStage":
            return [0]
        if uniform == "PhysicalObserverHeight":
            position = draw.get("cameraPosition", record["cameraPosition"]) if role == "lut" else record["cameraPosition"]
            return [max(p["observerAltitude"] + (position[1] - p["groundLevel"]) * .001, .001)]
        if uniform in ("AtmosphereLutReady", "AtmosphereSkyViewReady"):
            if role == "lut":
                return [1 if uniform == "AtmosphereLutReady" else 0]
            return [int(p["atmosphereStyle"] == 2)]
        if uniform == "AtmosphereLutSun":
            time = draw.get("actualTime", record["actualTime"]) if role == "lut" else record["actualTime"]
            y = math.sin(math.radians(p["sunElevation"]) + time * p["cycleSpeed"] * .035)
            return [0, y, math.sqrt(max(1 - y * y, 0))]
        if uniform == "InverseProjection" and record.get("skyDraw"):
            return np.linalg.inv(matrix(record["skyDraw"]["projection"])).flatten(order="F").tolist()
        if uniform == "InverseViewRotation" and record.get("skyDraw"):
            return record["skyDraw"]["inverseViewRotation"]
        if uniform == "InverseViewTranslation":
            return record.get("inverseViewTranslation")
        if uniform == "OpticalEyeOffset":
            return optical_eye(record).tolist()
        if uniform in ("ChunkOffset", "ModelViewMat", "ProjMat"):
            return draw.get({"ChunkOffset": "chunkOffset", "ModelViewMat": "modelView", "ProjMat": "projection"}[uniform])
        if uniform == "WorldPrecipOrigin" and record.get("weatherOrigin"):
            return record["weatherOrigin"][:3]
        if uniform == "WorldPrecipGeometry":
            return [8, 16, 4, int(bool(record.get("weatherVolume")))]
        if uniform == "WorldSurfaceGrid" and record.get("weatherOrigin"):
            return [*record["weatherOrigin"][3:], 1, 128]
        if uniform == "WorldPrecipPresence" and record.get("weatherCounts"):
            return [int(record["weatherCounts"][0] > 0), int(record["weatherCounts"][1] > 0)]
        if uniform == "OutputDeltaEncoding":
            return [2 if record.get("effectiveRenderScale", 1) < .999 else 0]
        return None

    def program(record, key, required, supplied=None, role=None):
        name = record["name"]
        draw = record.get(key) if supplied is None else supplied
        require(isinstance(draw, dict), name + ": missing actual draw " + key)
        if not isinstance(draw, dict):
            return
        require(type(draw.get("program")) is int and draw["program"] > 0 and
                draw["program"] == draw.get("boundProgram"), name + ": program binding differs " + key)
        require(type(draw.get("fbo")) is int and draw["fbo"] >= 0, name + ": missing actual framebuffer " + key)
        require(vector(draw.get("viewport"), 4) and all(type(n) is int for n in draw["viewport"]) and
                draw["viewport"][2] > 0 and draw["viewport"][3] > 0, name + ": missing actual GPU viewport " + key)
        uniforms = draw.get("uniforms", {})
        require(isinstance(uniforms, dict) and RECIPE_UNIFORMS <= set(uniforms), name + ": incomplete uniform query contract " + key)
        for uniform in required:
            require(uniforms.get(uniform, {}).get("location", -1) >= 0, name + ": required uniform inactive " + key + "/" + uniform)
        for uniform, data in uniforms.items():
            location = data.get("location")
            require(type(location) is int, name + ": invalid location " + key + "/" + uniform)
            if type(location) is not int or location < 0:
                require("values" not in data, name + ": fabricated inactive uniform values " + uniform)
                continue
            values = data.get("values")
            require(isinstance(values, list) and bool(values) and all(map(finite, values)), name + ": invalid GPU values " + key + "/" + uniform)
            if not isinstance(values, list) or not values:
                continue
            try:
                expected = expected_uniform(record, draw, uniform, len(values), role)
                if expected is not None:
                    if uniform in RECIPE_UNIFORMS and not (uniform == "FogColor" and len(values) == 4):
                        matches = len(values) == len(expected) and np.array_equal(np.asarray(values, dtype=np.float32), np.asarray(expected, dtype=np.float32))
                    else:
                        matches = close(values, expected, 1e-5)
                    require(matches, name + ": uploaded GPU value differs " + key + "/" + uniform)
            except (KeyError, ValueError, TypeError, np.linalg.LinAlgError) as error:
                errors.append(name + ": cannot independently derive " + uniform + ": " + str(error))
            if uniform == "WorldWeather":
                require(close(values, [0, 0]), name + ": actual clear world weather differs " + key)
        samplers = draw.get("samplers", {})
        for uniform in SAMPLERS:
            if uniforms.get(uniform, {}).get("location", -1) >= 0:
                data = samplers.get(uniform, {})
                optional_unready = (uniform.startswith("Atmosphere") and record["recipe"]["parameters"].get("atmosphereStyle") != 2) or (
                    uniform in ("WeatherVolumeSampler", "SurfaceSampler") and not record.get("weatherVolume"))
                require(type(data.get("unit")) is int and 0 <= data["unit"] < 32 and
                        type(data.get("texture")) is int and data["texture"] >= (0 if optional_unready else 1) and data.get("target") == 3553,
                        name + ": actual sampler binding missing " + key + "/" + uniform)
                require(close(uniforms[uniform].get("values"), [data.get("unit", -1)]), name + ": sampler unit differs " + uniform)
                if uniform == "WeatherVolumeSampler" and record.get("weatherVolume"):
                    require(data.get("texture") == record["weatherVolume"].get("texture"), name + ": wrong weather texture binding")
                if uniform == "SurfaceSampler" and record.get("weatherSurface"):
                    require(data.get("texture") == record["weatherSurface"].get("texture"), name + ": wrong surface texture binding")
                if uniform.startswith("Atmosphere") and record.get("luts"):
                    index = ["AtmosphereTransmittance", "AtmosphereMultipleScattering", "AtmosphereSkyViewSampler"].index(uniform)
                    require(data.get("texture") == record["luts"][index].get("texture"), name + ": wrong atmosphere LUT binding")

    def image(name, suffix):
        with Image.open(folder / (name + "-" + suffix + ".png")) as source:
            source.load()
            return np.asarray(source.convert("RGBA"))

    def states(record, expected):
        name, evidence = record["name"], record.get("stateEvidence", {})
        require(isinstance(evidence, dict) and set(evidence) == set(expected), name + ": actual GL state pass coverage differs")
        if not isinstance(evidence, dict):
            return
        for pass_name in expected:
            item = evidence.get(pass_name, {})
            require(isinstance(item, dict) and set(item) == {"before", "after"}, name + ": missing actual before/after GL states " + pass_name)
            before, after = item.get("before", {}), item.get("after", {})
            for side, data in (("before", before), ("after", after)):
                label = name + ": " + pass_name + "/" + side + "/"
                require(isinstance(data, dict) and set(data) == STATE_FIELDS, label + "actual state dictionary keys differ")
                if not isinstance(data, dict):
                    continue
                for key in STATE_INTS:
                    require(type(data.get(key)) is int and data[key] >= 0, label + "invalid integer " + key)
                for key in STATE_BOOLS:
                    require(type(data.get(key)) is bool, label + "invalid measured GL bit " + key)
                for key, count in STATE_ARRAYS.items():
                    require(isinstance(data.get(key), list) and len(data[key]) == count and all(type(v) is int for v in data[key]), label + "invalid state array " + key)
                    if key in ("textureBindings", "shaderTextures", "blendFactors") and isinstance(data.get(key), list):
                        require(all(type(v) is int and v >= 0 for v in data[key]), label + "invalid binding/enum " + key)
                require(isinstance(data.get("colorMask"), list) and len(data["colorMask"]) == 4 and all(type(v) is bool for v in data["colorMask"]), label + "invalid measured color mask")
                require(type(data.get("activeTexture")) is int and 33984 <= data["activeTexture"] < 34016, label + "invalid active texture unit")
                if vector(data.get("viewport"), 4):
                    require(data["viewport"][2] > 0 and data["viewport"][3] > 0, label + "empty actual viewport")
            differences = []
            for key in STATE_FIELDS:
                same = before.get(key) == after.get(key)
                require(same, name + ": actual GL state failed to restore " + pass_name + "/" + key)
                if not same:
                    differences.append(key)
            state_comparisons.append({"case": name, "pass": pass_name, "fields": len(STATE_FIELDS),
                                      "textureUnits": 16, "shaderTextureSlots": 12, "differences": sorted(differences)})

    def pair(first, second, suffix="world", identical=False, minimum=100, same_recipe=False, ignore=None):
        if first not in by or second not in by:
            return
        try:
            a, b = image(first, suffix), image(second, suffix)
            require(a.shape == b.shape, first + "/" + second + ": PNG dimensions differ")
            if a.shape != b.shape:
                return
            difference = np.abs(a.astype(np.int16) - b.astype(np.int16))
            changed = int(np.count_nonzero(np.any(difference, axis=-1)))
            mean = float(difference[..., :3].mean())
            # An unrelated 1-LSB sky change must not pass particle animation checks.
            strong = int(np.count_nonzero(np.max(difference[..., :3], axis=-1) >= 3))
            require(changed == 0 if identical else strong >= minimum,
                    first + "/" + second + ": independently decoded effect comparison failed (changed=" + str(changed) + ", >=3LSB=" + str(strong) + ")")
            if same_recipe:
                aa, bb = by[first]["recipe"]["parameters"].copy(), by[second]["recipe"]["parameters"].copy()
                for key in ignore or []:
                    aa.pop(key, None); bb.pop(key, None)
                require(aa == bb, first + "/" + second + ": unintended recipe change")
            comparisons.append({"first": first, "second": second, "suffix": suffix,
                                "changedPixels": changed, "pixelsAtLeast3LSB": strong, "meanAbsoluteRgb": mean})
        except (OSError, ValueError) as error:
            errors.append(first + "/" + second + ": PNG decode failure: " + str(error))

    for name in names:
        if name not in by:
            continue
        record = by[name]
        p = record.get("recipe", {}).get("parameters", {})
        require(record.get("recipe", {}).get("schema") == "cosmic-sky/6" and set(p) == set(fields), name + ": strict 188-field recipe differs")
        for key, field in fields.items():
            value = p.get(key)
            if field.get("color"):
                valid = isinstance(value, str) and bool(re.fullmatch(r"#[0-9a-f]{6}", value))
            else:
                valid = finite(value) and field.get("min", -math.inf) <= value <= field.get("max", math.inf) and (
                    not field.get("integer") or value == int(value))
            require(valid, name + ": invalid parameter " + key)
        size = (800, 600) if name.endswith("resize") else (960, 540)
        require((record.get("width"), record.get("height")) == size, name + ": wrong framebuffer dimensions")
        require(record.get("glError") == 0, name + ": actual GL error")
        require(record.get("frames", 0) >= 150 and record.get("ticks", 0) >= 100, name + ": insufficient warmed frames")
        requested_time = 2 if name.endswith("time2") else 0
        require(record.get("actualTime") == requested_time, name + ": unfrozen animation time")
        require(p.get("quality") == (1 if name.endswith("low") else 3 if name.endswith("high") else 2), name + ": quality mismatch")
        for key, count in (("cameraPosition", 3), ("cameraLook", 3), ("inverseViewTranslation", 3), ("countsBefore", 7), ("countsAfter", 7)):
            require(vector(record.get(key), count), name + ": invalid " + key)
            if key.startswith("counts"):
                require(isinstance(record.get(key), list) and all(type(n) is int and n >= 0 for n in record[key]), name + ": invalid integer cumulative counts " + key)
        if vector(record.get("countsBefore"), 7) and vector(record.get("countsAfter"), 7):
            delta = np.asarray(record["countsAfter"]) - record["countsBefore"]
            require(np.all(delta >= 0), name + ": decreasing cumulative counters")
        else:
            delta = np.zeros(7)
        for suffix in ("sky", "world"):
            if mode == "compat" and name in ("compat-on", "compat-reload") and suffix == "sky":
                continue
            try:
                pixels = image(name, suffix)
                require(pixels.shape == (size[1], size[0], 4), name + ": decoded PNG size differs " + suffix)
                if not (mode == "celestial" and suffix == "sky" and name in ("giant-off", "giant-gas-backside")):
                    require(int(pixels[..., :3].max()) > int(pixels[..., :3].min()), name + ": empty constant screenshot " + suffix)
            except (OSError, ValueError) as error:
                errors.append(name + ": PNG decode failure " + suffix + ": " + str(error))
        if mode in ("native", "native-hook", "compat", "celestial"):
            expected_states = ["environment-opaque", "native-translucent"] if name.startswith("native-") else (
                ["environment-final"] if ((mode == "compat" and name in ("compat-off", "compat-off-again")) or
                                           (mode == "native" and name.startswith(("legacy-", "weather-", "performance-")))) else ["sky"] if mode == "celestial" else [])
            states(record, expected_states)
        if mode == "compat" and name in ("compat-on", "compat-reload"):
            continue
        program(record, "skyDraw", {"Mode", "Quality", "Time", "WorldWeather", "AtmospherePhysical", "InverseProjection", "InverseViewRotation"})
        sky = record.get("skyDraw", {})
        require(vector(sky.get("projection"), 16) and vector(sky.get("inverseViewRotation"), 9), name + ": missing actual sky matrices")
        if vector(sky.get("inverseViewRotation"), 9):
            rotation = matrix(sky["inverseViewRotation"], 3)
            require(np.allclose(rotation.T @ rotation, np.eye(3), rtol=1e-5, atol=1e-5) and
                    abs(np.linalg.det(rotation) - 1) < 1e-5, name + ": non-orthonormal actual camera rotation")
            if name != "native-fog-bob" and vector(record.get("cameraLook"), 3):
                direction = -rotation[:, 2]
                direction /= np.linalg.norm(direction)
                require(close(direction.tolist(), record["cameraLook"], 1e-5), name + ": shader center direction differs from actual Camera look")
        if vector(sky.get("projection"), 16):
            require(sky.get("viewport") == [0, 0, *size], name + ": actual sky viewport differs from framebuffer size")
            fov = 100 if name.endswith("fov100") else 70
            scale = 1 / math.tan(math.radians(fov) / 2)
            # Bob may rotate the projection; the base FOV checks use non-bob cases.
            if name != "native-fog-bob":
                require(abs(sky["projection"][5] - scale) < 1e-5 and abs(sky["projection"][0] - scale * size[1] / size[0]) < 1e-5,
                        name + ": actual projection differs from FOV/aspect")
        if name.startswith("physical-"):
            builds = record.get("lutBuildDraws")
            require(isinstance(builds, list), name + ": missing actual LUT generation program readback")
            if isinstance(builds, list):
                seen = set()
                for build in builds:
                    index = build.get("pass")
                    require(type(index) is int and 0 <= index <= 2 and index not in seen, name + ": duplicate/invalid actual LUT pass")
                    seen.add(index)
                    program(record, "lutBuildDraws/" + str(index), {"AtmosphereGeometry", "AtmosphereComposition"}, supplied=build, role="lut")
                require(all(index in seen for index in range(3) if delta[index] > 0), name + ": built LUT has no bound-program readback")
                require(all(delta[index] > 0 for index in seen if type(index) is int and 0 <= index <= 2), name + ": LUT program recorded without an actual build increment")
            require(record.get("lutReady") is True and record.get("observerHeightKm", -1) > 0, name + ": physical LUT not ready")
            luts = record.get("luts", [])
            require(len(luts) == 3, name + ": expected three actual float LUTs")
            for index, (tag, width, height) in enumerate((("transmittance", 256, 64), ("multiple", 32, 32), ("sky-view", 512, 128))):
                values = texture(record, luts[index] if index < len(luts) else None, tag, width, height, 34842)
                if values is not None:
                    require(values.min() >= 0, name + ": negative physical float data " + tag)
                    if tag == "transmittance":
                        require(values.max() <= 1.0005, name + ": transmittance above one")
                    if tag == "multiple":
                        require(values[..., 3].max() < 1, name + ": unstable multiple-scattering closure")
            if vector(record.get("cameraPosition"), 3):
                expected_height = max(p["observerAltitude"] + (record["cameraPosition"][1] - p["groundLevel"]) * .001, .001)
                require(math.isclose(record.get("observerHeightKm", -1), expected_height, rel_tol=2e-6, abs_tol=2e-6), name + ": wrong camera-relative observer altitude")
        if record.get("weatherVolume"):
            values = texture(record, record["weatherVolume"], "weather-volume", 64, 64, 34836)
            surface = texture(record, record.get("weatherSurface"), "weather-surface", 128, 128, 34836)
            cpu = record.get("weatherCounts")
            require(vector(cpu, 4) and all(type(n) is int and n >= 0 for n in cpu) and sum(cpu) == 4096, name + ": invalid actual biome CPU counts")
            if values is not None:
                unknown = values[..., 3] == 0
                rain = (~unknown) & (values[..., 0] > 0)
                snow = (~unknown) & (~rain) & (values[..., 1] > 0)
                none = (~unknown) & (~rain) & (~snow)
                counts = [int(a.sum()) for a in (rain, snow, none, unknown)]
                require(counts == cpu == record["weatherVolume"].get("gpuWeatherCounts"), name + ": independent GPU atlas/CPU biome mismatch")
                require(np.isin(values[..., (0, 1, 3)], [0, 1]).all() and np.all(values[..., 2] == 0), name + ": malformed climate atlas values")
            if surface is not None:
                require(np.isin(surface[..., 1], [0, 1]).all() and np.all(surface[..., 3] == 1), name + ": malformed world height atlas")
            require(finite(record.get("weatherUpdateCpuMs")) and record.get("weatherUpdateCpuMs", -1) >= 0 and record.get("weatherUpdates", 0) > 0,
                    name + ": missing actual climate update measurement")
        needs_environment = name.startswith(("native-", "legacy-", "weather-", "performance-", "compat-"))
        if needs_environment:
            program(record, "environmentDraw", {"Quality", "Time", "CameraPosition", "EnvironmentStage", "WeatherSettings", "WeatherMotion", "RainSettings", "SnowSettings", "PrecipitationSettings", "FogSettings", "FogMotion", "VolumeCloudSettings", "VolumeCloudGeometry", "VolumeCloudMotion", "PipelineSettings", "SceneSampler", "DepthSampler", "InverseProjection", "InverseViewRotation"})
            require(record.get("snapshotFbo") != record.get("mainFbo") and type(record.get("snapshotFbo")) is int,
                    name + ": missing distinct framebuffer feedback protection")
            require(record.get("environmentDepthMaxDelta") == 0, name + ": environment changed scene depth")
            require(record.get("environmentDraw", {}).get("program") != sky.get("program"), name + ": reused sky as environment program")
            scale = 1 if name == "weather-tiny-snow-half" else p.get("environmentRenderScale")
            require(record.get("requestedRenderScale") == p.get("environmentRenderScale") and record.get("effectiveRenderScale") == scale,
                    name + ": actual render scale differs")
            environment = record.get("environmentDraw", {})
            scene_texture = environment.get("samplers", {}).get("SceneSampler", {}).get("texture")
            require(type(environment.get("boundColorTexture")) is int and environment["boundColorTexture"] > 0 and
                    environment["boundColorTexture"] != scene_texture, name + ": missing actual color attachment or texture read/write feedback")
            expected_size = [math.floor(size[0] * scale + .5), math.floor(size[1] * scale + .5)]
            require(environment.get("viewport") == [0, 0, *expected_size], name + ": actual environment GPU viewport differs from render scale")
            require(environment.get("fbo") == record.get("mainFbo") if scale >= .999 else
                    environment.get("fbo") not in (record.get("mainFbo"), record.get("snapshotFbo")), name + ": wrong actual environment output framebuffer")
            require(record.get("renderScaleReason") == ("tiny-snow-full-resolution" if name == "weather-tiny-snow-half" else "requested"), name + ": wrong resolution protection reason")
        if name.startswith("native-"):
            require(record.get("surfaceReason") == ("native-fabulous" if name.endswith("fabulous") else "native-fancy"), name + ": native route was not selected")
            program(record, "surfaceDraw", {"EnvironmentStage", "EnvironmentFogColor", "ModelViewMat", "ProjMat", "ChunkOffset", "CameraPosition", "OpticalEyeOffset", "WeatherSettings", "RainSettings", "FogSettings", "SnowSettings", "PrecipitationSettings"})
            stable_before, stable_after = record.get("stableCountsBefore"), record.get("stableCountsAfter")
            require(vector(stable_before, 7) and vector(stable_after, 7), name + ": no numeric stable-window counts")
            if vector(stable_before, 7) and vector(stable_after, 7):
                require(all(type(n) is int and n >= 0 for n in stable_before + stable_after), name + ": invalid stable-window integer counts")
                stable = np.asarray(stable_after) - stable_before
                require(stable[3] > 0 and stable[4] == 0 and stable[5] > 0 and stable[6] > 0,
                        name + ": native early/surface route did not eliminate final pass in stable window")
            require(record.get("environmentDraw", {}).get("early") is True, name + ": environment was not before translucent layer")
            surface = record.get("surfaceDraw", {})
            require(surface.get("program") not in (sky.get("program"), record.get("environmentDraw", {}).get("program")), name + ": native surface reused full-screen program")
            require(surface.get("fbo") != record.get("mainFbo") if name.endswith("fabulous") else
                    surface.get("fbo") == record.get("mainFbo"), name + ": actual native framebuffer differs from graphics mode")
            require(type(surface.get("vao")) is int and surface["vao"] > 0 and type(surface.get("vertexBuffer")) is int and surface["vertexBuffer"] > 0,
                    name + ": no actual native VAO/VBO")
            for key, count in (("chunkOffset", 3), ("modelView", 16), ("projection", 16), ("firstPosition", 3), ("blendFactors", 4)):
                require(vector(surface.get(key), count), name + ": invalid native VBO/matrix " + key)
            try:
                require(close(record.get("opticalEyeOffset"), optical_eye(record).tolist(), 1e-5), name + ": actual world optical eye differs from inverse projection/view")
                if vector(surface.get("modelView"), 16):
                    inverse = np.linalg.inv(matrix(surface["modelView"]))
                    require(np.allclose(inverse[:3, :3], matrix(sky["inverseViewRotation"], 3), rtol=1e-5, atol=1e-5), name + ": surface and sky camera rotation differ")
                    require(close(inverse[:3, 3].tolist(), record["inverseViewTranslation"], 1e-5), name + ": actual bob translation differs")
                require(close(surface.get("projection"), sky.get("projection", []), 1e-5), name + ": native surface projection differs from sky")
                if vector(surface.get("firstPosition"), 3):
                    require(all(-.01 <= value <= 16.5 for value in surface["firstPosition"]), name + ": first native vertex is not chunk-local geometry")
            except (ValueError, KeyError, np.linalg.LinAlgError) as error:
                errors.append(name + ": noninvertible/missing native matrix: " + str(error))
            reference = by.get("transparent-clear-fabulous" if name.endswith("fabulous") else "transparent-clear", {}).get("vanillaSurfaceDraw", {})
            baseline = by.get("transparent-clear-fabulous" if name.endswith("fabulous") else "transparent-clear", {})
            require(record.get("graphics") == baseline.get("graphics"), name + ": native alpha baseline has different actual graphics mode")
            require(surface.get("blendFactors") == reference.get("blendFactors") and surface.get("depthWrite") == reference.get("depthWrite"),
                    name + ": native alpha/depth policy differs from actual original graphics mode")
        elif needs_environment:
            require(delta[3] == 0 and delta[4] > 0 and delta[5] == 0 and delta[6] == 0, name + ": legacy environment route differs")
        if name == "native-fog-bob":
            # In 1.20.1 bobView changes the projection PoseStack; the level view
            # matrix can correctly retain zero translation. Compute the optical
            # eye independently from the projective matrix rather than treating
            # a Java 'bob enabled' flag as proof that bob was exercised.
            require(vector(record.get("projectionEyeView"), 3), name + ": missing measured projection eye")
            if vector(sky.get("projection"), 16):
                try:
                    eye = np.linalg.inv(matrix(sky["projection"])) @ np.asarray([0., 0., 1., 0.])
                    require(abs(eye[3]) > 1e-8, name + ": projection has no finite optical eye")
                    if abs(eye[3]) > 1e-8:
                        eye = (eye[:3] / eye[3]).tolist()
                        require(close(record.get("projectionEyeView"), eye, 1e-5) and np.linalg.norm(eye) > 1e-4,
                                name + ": bob did not exercise an independently reconstructed translated projection eye")
                except (ValueError, np.linalg.LinAlgError) as error:
                    errors.append(name + ": projection eye reconstruction failed: " + str(error))
        if name.startswith("weather-biome-"):
            require(type(record.get("fillBiomeResult")) is int and record["fillBiomeResult"] > 0, name + ": actual biome replacement missing")
            index = 0 if name.endswith("rain") else 1 if name.endswith("snow") else 2
            require(vector(record.get("weatherCounts"), 4) and record["weatherCounts"][index] > 0 and
                    record["weatherCounts"][(index + 1) % 3] == 0 and record["weatherCounts"][(index + 2) % 3] == 0,
                    name + ": actual biome classification differs")
        timings = record.get("gpuTimings", {})
        for key, value in timings.items():
            require(type(value.get("samples")) is int and value["samples"] > 0 and finite(value.get("medianMs")) and
                    finite(value.get("p95Ms")) and 0 <= value["medianMs"] <= value["p95Ms"], name + ": invalid GPU timer " + key)
        if name.startswith("performance-"):
            require(timings.get("environment-final", {}).get("samples", 0) >= 16, name + ": too few actual environment GPU timer samples")
            if p.get("environmentRenderScale", 1) < 1:
                require(timings.get("environment-composite", {}).get("samples", 0) >= 16, name + ": missing low-resolution composite timer samples")

    if mode == "native":
        for first, second in (("physical-day", "physical-sunset"), ("physical-day", "physical-night"),
                              ("physical-multi-off", "physical-multi-on"), ("physical-day", "physical-observer-high"),
                              ("physical-observer-high", "physical-observer-outside"), ("physical-day", "physical-density")):
            pair(first, second, "sky")
        pair("physical-day", "physical-reload", "sky", identical=True, same_recipe=True)
        for name in ("physical-sunset", "physical-night", "physical-multi-off", "physical-multi-on", "physical-observer-high", "physical-observer-outside"):
            if name in by:
                delta = np.asarray(by[name]["countsAfter"]) - by[name]["countsBefore"]
                require(list(delta[:3]) == [0, 0, 1], name + ": solar/view change rebuilt medium LUTs or missed sky LUT")
        if "physical-density" in by:
            delta = np.asarray(by["physical-density"]["countsAfter"]) - by["physical-density"]["countsBefore"]
            require(list(delta[:3]) == [1, 1, 1], "density change did not rebuild actual three LUTs")
        if "physical-yaw" in by:
            delta = np.asarray(by["physical-yaw"]["countsAfter"]) - by["physical-yaw"]["countsBefore"]
            require(list(delta[:3]) == [0, 0, 0], "pure azimuth change rebuilt solar-relative LUTs")
        if "physical-reload" in by:
            r = by["physical-reload"]
            require(r.get("generationAfter", 0) > r.get("generationBefore", 0), "resource reload did not rebuild actual sky shader")
            require(all(a > b for a, b in zip(r["countsAfter"][:3], r["countsBefore"][:3])), "reload did not regenerate all three GPU LUTs")
        for first, second in (("physical-density", "physical-yaw"), ("physical-day", "physical-reload")):
            for tag in ("transmittance", "multiple", "sky-view"):
                if (first, tag) in raw_arrays and (second, tag) in raw_arrays:
                    require(np.array_equal(raw_arrays[first, tag], raw_arrays[second, tag]), first + "/" + second + ": cached float LUT changed " + tag)
        for first, second in (("transparent-clear", "native-fog-fancy"), ("transparent-clear-fabulous", "native-fog-fabulous"),
                              ("legacy-fog", "native-fog-fancy"), ("legacy-rain", "native-rain-fancy"),
                              ("native-rain-fancy", "native-snow-fancy"), ("native-rain-fabulous", "native-snow-fabulous"),
                              ("weather-rain-time0", "weather-rain-time2"), ("weather-snow-time0", "weather-snow-time2"),
                              ("weather-rain-time0", "weather-snow-time0"), ("weather-roof-off", "weather-roof-on"),
                              ("weather-biome-rain", "weather-biome-snow"), ("weather-biome-rain", "weather-biome-dry"),
                              ("weather-biome-snow", "weather-biome-dry")):
            pair(first, second, same_recipe=first.startswith("weather-") and "time2" in second)
        pair("performance-zero-full", "performance-zero-half", identical=True, same_recipe=True, ignore=["environmentRenderScale"])
        pair("performance-medium", "performance-half")
        pair("performance-medium", "performance-threequarter")
        require(set(fields) == set(k for value in PACKS.values() for k in value) | set(COLORS.values()) | {"quality", "seed"},
                "checker pack mapping does not cover exactly 188 recipe fields")
        # Some pack lanes are CPU controls (native ownership/render scale) and may be optimized out.
        require(set(fields) - {"nativeTransparentMode", "environmentRenderScale"} <= coverage,
                "active GPU uniform coverage misses recipe fields: " + ",".join(sorted(set(fields) - coverage)))
    elif mode == "celestial":
        zero = ("atmosphereStrength thinCloudStrength volumeCloudStrength fogStrength cloudShadowStrength lightShaftStrength "
                "rainAmount thunderAmount specialStrength birdDensity rainbowStrength haloStrength auroraStrength meteorRate").split()
        common = {key: 0 for key in zero}
        common.update(nativeTransparentMode=0, precipitationMode=0, weatherFollowWorld=0, cycleSpeed=0,
                      environmentRenderScale=1, quality=2, twinkleAmount=0, nebulaSpeed=0)
        for name in CELESTIAL:
            if name not in by:
                continue
            record = by[name]
            giant = name.startswith("giant-")
            kind = name.split("-")[1] if giant and name != "giant-off" else "gas"
            reference = CELESTIAL_PRESETS[kind] if giant else "cinematicblackhole"
            require(record.get("referencePreset") == reference, name + ": actual referenced work differs")
            preset = validation_presets.get(reference, {})
            authored = preset.get("parameters", {})
            require(preset.get("schema") == "cosmic-sky/6" and set(authored) == set(fields), name + ": pinned preset is not strict188")
            if set(authored) != set(fields):
                continue
            overrides = common.copy()
            if giant:
                overrides.update({key: 0 for key in ("starDensity", "galaxyStrength", "nebulaStrength", "dustStrength", "blackHoleStrength")})
                if name == "giant-off":
                    overrides["celestialStrength"] = 0
            else:
                overrides["lensingStrength"] = 0 if name.endswith("off") else authored["lensingStrength"]
            if name.endswith("yaw45"):
                overrides["celestialYaw" if giant else "blackHoleYaw"] = -45
            require(record.get("validationOverrides") == overrides, name + ": extra/omitted/incorrect validation overrides")
            desired = {**authored, **overrides}
            require(record["recipe"]["parameters"] == desired, name + ": actual recipe differs from pinned preset plus explicit overrides")
            require(desired["giantBodyStyle"] == (list(CELESTIAL_PRESETS).index(kind) + 1 if giant else 0),
                    name + ": named preset selects the wrong structural celestial style")
            require(record.get("configuredYaw") == (45 if name.endswith("yaw45") else 180 if name.endswith("backside") else 0), name + ": configured camera yaw differs")
            require(record.get("configuredPitch") == -authored["celestialPitch" if giant else "blackHolePitch"], name + ": configured camera elevation differs")
            position = record.get("cameraPosition", [])
            if vector(position, 3):
                require(close([position[0], position[2]], [64, -128] if name.endswith("translation") else [0, 0], 1e-6) and
                        100 <= position[1] <= 103, name + ": actual isolated camera position differs")
            require(record.get("skyDraw", {}).get("uniforms", {}).get("GiantBodySettings", {}).get("location", -1) >= 0,
                    name + ": giant settings are not an actual active GPU uniform")
        for kind in CELESTIAL_PRESETS:
            first, second = "giant-" + kind + "-time0", "giant-" + kind + "-time2"
            pair("giant-off", first, "sky")
            pair(first, second, "sky", same_recipe=True)
        for first, second in (("giant-gas-time0", "giant-gas-reload"), ("giant-gas-time0", "giant-gas-translation"),
                              ("giant-off", "giant-gas-backside"), ("cinematic-lens-on", "cinematic-lens-reload")):
            pair(first, second, "sky", identical=True, same_recipe=True,
                 ignore=["celestialStrength"] if first == "giant-off" else [])
        pair("cinematic-lens-off", "cinematic-lens-on", "sky", same_recipe=True, ignore=["lensingStrength"])
        pair("cinematic-lens-on", "cinematic-lens-time2", "sky", same_recipe=True)
        for name in ("giant-gas-reload", "cinematic-lens-reload"):
            if name in by:
                require(by[name].get("generationAfter", 0) > by[name].get("generationBefore", 0), name + ": actual shader reload did not occur")
        # Translation must preserve this infinitely distant directional field.
        if "giant-gas-time0" in by and "giant-gas-translation" in by:
            movement = np.asarray(by["giant-gas-translation"]["cameraPosition"]) - by["giant-gas-time0"]["cameraPosition"]
            require(close(movement.tolist(), [64, 0, -128], 1e-6), "giant actual camera translation was not exercised")
        # The gas field is sampled in its local direction basis, so rotating both
        # camera and authored body should produce the same composition, with a
        # small allowance for quaternion/float32 raster rounding.
        if "giant-gas-time0" in by and "giant-gas-yaw45" in by:
            a, b = image("giant-gas-time0", "sky"), image("giant-gas-yaw45", "sky")
            diff = np.abs(a[..., :3].astype(np.int16) - b[..., :3].astype(np.int16))
            strong = int(np.count_nonzero(diff.max(axis=-1) >= 3))
            mean = float(diff.mean())
            require(mean <= .03 and strong <= a.shape[0] * a.shape[1] * .002, "rotated giant basis changed its image beyond float32 rounding")
            comparisons.append({"first": "giant-gas-time0", "second": "giant-gas-yaw45", "suffix": "sky", "pixelsAtLeast3LSB": strong, "meanAbsoluteRgb": mean})
        # Independent decoded apparent width verifies the FOV and resize effect.
        if all(name in by for name in ("giant-off", "giant-gas-time0", "giant-gas-fov100", "giant-gas-resize")):
            baseline = image("giant-off", "sky")[0, 0, :3].astype(np.int16)
            widths, heights = {}, {}
            for name in ("giant-gas-time0", "giant-gas-fov100", "giant-gas-resize"):
                pixels = image(name, "sky")[..., :3].astype(np.int16)
                mask = np.abs(pixels - baseline).max(axis=-1) >= 3
                points = np.argwhere(mask)
                require(len(points) > 100, name + ": missing measurable apparent celestial width")
                if len(points):
                    widths[name] = int(points[:, 1].max() - points[:, 1].min() + 1)
                    heights[name] = int(points[:, 0].max() - points[:, 0].min() + 1)
            if len(widths) == 3:
                fov_ratio = widths["giant-gas-fov100"] / widths["giant-gas-time0"]
                resize_width = widths["giant-gas-resize"]
                predicted_width = min(800, widths["giant-gas-time0"] * 600 / 540)
                resize_height_ratio = heights["giant-gas-resize"] / heights["giant-gas-time0"]
                require(math.isclose(fov_ratio, math.tan(math.radians(35)) / math.tan(math.radians(50)), rel_tol=.04), "actual giant width did not follow measured projection FOV")
                # The 800x600 view clips this deliberately huge ring horizontally.
                # Compare the clipped width and the complete unclipped height.
                require(math.isclose(resize_width, predicted_width, rel_tol=.04) and
                        math.isclose(resize_height_ratio, 600 / 540, rel_tol=.04), "actual giant extent did not follow resized projection and clipping")
                comparisons.append({"apparentWidths": widths, "apparentHeights": heights, "fovRatio": fov_ratio,
                                    "resizePredictedClippedWidth": predicted_width, "resizeHeightRatio": resize_height_ratio})
    elif mode == "compat":
        try:
            pin_path = Path(manifest) if manifest else folder / "compatibility-manifest.json"
            pin = strict(pin_path)
            runtime_binding["compatibilityManifestSha256"] = hashlib.sha256(pin_path.read_bytes()).hexdigest()
            require(pin.get("minecraft") == "1.20.1" and pin.get("forge") == "47.4.22", "compatibility manifest game/loader differ")
            for key in ("oculus", "embeddium", "shaderpack"):
                item = pin.get(key, {})
                require(isinstance(item.get("version"), str) and item["version"] and isinstance(item.get("file"), str) and
                        bool(re.fullmatch(r"[0-9a-f]{64}", item.get("sha256", ""))), "unpinned compatibility dependency " + key)
                path = Path(item.get("path", folder / item.get("file", "missing")))
                if not path.is_absolute():
                    path = folder / path
                require(path.is_file(), "missing pinned compatibility dependency " + key)
                if path.is_file():
                    require(hashlib.sha256(path.read_bytes()).hexdigest() == item.get("sha256"), "pinned compatibility dependency hash differs " + key)
                if key != "shaderpack":
                    require(summary.get("rendererMods", {}).get(key) == item.get("version"), "actual loaded dependency version differs " + key)
            for name in COMPAT:
                if name not in by:
                    continue
                record = by[name]
                owns = name in ("compat-on", "compat-reload")
                require(record.get("rendererReason") == ("shaderpack-owner-fallback" if owns else "embeddium-legacy-post"), name + ": wrong actual rendering owner")
                require(record.get("handoff") == owns, name + ": Iris API ownership differs")
                require(isinstance(record.get("pipeline"), str) and bool(record["pipeline"]) and record["pipeline"] not in ("vanilla", "unprepared"), name + ": no actual Iris pipeline class")
                if owns:
                    require(pin["shaderpack"]["file"] in record.get("pack", ""), name + ": actual selected pack differs from pinned package")
                    require(record.get("cloudHeight") == 192, name + ": vanilla cloud height was not restored to 192")
                    require(record["countsAfter"] == record["countsBefore"], name + ": Cosmic GPU draw/build deltas nonzero under pack owner")
                    require(not any(k in record for k in ("skyDraw", "environmentDraw", "surfaceDraw")), name + ": Cosmic draw recorded while shaderpack owns renderer")
                else:
                    require(record.get("recipe", {}).get("parameters", {}).get("nativeTransparentMode") == 1 and
                            record.get("surfaceReason") == "embeddium-native-surface-bypass-legacy",
                            name + ": requested native transparency did not exercise explicit Embeddium legacy fallback")
                    require(record["countsAfter"][4] > record["countsBefore"][4], name + ": disabled pack did not restore Cosmic environment")
                    require(record.get("cloudHeight") == "disabled", name + ": Cosmic cloud replacement did not resume")
            if "compat-reload" in by:
                record = by["compat-reload"]
                require(record.get("generationAfter", 0) > record.get("generationBefore", 0), "compatibility resource reload did not actually occur")
            pair("compat-off", "compat-off-again", identical=True, same_recipe=True)
            pair("compat-off", "compat-on")
        except (OSError, ValueError, TypeError, KeyError) as error:
            errors.append("compatibility manifest/ownership failure: " + str(error))
    return {"ok": not errors, "mode": mode, "scope": "complete-" + mode,
            "evidenceFolder": str(folder),
            "caseCount": len(records), "expectedCases": len(names), "checks": checked,
            "schemaSha256": hashlib.sha256(schema_path.read_bytes()).hexdigest(),
            "checkerSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), "sourceHashes": source_hashes(),
            "sourceHashProvenance": "checker source tree at verification; these are separate from actual runtime byte hashes",
            "runtimeBinding": runtime_binding, "classHashes": summary.get("classHashes", {}), "resourceHashes": summary.get("resourceHashes", {}),
            "activeGpuFieldCoverage": sorted(coverage), "rawFloatTextureCount": len(raw_arrays),
            "comparisons": comparisons, "stateComparisons": state_comparisons, "errors": errors,
            "limitations": ["Pinned renderer compatibility applies only to the manifest versions and named shaderpack.",
                            "GPU timer samples describe these isolated scenes, not general Minecraft FPS."]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--mode", choices=("native", "compat", "native-hook", "celestial"), default="native")
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--runtime-artifact", type=Path, help="Frozen development-mapped compiled classes and resources ZIP")
    parser.add_argument("--production-artifact", type=Path, help="Final Forge reobfuscated production JAR (resources compared)")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    result = check(args.folder, args.mode, args.manifest, args.runtime_artifact, args.production_artifact)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text + "\n", encoding="utf-8")
    print(text)
    raise SystemExit(0 if result["ok"] else 1)
