# 本地视频采样

`scripts/sample_video.py` 需要 Python 3、Pillow、FFmpeg、FFprobe。优先使用本机已有环境，不为抽帧重装系统依赖。脚本不上传媒体；拒绝已有非空输出目录，不覆盖视频、图片或旧报告。

本机已验证调用（输出目录需改为新名称）：

```powershell
& 'E:/JianyingAI/venvs/editor/Scripts/python.exe' `
  'C:/Users/USER/.codex/skills/minecraft-huahong/scripts/sample_video.py' `
  --input 'C:/Users/USER/Downloads/元婴遁光一个比一个帅啊.mp4' `
  --out 'E:/着色器渲染/research/new-flight-samples' `
  --ffmpeg 'E:/JianyingAI/tools/ffmpeg.exe' `
  --ffprobe 'E:/JianyingAI/tools/ffprobe.exe' `
  --count 96 --width 960
```

通用接口：`--input <绝对视频路径> --out <新目录> [--start 秒 --end 秒 --count 24 --width 960]`。FFmpeg/FFprobe在PATH时不用指定工具路径。

- `--count` 均匀覆盖首尾，1张取start；默认区间为整个视频流。
- `--times 10.6,10.8,11.0` 显式时刻，与 start/end/count 互斥。时间单位是首个解码帧之后的秒数。
- 输出每帧原图、`manifest.json`、`source-probe.json`、每张最多12帧的联系表。manifest保存完整源哈希、视频时长、请求时间、实际解码PTS和末帧。
- 请求尾部端点时映射到最后可解码帧，容器时长与视频流末帧可能略有差异。不要把标签请求时间当作精确命中的PTS。
- 当前实现先读取全视频帧时间戳，适合这类短参考片。很长视频的探测开销应评估，不把脚本当实时播放器或逐帧理解工具。

粗看只建立目录；镜头短于采样间隔可能遗漏。对重要形态至少查看一段连续时间采样，同时记录角色、前后方向、尾迹是否留在世界、相机变化和生命周期。不要用抽到的静帧证明流速、模拟机制或多人一致性。
