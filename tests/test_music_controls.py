import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from PySide6.QtWidgets import QApplication,QFileDialog
from PySide6.QtMultimedia import QMediaPlayer
from PySide6.QtTest import QTest
from unittest.mock import patch
from work_buddy import WorkBuddy
app=QApplication([]);w=WorkBuddy();w.show();w.audio.setMuted(True);w.chime.setMuted(True)
custom=str(Path('assets/gentle-chime.wav').resolve())
with patch.object(QFileDialog,'getOpenFileNames',return_value=([custom],'')):w.choose()
assert w.list.currentRow()==1
w.toggle_selected_music()
for _ in range(80):
 QTest.qWait(100)
 if w.player.duration()>0 and w.player.isSeekable():break
assert Path(w.player.source().toLocalFile())==Path(custom) and w.total.text()=='00:04'
assert w.seek.isEnabled()
w.toggle_selected_music();assert w.player.playbackState()==QMediaPlayer.PlaybackState.PausedState
w.seek.setSliderDown(True);w.seek.setValue(2000);w.seek.setSliderDown(False);QTest.qWait(100)
assert abs(w.player.position()-2000)<200
w.toggle_selected_music();assert w.play.text()=='暂停'
w.list.setCurrentRow(0);w.toggle_selected_music();QTest.qWait(200)
assert 'quiet-steps' in w.player.source().toLocalFile()
w.player.pause();app.processEvents();w.grab().save('artifacts/music-progress.png')
w.close();print('PASS: added-track selection, combined button, duration and seek while paused, switching tracks')
