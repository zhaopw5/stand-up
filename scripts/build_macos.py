"""Build a native macOS .app and distributable ZIP on a Mac runner."""
import hashlib
import platform
import shutil
import subprocess
import sys
from pathlib import Path
from PIL import Image

if sys.platform != 'darwin':
    raise SystemExit('Run this script on macOS; cross-compiling a Mac app on Windows is not supported.')
root = Path(__file__).resolve().parents[1]
arch = platform.machine()
if arch not in ('arm64', 'x86_64'):
    raise SystemExit(f'Unsupported architecture: {arch}')
Image.open(root / 'assets/app-icon.png').save(root / 'assets/app-icon.icns')
subprocess.run([sys.executable, '-m', 'PyInstaller', '--noconfirm', '--windowed', '--onedir',
    '--name', 'stand up', '--osx-bundle-identifier', 'io.github.zhaopw5.stand-up',
    '--target-arch', arch, '--icon', str(root / 'assets/app-icon.icns'),
    '--add-data', f'{root / "assets/app-icon.png"}:assets',
    '--add-data', f'{root / "assets/gentle-chime.wav"}:assets',
    '--add-data', f'{root / "assets/fluent"}:assets/fluent',
    '--add-data', f'{root / "THIRD_PARTY_NOTICES.md"}:.',
    '--specpath', 'build/macos-spec', str(root / 'work_buddy.py')], cwd=root, check=True)
app = root / 'dist/stand up.app'
# PyInstaller ad-hoc signs the bundle. This is not Developer ID signing or notarization.
subprocess.run(['codesign', '--verify', '--deep', '--strict', str(app)], check=True)
subprocess.run(['file', str(app / 'Contents/MacOS/stand up')], check=True)
release = root / 'dist' / f'stand-up-macos-{arch}'
release.mkdir(exist_ok=True)
shutil.copytree(app, release / app.name, symlinks=True, dirs_exist_ok=True)
shutil.copytree(root / 'assets/fluent', release / 'licenses/fluent', dirs_exist_ok=True)
shutil.copy(root / 'THIRD_PARTY_NOTICES.md', release)
shutil.copy(root / 'docs/MACOS.md', release / 'READ-ME.md')
zip_path = release.with_suffix('.zip')
subprocess.run(['ditto', '-c', '-k', '--sequesterRsrc', '--keepParent', str(release), str(zip_path)], check=True)
zip_path.with_suffix('.sha256').write_text(hashlib.sha256(zip_path.read_bytes()).hexdigest() + '  ' + zip_path.name + '\n')
print(f'Built {zip_path}', flush=True)
