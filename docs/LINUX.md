# stand up：Ubuntu Linux 预览版

适用于 Ubuntu 24.04 x86_64。便携包已包含 Python/Qt 运行时，无需安装 Python。

1. 从 GitHub Releases 下载并解压 stand-up-linux-x86_64.tar.gz。
2. 打开其中的 stand up 目录，运行同名的 stand up 可执行文件。请保留旁边的 _internal 目录。
3. 右上角箭头切换小窗；小窗左上角恢复主窗口，右上角图钉切换置顶。拖动时间区域可移动小窗。

在 GNOME Wayland 中，如果存在 X11 显示连接且没有明确指定 QT_QPA_PLATFORM，程序优先使用 XWayland（xcb），以兼容窗口移动和小窗切换。这不会更改整个桌面会话或显卡驱动。用户明确指定的 Qt 后端会被保留。

本次修复已在作者的 Ubuntu 24.04 GNOME Wayland 电脑上确认解决小窗消失问题；自动测试覆盖普通窗口和最大化窗口的小窗切换与恢复。其它 Linux 发行版、桌面环境、多屏和不同音频设备仍需实际验证。

需要图形桌面及 PulseAudio/PipeWire 音频服务。构建参考环境的运行库包括 libegl1、libgl1、libpulse0、libxcb-cursor0 和 libxkbcommon-x11-0。

此包不是 AppImage 或 Debian 安装包，没有系统安装器和自动更新。关闭窗口即可退出。
