"""Build the portable Windows ZIP: the PyInstaller exe plus a desktop-shortcut helper.

Run from the repository root:

    python scripts/build_windows.py              # full build (needs PyInstaller)
    python scripts/build_windows.py --repack     # reuse dist/stand up.exe, rebuild the ZIP only
"""
import argparse
import hashlib
import locale
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

APP_NAME = 'stand up'
README_TEXT = '''stand up - Windows x64 portable build.

1. Double-click "stand up.exe" to start the app.
2. Double-click "创建桌面快捷方式.bat" once to put an icon on your desktop.
   Keep that .bat in the same folder as the .exe; run it again if you move the folder.

No installation is needed and nothing is written to the registry.
This build is not code-signed. See THIRD_PARTY_NOTICES.md for icon notices.
'''


def build_exe(root):
    subprocess.run([sys.executable, '-m', 'PyInstaller', '--noconfirm', '--clean',
                    str(root / 'stand up.spec')], cwd=root, check=True)


def set_medium_integrity(path):
    """Give a Windows EXE an explicit Medium integrity label.

    A Low mandatory label on the workspace (for example from a sandbox) is
    inherited by newly built executables. Windows then starts them at Low,
    where writes to the normal user Temp directory are denied. The label is
    file-system metadata, so shipping copies need it set again as well.
    """
    if sys.platform != 'win32':
        return
    try:
        result = subprocess.run(
            ['icacls', str(path), '/setintegritylevel', 'Medium'],
            capture_output=True,
        )
    except FileNotFoundError as error:
        raise SystemExit(f'icacls not found; cannot set the integrity level of {path}.') from error
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).decode(
            locale.getpreferredencoding(False), errors='replace').strip()
        raise SystemExit(
            f'Failed to set Medium integrity on {path} (icacls exit {result.returncode}).\n'
            f'{detail}\n'
            'Run the build from a normal PowerShell terminal; '
            'a Low-integrity process cannot change this label.'
        )
    print(f'Set Medium integrity: {path.name}')


def assemble(root, release):
    """Lay out the folder that becomes the ZIP. Files sit at the top level so
    extracting the archive gives the user the exe directly."""
    if release.exists():
        shutil.rmtree(release)
    release.mkdir(parents=True)
    shutil.copy2(root / 'dist' / f'{APP_NAME}.exe', release / f'{APP_NAME}.exe')
    shutil.copy2(root / '创建桌面快捷方式.bat', release / '创建桌面快捷方式.bat')
    shutil.copy2(root / 'THIRD_PARTY_NOTICES.md', release / 'THIRD_PARTY_NOTICES.md')
    shutil.copytree(root / 'assets' / 'fluent', release / 'licenses' / 'fluent')
    (release / 'README.txt').write_text(README_TEXT, encoding='utf-8')


def write_zip(release):
    zip_path = release.with_suffix('.zip')
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(release.rglob('*')):
            if path.is_file():
                archive.write(path, path.relative_to(release).as_posix())
    digest = hashlib.sha256(zip_path.read_bytes()).hexdigest()
    zip_path.with_suffix('.sha256').write_text(
        f'{digest}  {zip_path.name}\n', encoding='utf-8')
    return zip_path, digest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repack', action='store_true',
                        help='reuse the existing dist/stand up.exe instead of running PyInstaller')
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    release = root / 'dist' / 'stand-up-windows-x64'
    exe = root / 'dist' / f'{APP_NAME}.exe'
    if not args.repack:
        build_exe(root)
    if not exe.is_file():
        raise SystemExit(f'Missing {exe}; run PyInstaller first (drop --repack).')
    set_medium_integrity(exe)
    assemble(root, release)
    set_medium_integrity(release / f'{APP_NAME}.exe')
    zip_path, digest = write_zip(release)
    print(f'Built {zip_path}')
    print(f'sha256: {digest}  {zip_path.name}')


if __name__ == '__main__':
    main()
