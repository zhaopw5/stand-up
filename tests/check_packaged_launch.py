"""Require positive evidence that the packaged Qt UI initialized successfully."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

executable = Path(sys.argv[1]).resolve()
assert executable.is_file(), executable
environment = os.environ.copy()
environment['QT_QPA_PLATFORM'] = 'offscreen'
log_path = Path('artifacts/packaged-launch.log').resolve()
log_path.parent.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix='stand-up-startup-') as folder:
    report = Path(folder) / 'ready.json'
    command = ([sys.executable, str(executable)] if executable.suffix == '.py'
               else [str(executable)]) + ['--startup-check', str(report)]
    with log_path.open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=executable.parent, env=environment,
                                   stdout=log, stderr=subprocess.STDOUT)
        try:
            process.wait(timeout=30)
            assert process.returncode == 0, log_path.read_text(errors='replace')
            assert report.is_file(), 'Application never confirmed successful UI initialization'
            result = json.loads(report.read_text())
            assert result['window_title'] == 'stand up' and result['visible'], result
            assert result['timer_text'] == '30:00', result
        finally:
            if process.poll() is None:
                if sys.platform == 'win32':
                    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                   check=True, capture_output=True, timeout=15)
                else:
                    process.kill()
                process.wait(timeout=10)
print(f'PASS: Qt initialized, main window created, clean exit: {executable}')
print(f'Saved: {log_path}')
