# 参考源码定位

针对用户提供的固定归档；行号对应 source-evidence.json 中 SHA256。不是最新上游行为保证。

## IP-STENCIL

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/renderer/RendererUsingStencil.java`

- `doRenderPortal(`：76, 123
- `renderPortalViewAreaToStencil(`：136, 175
- `clearDepthOfThePortalViewArea(`：152, 203
- `restoreDepthOfPortalViewArea(`：164, 231
- `clampStencilValue(`：167, 253

## IP-FBO

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/renderer/RendererUsingFrameBuffer.java`

- `doRenderPortal(`：58, 139
- `only support one-layer`：63
- `ip_setFrameBuffer`：75, 88
- `renderSecondBufferIntoMainBuffer(`：94, 126

## IP-TARGET

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/SecondaryFrameBuffer.java`

- `prepare(`：12, 16, 19
- `resize(`：32
- `TextureTarget`：4, 10, 21

## IP-COMPOSE

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/MyRenderHelper.java`

- `drawPortalAreaWithFramebuffer(`：146
- `depthMask`：155, 338, 384

## IP-AREA

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/ViewAreaRenderer.java`

- `renderPortalArea(`：25
- `renderViewAreaMesh(`：142
- `outputTriangle(`：147, 215, 219

## IP-RENDER

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/renderer/PortalRenderer.java`

- `getPortalsToRender(`：86
- `renderPortalContent(`：199
- `getMaxPortalLayer(`：202

## IP-CONTEXT

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/context_management/WorldRenderInfo.java`

- `pushRenderInfo(`：103
- `popRenderInfo(`：108
- `cameraPos`：37, 79, 91, 116, 178, 199, 218, 219, 271, 273
- `renderDistance`：60, 83, 94, 169, 203, 238, 239, 274

## IP-STACK

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/context_management/PortalRendering.java`

- `pushPortalLayer(`：33
- `popPortalLayer(`：38
- `getRenderingCameraPos(`：105
- `getActiveClippingPlane(`：178

## IP-WORLD-DRAW

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/MyGameRenderer.java`

- `renderWorldNew(`：96
- `renderLevel(`：231
- `oldWorld`：142, 143, 148, 166, 241, 242, 244, 248, 256
- `oldCamera`：150, 250, 278

## IP-CAMERA

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/portal/Portal.java`

- `transformPoint(`：464
- `transformLocalVec(`：467, 474, 1071, 1158, 1159, 1160, 1161
- `inverseTransformPoint(`：1224

## IP-CLIP

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/render/FrontClipping.java`

- `updateInnerClipping(`：48, 50, 53
- `transform`：79, 89, 97, 130
- `invert()`：95
- `transpose()`：96

## IP-HOOKS

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/mixin/client/render/MixinGameRenderer.java`

- `prepareRendering(`：114
- `finishRendering(`：129
- `resize(`：185, 193
- `renderLevel`：106, 122, 147, 157, 160, 168, 171, 276, 303

## IP-WORLDS

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/ClientWorldLoader.java`

- `createSecondaryClientWorld(`：315, 389
- `withSwitchedWorld(`：157, 527, 544, 585, 586, 602
- `finally`：479, 569
- `cleanUp(`：208, 258
- `disposeDimensionDynamically(`：92, 233

## IP-PACKETS

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/network/PacketRedirectionClient.java`

- `withSwitchedWorld`：59, 100
- `finally`：66, 112

## IP-CHUNK-LOAD

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/chunk_loading/PlayerChunkLoading.java`

- `send`：36, 90, 166, 170, 183, 190, 193, 201

## IP-CHUNKS

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/chunk_loading/ImmPtlChunkTracking.java`

- `getPlayersViewingChunk(`：456, 467
- `tick(`：362, 389, 398
- `updateForPlayer(`：149, 160, 375

## IP-ENTITIES

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/chunk_loading/EntitySync.java`

- `tick(`：20, 46
- `sendChanges`：63

## IP-WORLD-INFO

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/chunk_loading/WorldInfoSender.java`

- `Time`：5, 21, 53, 54, 55

## IP-SHAPE

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/portal/shape/PortalShape.java`

- `renderViewAreaMesh(`：100
- `getInnerClipping(`：70
- `raytracePortalShape`：38, 43, 51

## IP-FLAT-MESH

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/portal/shape/SpecialFlatPortalShape.java`

- `renderViewAreaMesh(`：156
- `mesh.boxIntersects(`：97, 218
- `getInnerClipping(`：116, 120

## IP-BOX

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/portal/shape/BoxPortalShape.java`

- `getInnerClipping(`：138
- `getOuterClipping(`：133
- `renderViewAreaMesh(`：189

## IP-IRIS

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/compat/iris_compatibility/IrisPortalRenderer.java`

- `blit`：227
- `copy`：126
- `depth`：67, 68, 103, 126
- `onHandRenderingEnded(`：171

## IP-IRIS-COMPAT

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/compat/iris_compatibility/IrisCompatibilityPortalRenderer.java`

- `one-layer`：87
- `isRendering(`：51, 86, 165
- `doRenderPortal(`：85, 212

## IP-SODIUM

`ImmersivePortalsMod-1.21/src/main/java/qouteall/imm_ptl/core/compat/sodium_compatibility/SodiumInterface.java`

- `Context`：29, 33, 59, 60, 64, 73
- `switch`：33, 64
- `Chunk`：5, 6, 41, 45, 84, 85, 86, 90, 91, 92

## VEIL-VIEW

`Veil-1.21/common/src/main/java/foundry/veil/api/client/render/VeilLevelPerspectiveRenderer.java`

- `render(`：70, 71, 78, 88
- `finally`：179
- `renderLevel(`：165
- `renderingPerspective`：52, 89, 153, 186, 229

## VEIL-FBO

`Veil-1.21/common/src/main/java/foundry/veil/api/client/render/framebuffer/AdvancedFbo.java`

- `create(`：40, 1083
- `free(`：27
- `resolveToFbo(`：220
- `bind(`：141, 169, 208

## VEIL-MANAGER

`Veil-1.21/common/src/main/java/foundry/veil/api/client/render/framebuffer/FramebufferManager.java`

- `setFramebuffer(`：159, 232
- `removeFramebuffer(`：178
- `free(`：85, 163, 218, 236

## VEIL-STACK

`Veil-1.21/common/src/main/java/foundry/veil/api/client/render/framebuffer/FramebufferStack.java`

- `STATE_STACK.add(`：47
- `STATE_STACK.removeFirst(`：74
- `push(`：34
- `pop(`：61

## VEIL-POST

`Veil-1.21/common/src/main/java/foundry/veil/api/client/render/post/PostProcessingManager.java`

- `runPipeline(`：264, 265, 274
- `catch`：244, 291, 360, 370
- `FramebufferStack`：18, 173, 183

## VEIL-PIPELINE

`Veil-1.21/common/src/main/java/foundry/veil/api/client/render/post/stage/CompositePostPipeline.java`

- `apply(`：48, 98, 125
- `free(`：130, 132
- `stages`：37, 50, 65, 66, 87, 89, 93, 94, 124, 131, 149, 165, 174, 184（更多匹配见 JSON）

## EXAMPLE-MIRROR

`veil-example-mod-1.21/src/main/java/foundry/veil/example/client/render/MirrorBlockEntityRenderer.java`

- `render(`：77, 196
- `renderLevel(`：150
- `perspective`：183

## EXAMPLE-STAGE

`veil-example-mod-1.21/src/main/java/foundry/veil/example/VeilExampleModClient.java`

- `AFTER_LEVEL`：57
- `MirrorBlockEntityRenderer.renderLevel`：58

## LOD-VFX

`Lodestone-1.21/src/main/java/team/lodestar/lodestone/systems/rendering/builder/WorldVFXBuilder.java`

- `renderSphere(`：389
- `renderTrail(`：270, 271, 275, 276, 280
- `renderQuad(`：327, 328, 330, 348, 352, 353, 356, 357, 360, 362, 365, 366, 369, 373（更多匹配见 JSON）
- `renderBeam(`：239, 240, 243, 244, 247, 248, 251, 252, 256

## LOD-TRAIL

`Lodestone-1.21/src/main/java/team/lodestar/lodestone/systems/rendering/trail/TrailPointBuilder.java`

- `addTrailPoint(`：30, 31, 34
- `tickTrailPoints(`：52
- `build(`：64, 68

## LOD-BLOOM

`Lodestone-1.21/src/main/resources/assets/lodestone/shaders/post/pb_bloom.json`

- `depth`：3

## LOD-BLOOM-TARGETS

`Lodestone-1.21/src/main/java/team/lodestar/lodestone/modules/rendering/postprocess/effects/PhysicallyBasedBloomPostProcessor.java`

- `resize(`：43, 58, 68, 70, 71, 72, 73, 74, 75, 78, 80
- `width /`：80
- `copyDepthFrom(`：104, 107, 109
