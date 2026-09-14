from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from PySide6.QtWidgets import QApplication,QPushButton
from PySide6.QtMultimedia import QMediaPlayer,QSoundEffect
from PySide6.QtTest import QTest
from work_buddy import WorkBuddy
app=QApplication([]);w=WorkBuddy();w.show();w.audio.setMuted(True);w.chime.setMuted(True)
assert not any(b.text()=='应用时长' for b in w.findChildren(QPushButton))
w.duration.setEditText('25');w.start_timer();assert w.remaining==1500
w.tick();w.pause_timer();w.duration.setEditText('15');w.start_timer();assert w.remaining==1499
w.reset();assert w.remaining==900 and not w.timer.isActive()
w.add_tracks([str(Path('assets/gentle-chime.wav').resolve())]);w.play_music()
for _ in range(50):
 if w.player.playbackState()==QMediaPlayer.PlaybackState.PlayingState:break
 QTest.qWait(100)
assert w.player.playbackState()==QMediaPlayer.PlaybackState.PlayingState
w.remaining=1;w.start_timer();w.remaining=1;w.tick();QTest.qWait(100)
assert w.player.playbackState()==QMediaPlayer.PlaybackState.PausedState
assert w.alarm_active and w.chime.isPlaying()
w.pause_timer();assert not w.alarm_active
assert w.player.playbackState()==QMediaPlayer.PlaybackState.PausedState
w.close();print('PASS: no duplicate apply button; selected duration on start/reset; resume preserves time; alarm pauses music without auto-resume')

