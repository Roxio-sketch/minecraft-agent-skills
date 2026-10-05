/*
 * Original WebGL 2 postprocess for linear black-hole material output.
 * Multiscale bloom is informed by the general downsample/filter/combine method
 * studied in Eric Bruneton's BSD bloom renderer; no source or filter tables are
 * copied. This is an artistic glow, not a calibrated optical scattering model.
 *
 * const post = window.createBlackHolePost(gl);
 * post.render(hdrTexture, width, height,
 *   { exposure: 1, bloom: 0.35, bloomRadius: 1,
 *     outputWidth: canvas.width, outputHeight: canvas.height }, outputFramebuffer);
 * post.dispose();
 *
 * Input: complete, single-sample linear RGBA16F texture (no prior gamma, exposure,
 * or tone mapping). Output: display-encoded straight RGB and the source alpha.
 * width/height describe the source texture and determine bloom attachment sizes.
 * Optional integer outputWidth/outputHeight each default to the corresponding
 * source dimension. The final pass filters into this output size, so a 2x scene
 * can resolve directly to the native canvas. The default framebuffer's actual
 * dimensions are checked; for an FBO, the caller must provide a matching color
 * attachment 0 (WebGL cannot query a texture attachment's allocated dimensions).
 * The destination must not attach the input texture. Do not
 * perform another sRGB conversion or blend this result as linear scene light.
 * exposure: [0, 64], bloom: [0, 16], bloomRadius: [0, 8]. Radius is a multiplier:
 * 1 gives a compact half-resolution glow and a wider eighth-resolution glow.
 * bloom=0 is a single sharp tone-map pass and allocates no bloom attachments.
 * Requires WebGL 2 + EXT_color_buffer_float. Recreate after context restoration.
 */
'use strict';
(function () {
  const vertex = `#version 300 es
precision highp float;
out vec2 vUV;
void main() {
  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
  vUV = p;
  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}`;
  const prefix = `#version 300 es
precision highp float;
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uSource;
vec3 sampleLight(vec2 uv) {
  // Bloom outside the image is zero, not a repeated bright screen-edge texel.
  if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec3(0.0);
  return clamp(texture(uSource, uv).rgb, vec3(0.0), vec3(65504.0));
}
`;
  const downsample = prefix + `
uniform vec2 uTexel;
uniform bool uExtract;
vec3 bright(vec2 uv) {
  vec3 c = sampleLight(uv);
  if (!uExtract) return c;
  float peak = max(c.r, max(c.g, c.b));
  // A soft knee avoids flicker as a moving highlight crosses the threshold.
  float soft = clamp(peak - 0.5, 0.0, 1.0);
  float energy = max(peak - 1.0, soft * soft * 0.5);
  return c * (energy / max(peak, 0.00001));
}
void main() {
  vec2 d = uTexel * 0.5;
  vec3 c = bright(vUV + vec2(-d.x, -d.y)) + bright(vUV + vec2(d.x, -d.y))
         + bright(vUV + vec2(-d.x, d.y)) + bright(vUV + vec2(d.x, d.y));
  fragColor = vec4(c * 0.25, 1.0);
}`;
  const blur = prefix + `
uniform vec2 uStep;
void main() {
  // Symmetric binomial kernel; bilinear sampling smoothly scales its support.
  vec3 c = sampleLight(vUV) * (6.0 / 16.0);
  c += (sampleLight(vUV - uStep) + sampleLight(vUV + uStep)) * (4.0 / 16.0);
  c += (sampleLight(vUV - uStep * 2.0) + sampleLight(vUV + uStep * 2.0)) * (1.0 / 16.0);
  fragColor = vec4(c, 1.0);
}`;
  const composite = prefix + `
uniform sampler2D uNear;
uniform sampler2D uFar;
uniform float uExposure;
uniform float uBloom;
uniform bool uMaskBloomWithSourceAlpha;
vec3 displayTransform(vec3 light) {
  vec3 x = max(light * uExposure, vec3(0.0));
  // Common fitted filmic curve, approximately ACES; not the ACES RRT/ODT.
  vec3 mapped = clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
  return mix(mapped * 12.92, 1.055 * pow(mapped, vec3(1.0 / 2.4)) - 0.055,
             step(vec3(0.0031308), mapped));
}
void main() {
  vec3 light = sampleLight(vUV);
  if (uBloom > 0.0) {
    float visibility=uMaskBloomWithSourceAlpha?clamp(texture(uSource,vUV).a,0.0,1.0):1.0;
    light += visibility*uBloom * (texture(uNear, vUV).rgb * 0.6 + texture(uFar, vUV).rgb * 0.4);
  }
  fragColor = vec4(displayTransform(light), uMaskBloomWithSourceAlpha?1.0:clamp(texture(uSource, vUV).a, 0.0, 1.0));
}`;

  window.createBlackHolePost = function createBlackHolePost(gl) {
    if (!gl || typeof gl.createVertexArray !== 'function') throw Error('Black-hole postprocess requires WebGL 2.');
    if (!gl.getExtension('EXT_color_buffer_float')) throw Error('Black-hole HDR bloom requires EXT_color_buffer_float.');
    const ownedPrograms = [], ownedShaders = [];
    const targets = [];
    const maxSize = gl.getParameter(gl.MAX_TEXTURE_SIZE);
    const capabilities = [gl.BLEND, gl.DEPTH_TEST, gl.CULL_FACE, gl.SCISSOR_TEST,
      gl.STENCIL_TEST, gl.RASTERIZER_DISCARD, gl.DITHER];
    let vao = null, sampler = null, disposed = false, sizeKey = '';

    function dispose() {
      if (disposed) return;
      for (const t of targets) { gl.deleteFramebuffer(t.framebuffer); gl.deleteTexture(t.texture); }
      targets.length = 0;
      for (const p of ownedPrograms) gl.deleteProgram(p);
      for (const s of ownedShaders) gl.deleteShader(s);
      if (vao) gl.deleteVertexArray(vao);
      if (sampler) gl.deleteSampler(sampler);
      disposed = true;
    }
    function makeProgram(fragment, names) {
      const p = gl.createProgram();
      if (!p) throw Error('Could not allocate the postprocess program.');
      ownedPrograms.push(p);
      for (const [type, code] of [[gl.VERTEX_SHADER, vertex], [gl.FRAGMENT_SHADER, fragment]]) {
        const s = gl.createShader(type);
        if (!s) throw Error('Could not allocate a postprocess shader.');
        ownedShaders.push(s);
        gl.shaderSource(s, code); gl.compileShader(s);
        if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(s) || 'Postprocess shader compilation failed.');
        gl.attachShader(p, s);
      }
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(p) || 'Postprocess link failed.');
      return { program: p, uniforms: Object.fromEntries(names.map(n => [n, gl.getUniformLocation(p, n)])) };
    }
    let down, gaussian, finish;
    try {
      down = makeProgram(downsample, ['uSource', 'uTexel', 'uExtract']);
      gaussian = makeProgram(blur, ['uSource', 'uStep']);
      finish = makeProgram(composite, ['uSource', 'uNear', 'uFar', 'uExposure', 'uBloom', 'uMaskBloomWithSourceAlpha']);
      vao = gl.createVertexArray(); sampler = gl.createSampler();
      if (!vao || !sampler) throw Error('Could not allocate postprocess state.');
      gl.samplerParameteri(sampler, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.samplerParameteri(sampler, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.samplerParameteri(sampler, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.samplerParameteri(sampler, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      // Programs retain the linked shader code; free the standalone objects now.
      for (const s of ownedShaders) gl.deleteShader(s);
      ownedShaders.length = 0;
    } catch (error) { dispose(); throw error; }

    function captureState() {
      const state = {
        program: gl.getParameter(gl.CURRENT_PROGRAM), vao: gl.getParameter(gl.VERTEX_ARRAY_BINDING),
        framebuffer: gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING), viewport: gl.getParameter(gl.VIEWPORT),
        activeTexture: gl.getParameter(gl.ACTIVE_TEXTURE), colorMask: gl.getParameter(gl.COLOR_WRITEMASK),
        depthMask: gl.getParameter(gl.DEPTH_WRITEMASK), enabled: capabilities.map(c => gl.isEnabled(c)), textures: []
      };
      for (let unit = 0; unit < 3; unit++) {
        gl.activeTexture(gl.TEXTURE0 + unit);
        state.textures.push([gl.getParameter(gl.TEXTURE_BINDING_2D), gl.getParameter(gl.SAMPLER_BINDING)]);
      }
      return state;
    }
    function restoreState(state) {
      gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, state.framebuffer);
      gl.viewport(...state.viewport); gl.useProgram(state.program); gl.bindVertexArray(state.vao);
      gl.colorMask(...state.colorMask); gl.depthMask(state.depthMask);
      capabilities.forEach((c, i) => state.enabled[i] ? gl.enable(c) : gl.disable(c));
      state.textures.forEach(([texture, previousSampler], unit) => {
        gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, texture); gl.bindSampler(unit, previousSampler);
      });
      gl.activeTexture(state.activeTexture);
    }
    function bindTexture(unit, texture) {
      gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, texture); gl.bindSampler(unit, sampler);
    }
    function target(index, width, height) {
      if (!targets[index]) {
        targets[index] = { framebuffer: gl.createFramebuffer(), texture: gl.createTexture() };
        if (!targets[index].framebuffer || !targets[index].texture) throw Error('Could not allocate a bloom attachment.');
      }
      const t = targets[index]; t.width = width; t.height = height;
      bindTexture(0, t.texture);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA16F, width, height, 0, gl.RGBA, gl.HALF_FLOAT, null);
      gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, t.framebuffer);
      gl.framebufferTexture2D(gl.DRAW_FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t.texture, 0);
      if (gl.checkFramebufferStatus(gl.DRAW_FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) throw Error('RGBA16F bloom framebuffer is incomplete.');
      return t;
    }
    function ensureTargets(width, height) {
      const nextKey = width + 'x' + height;
      if (nextKey === sizeKey) return;
      const half = [Math.max(1, Math.ceil(width / 2)), Math.max(1, Math.ceil(height / 2))];
      const quarter = half.map(n => Math.max(1, Math.ceil(n / 2)));
      const eighth = quarter.map(n => Math.max(1, Math.ceil(n / 2)));
      target(0, ...half); target(1, ...half); target(2, ...quarter); target(3, ...eighth); target(4, ...eighth);
      sizeKey = nextKey;
    }
    function drawInto(t) {
      gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, t.framebuffer); gl.viewport(0, 0, t.width, t.height);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    }
    function reduce(source, width, height, destination, extract) {
      gl.useProgram(down.program); bindTexture(0, source);
      gl.uniform1i(down.uniforms.uSource, 0); gl.uniform2f(down.uniforms.uTexel, 1 / width, 1 / height);
      gl.uniform1i(down.uniforms.uExtract, extract ? 1 : 0); drawInto(destination);
    }
    function soften(source, destination, x, y) {
      gl.useProgram(gaussian.program); bindTexture(0, source.texture);
      gl.uniform1i(gaussian.uniforms.uSource, 0); gl.uniform2f(gaussian.uniforms.uStep, x / source.width, y / source.height);
      drawInto(destination);
    }
    function option(settings, name, fallback, maximum) {
      const value = settings[name] === undefined ? fallback : settings[name];
      if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > maximum)
        throw RangeError(name + ' must be a finite number in [0, ' + maximum + '].');
      return value;
    }
    function render(sourceTexture, width, height, settings = {}, targetFramebuffer = null) {
      if (disposed) throw Error('Black-hole postprocess has been disposed.');
      if (gl.isContextLost()) throw Error('WebGL context lost; recreate the postprocess after restoration.');
      if (!sourceTexture || !gl.isTexture(sourceTexture)) throw TypeError('sourceTexture must be a texture from this context.');
      if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1 || width > maxSize || height > maxSize)
        throw RangeError('Postprocess dimensions must be positive integers within MAX_TEXTURE_SIZE.');
      if (!settings || typeof settings !== 'object' || Array.isArray(settings)) throw TypeError('Postprocess settings must be an object.');
      if (targetFramebuffer !== null && !gl.isFramebuffer(targetFramebuffer)) throw TypeError('Invalid target framebuffer.');
      const outputWidth = settings.outputWidth === undefined ? width : settings.outputWidth;
      const outputHeight = settings.outputHeight === undefined ? height : settings.outputHeight;
      if (!Number.isInteger(outputWidth) || !Number.isInteger(outputHeight) || outputWidth < 1 || outputHeight < 1
          || outputWidth > maxSize || outputHeight > maxSize)
        throw RangeError('Postprocess output dimensions must be positive integers within MAX_TEXTURE_SIZE.');
      if (targetFramebuffer === null && (outputWidth !== gl.drawingBufferWidth || outputHeight !== gl.drawingBufferHeight))
        throw RangeError('Postprocess output dimensions must match the default framebuffer; pass outputWidth/outputHeight for a resized source.');
      const exposure = option(settings, 'exposure', 1, 64), bloom = option(settings, 'bloom', 0, 16);
      const radius = option(settings, 'bloomRadius', 1, 8);
      if(settings.maskBloomWithSourceAlpha!==undefined&&typeof settings.maskBloomWithSourceAlpha!=='boolean')
        throw TypeError('maskBloomWithSourceAlpha must be a boolean.');
      const maskBloomWithSourceAlpha=settings.maskBloomWithSourceAlpha===true;
      const state = captureState();
      let destinationBuffers = null;
      try {
        capabilities.forEach(c => gl.disable(c)); gl.colorMask(true, true, true, true); gl.depthMask(false);
        gl.bindVertexArray(vao);
        if (bloom > 0) {
          ensureTargets(width, height);
          const [near, nearScratch, middle, far, farScratch] = targets;
          reduce(sourceTexture, width, height, near, true);
          reduce(near.texture, near.width, near.height, middle, false);
          reduce(middle.texture, middle.width, middle.height, far, false);
          soften(near, nearScratch, radius * 1.5, 0); soften(nearScratch, near, 0, radius * 1.5);
          soften(far, farScratch, radius * 2.0, 0); soften(farScratch, far, 0, radius * 2.0);
        }
        gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, targetFramebuffer);
        if (gl.checkFramebufferStatus(gl.DRAW_FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) throw Error('Postprocess destination framebuffer is incomplete.');
        if (targetFramebuffer && gl.getFramebufferAttachmentParameter(gl.DRAW_FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) === sourceTexture)
          throw Error('Postprocess source and destination must not share a texture.');
        const count = targetFramebuffer ? gl.getParameter(gl.MAX_DRAW_BUFFERS) : 1;
        destinationBuffers = Array.from({ length: count }, (_, i) => gl.getParameter(gl.DRAW_BUFFER0 + i));
        gl.drawBuffers([targetFramebuffer ? gl.COLOR_ATTACHMENT0 : gl.BACK]);
        gl.viewport(0, 0, outputWidth, outputHeight); gl.useProgram(finish.program);
        bindTexture(0, sourceTexture);
        // Complete fallback bindings also keep bloom=0 independent of previous GL state.
        bindTexture(1, bloom > 0 ? targets[0].texture : sourceTexture);
        bindTexture(2, bloom > 0 ? targets[3].texture : sourceTexture);
        gl.uniform1i(finish.uniforms.uSource, 0); gl.uniform1i(finish.uniforms.uNear, 1); gl.uniform1i(finish.uniforms.uFar, 2);
        gl.uniform1f(finish.uniforms.uExposure, exposure); gl.uniform1f(finish.uniforms.uBloom, bloom);
        gl.uniform1i(finish.uniforms.uMaskBloomWithSourceAlpha,maskBloomWithSourceAlpha?1:0);
        gl.drawArrays(gl.TRIANGLES, 0, 3);
      } finally {
        if (destinationBuffers) { gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, targetFramebuffer); gl.drawBuffers(destinationBuffers); }
        restoreState(state);
      }
    }
    return { render, dispose };
  };
})();
