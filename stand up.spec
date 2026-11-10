# -*- mode: python ; coding: utf-8 -*-
import os
import sys
from pathlib import Path

# Build tools must not resolve system dependencies from unrelated tools on PATH
# (e.g. Poppler's ICU exports versioned names incompatible with Windows ICU).
if sys.platform == 'win32':
    windows = Path(os.environ['SystemRoot'])
    os.environ['PATH'] = os.pathsep.join(str(p) for p in (
        windows / 'System32', windows, Path(sys.executable).parent,
        Path(sys.base_prefix), Path(sys.base_prefix) / 'Scripts'))


a = Analysis(
    ['work_buddy.py'],
    pathex=[],
    binaries=[],
    datas=[('assets/app-icon.ico', 'assets'), ('assets/gentle-chime.wav', 'assets'), ('assets/quiet-steps-loop.wav', 'assets'), ('assets/fluent', 'assets/fluent'), ('THIRD_PARTY_NOTICES.md', '.')],
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='stand up',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=['assets/app-icon.ico'],
)
