# 炼丹炉v0.2验证

当前配方alchemy-vfx/2，52字段，51个可调参数；完整31字段v1经过严格校验后升级。共享内核SHA eabbd06cd671d5a1a7afc4a788fd0689d29a291199a218893fc4fda5c6aa7cc5。默认400步、全分辨率；实际炉体为5×3.577099×5米、56462三角形、六材质。

圆底中心火床保留946版本造型；四个真实对角炉窗增加热气、火星和药香，源位法线距2.085m、高1.393125m。炉内素材光团与向心流保持在实体炉底之上；三层破碎环流有三维厚度。素材光团是辅助体积特效，宿主材料物品继续由真实ItemRenderer绘制。热气目前为温暖透明气流，未实现背景折射。

新21字段逐个改变真实像素；冻结重复0差、时间变化162985像素、实体深度负对照87617像素、恢复0差；六非法HTTP400。GL0、无页面错误，见evidence/qa-extension.json。该记录源SHA采用当时CRLF字节；冻结后统一LF，代码与画面未改。

Forge 1280×720四个新独立场景均GL0，见forge/evidence/game-extension-v2/game-results.json：关闭基线、完整近景、圆底空隙、实体遮挡。共享源码同资源/JAR哈希、配方一致，QA/宿主资源排除见forge/evidence/build-verification.json。新场景没有分别认证全部炉内阶段的宿主外观。

旧c7bad/320步十阶段测试是历史接入证明，记录在VALIDATION-before-v2.md；已被用户否定的旧18火舌不作为当前美术证据。946中心火床的旧4场景与GPU探针另在forge/evidence/game-firebed400。

正式宿主源码与JAR未替换，用户实例未安装新炉体库。新的可审查hook patch仍在forge/integration；发布包消费真实宿主。九色源自宿主weapon_flames文件并生成27份可编辑配方，见相邻vfx-palettes。
