import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from PySide6.QtWidgets import QApplication
from PySide6.QtCore import Qt
from PySide6.QtTest import QTest
from work_buddy import WorkBuddy
app=QApplication([]);w=WorkBuddy();w.show();w.chime.setMuted(True)
assert w.reminder_mode.currentText()=='提示音'
w.reminder_mode.setCurrentIndex(1)
for compact in (False,True):
 if compact:w.compact()
 w.remaining=1;w.tick();QTest.qWait(200)
 dialog=w.reminder_dialog
 assert dialog.isVisible() and not w.chime.isPlaying() and w.alarm_active
 assert (dialog.frameGeometry().center()-w.screen().availableGeometry().center()).manhattanLength()<6
 w.tick();assert w.reminder_dialog is dialog
 dialog.grab().save('artifacts/reminder-dialog.png')
 QTest.mouseClick(dialog.buttons()[0],Qt.MouseButton.LeftButton)
 assert not dialog.isVisible() and not w.alarm_active
w.remaining=1;w.tick();w.reset();assert not w.reminder_dialog.isVisible()
w.reminder_mode.setCurrentIndex(0);w.remaining=1;w.tick()
assert w.alarm_active and not w.reminder_dialog.isVisible()
w.pause_timer();assert not w.alarm_active
w.close();print('PASS: default sound, silent centered popup in both modes, acknowledge/reset, no duplicate popup')
