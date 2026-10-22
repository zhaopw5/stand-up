"""Check compact/restore from normal and maximized windows without audio playback."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from PySide6.QtCore import Qt
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication
from work_buddy import WorkBuddy

app = QApplication([])
window = WorkBuddy()
window.show()
QTest.qWait(100)
normal_size = window.size()

for maximized in (False, True):
    if maximized:
        window.showMaximized()
        QTest.qWait(150)
        assert window.isMaximized()
    for _ in range(2):
        QTest.mouseClick(window.compact_button, Qt.MouseButton.LeftButton)
        QTest.qWait(150)
        assert window.compact_mode
        # Cocoa reports its native zoom flag, including for fixed-size windows.
        # Verify mini geometry on every OS; the flag itself is reliable elsewhere.
        if sys.platform != 'darwin':
            assert not window.isMaximized()
        assert (window.width(), window.height()) == (113, 66), (
            maximized, window.size(), window.windowState()
        )
        assert window.mini.isVisible()

        QTest.mouseClick(window.mini.restore, Qt.MouseButton.LeftButton)
        QTest.qWait(150)
        assert not window.compact_mode
        assert window.isMaximized() == maximized
        if not maximized:
            assert window.size() == normal_size

window.close()
print('PASS: compact/restore preserves normal size and maximized state')
