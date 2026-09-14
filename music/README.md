# 慢步 / Quiet Steps — 背景音乐试听 v1

60 秒、64 BPM、16 小节、无歌词，立体声 WAV。

本试听由本项目的 AI 助手编排音符并编写合成脚本：
- 没有导入第三方歌曲、录音、钢琴采样、SoundFont 或音乐生成服务输出。
- 音色由正弦波泛音、微量失谐、衰减包络及延迟叠加合成。
- 音符与和弦记录在 quiet-steps-score.json，脚本为 scripts/build_background_music.py。
- 余音环绕到循环起点，适合单曲循环。首次播放从稳定循环状态开始。
- 这是合成键盘/钢琴风格试听，不是实录钢琴。

从项目根目录重新生成：

    python -m pip install numpy
    python scripts/build_background_music.py

这是制作过程与素材来源说明，不是“不侵权”保证或音乐著作权归属结论。
未进行与全部已发行音乐的相似性检索。此曲已作为默认背景音乐内置进应用；启动时不自动播放。
