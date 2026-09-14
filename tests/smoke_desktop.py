"""Cross-platform GUI smoke checks. Run from the repository root."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from PySide6.QtCore import Qt
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication
from work_buddy import WorkBuddy
app = QApplication([])
w = WorkBuddy()
w.chime.setMuted(True)
w.show()
app.processEvents()
assert not w.windowIcon().isNull()
assert w.compact_button.fluent_renderer.isValid()
assert w.mini.restore.fluent_renderer.isValid()
assert w.windowTitle() == 'stand up'
w.duration.setEditText('15')
w.apply()
assert w.remaining == 900
w.start_timer()
w.pause_timer()
assert not w.timer.isActive()
w.tick()
assert w.remaining == 899 and w.time.text() == '14:59'
Path('artifacts').mkdir(exist_ok=True)
w.grab().save('artifacts/main-window.png')
QTest.mouseClick(w.compact_button, Qt.MouseButton.LeftButton)
QTest.qWait(150)
assert w.compact_mode and (w.width(), w.height()) == (113, 66)
assert w.mini.clock.text() == '14:59'
QTest.mouseClick(w.mini.pin_button, Qt.MouseButton.LeftButton)
assert w.pinned
QTest.mouseClick(w.mini.pin_button, Qt.MouseButton.LeftButton)
assert not w.pinned
w.grab().save('artifacts/mini-window.png')
QTest.mouseClick(w.mini.restore, Qt.MouseButton.LeftButton)
QTest.qWait(150)
assert not w.compact_mode
w.reset()
assert w.remaining == 900
w.close()
print('PASS: desktop UI, icon resources, timer, mini mode, pin toggle and restore')
