"""Check that a packaged executable starts and remains running for eight seconds."""
import os
from pathlib import Path
import subprocess
import sys
import time

executable = Path(sys.argv[1]).resolve()
assert executable.is_file(), executable
environment = os.environ.copy()
environment['QT_QPA_PLATFORM'] = 'offscreen'
process = subprocess.Popen(
    [str(executable)], cwd=executable.parent, env=environment,
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
)
try:
    time.sleep(8)
    if process.poll() is not None:
        output = process.communicate()[0].decode(errors='replace')
        raise AssertionError(f'Packaged app exited with {process.returncode}:\n{output}')
finally:
    if process.poll() is None:
        process.terminate()
        try:
            process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.communicate()
print(f'PASS: packaged app starts and stays running: {executable}')
