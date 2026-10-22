"""Check packaged startup, then stop only the application processes started here."""
import os
from pathlib import Path
import subprocess
import sys
import time

executable = Path(sys.argv[1]).resolve()
assert executable.is_file(), executable
environment = os.environ.copy()
environment['QT_QPA_PLATFORM'] = 'offscreen'
log_path = Path('artifacts/packaged-launch.log').resolve()
log_path.parent.mkdir(exist_ok=True)
with log_path.open('w') as log:
    process = subprocess.Popen(
        [str(executable)], cwd=executable.parent, env=environment,
        stdout=log, stderr=subprocess.STDOUT,
    )
    try:
        time.sleep(8)
        if process.poll() is not None:
            raise AssertionError(
                f'Packaged app exited with {process.returncode}:\n'
                + log_path.read_text(errors='replace')
            )
    finally:
        if process.poll() is None:
            if sys.platform == 'win32':
                # A onefile EXE has a bootloader parent and an application child.
                # Stop this process tree so the child cannot outlive the test.
                subprocess.run(
                    ['taskkill', '/PID', str(process.pid), '/T', '/F'],
                    check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    timeout=15,
                )
            else:
                process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
print(f'PASS: packaged app starts and stays running: {executable}')
print(f'Saved: {log_path}')
