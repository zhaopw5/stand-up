"""Device-change regression tests; no Bluetooth hardware required in CI."""
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from PySide6.QtWidgets import QApplication
from PySide6.QtMultimedia import QMediaDevices, QSoundEffect
from work_buddy import WorkBuddy

app = QApplication.instance() or QApplication([])


class AudioDevicesTest(unittest.TestCase):
    def setUp(self):
        self.monitor = QMediaDevices()
        self.provider = patch('work_buddy.QMediaDevices', create=True)
        self.devices = self.provider.start()
        self.devices.return_value = self.monitor
        self.devices.defaultAudioOutput.return_value = QMediaDevices.defaultAudioOutput()
        self.window = WorkBuddy()
        self.window.audio.setMuted(True)
        self.window.chime.setMuted(True)
        self.addCleanup(self.provider.stop)
        self.addCleanup(self.window.close)

    def change(self, current, target):
        self.devices.defaultAudioOutput.return_value = target
        with patch.object(self.window.audio, 'device', return_value=current), \
             patch.object(self.window.chime, 'audioDevice', return_value=current), \
             patch.object(self.window.audio, 'setDevice') as music, \
             patch.object(self.window.chime, 'setAudioDevice') as alarm, \
             patch.object(self.window.player, 'play') as play, \
             patch.object(self.window.player, 'pause') as pause, \
             patch.object(self.window.player, 'setPosition') as seek:
            self.monitor.audioOutputsChanged.emit()
            play.assert_not_called()
            pause.assert_not_called()
            seek.assert_not_called()
            return music, alarm

    def test_connect_and_disconnect_follow_default_for_both_outputs(self):
        speakers = Mock(); speakers.isNull.return_value = False
        headphones = Mock(); headphones.isNull.return_value = False
        for current, target in [(speakers, headphones), (headphones, speakers)]:
            music, alarm = self.change(current, target)
            music.assert_called_once_with(target)
            alarm.assert_called_once_with(target)

    def test_unrelated_device_notification_does_not_reopen_outputs(self):
        device = Mock(); device.isNull.return_value = False
        music, alarm = self.change(device, device)
        music.assert_not_called(); alarm.assert_not_called()

    def test_temporarily_missing_default_does_not_replace_outputs(self):
        missing = Mock(); missing.isNull.return_value = True
        music, alarm = self.change(Mock(), missing)
        music.assert_not_called(); alarm.assert_not_called()

    def test_active_chime_stops_before_switch_and_restarts_once(self):
        device = Mock(); device.isNull.return_value = False
        self.devices.defaultAudioOutput.return_value = device
        calls = Mock()
        with patch.object(self.window.audio, 'device', return_value=device), \
             patch.object(self.window.chime, 'audioDevice', return_value=Mock()), \
             patch.object(self.window.chime, 'isPlaying', return_value=True), \
             patch.object(self.window.chime, 'status', return_value=QSoundEffect.Status.Ready), \
             patch.object(self.window.chime, 'stop', calls.stop), \
             patch.object(self.window.chime, 'setAudioDevice', calls.switch), \
             patch.object(self.window.chime, 'play', calls.play):
            self.monitor.audioOutputsChanged.emit()
        from unittest.mock import call
        self.assertEqual(calls.mock_calls, [call.stop(), call.switch(device), call.play()])

    def test_failed_chime_reloads_when_device_becomes_available(self):
        device = Mock(); device.isNull.return_value = False
        self.devices.defaultAudioOutput.return_value = device
        source = self.window.chime.source()
        with patch.object(self.window.audio, 'device', return_value=device), \
             patch.object(self.window.chime, 'audioDevice', return_value=device), \
             patch.object(self.window.chime, 'status', return_value=QSoundEffect.Status.Error), \
             patch.object(self.window.chime, 'setAudioDevice'), \
             patch.object(self.window.chime, 'setSource') as reload:
            self.monitor.audioOutputsChanged.emit()
        self.assertEqual(reload.call_count, 2)
        self.assertTrue(reload.call_args_list[0].args[0].isEmpty())
        self.assertEqual(reload.call_args_list[1].args[0], source)

    def test_real_output_switch_keeps_paused_position(self):
        devices = QMediaDevices.audioOutputs()
        if len(devices) < 2:
            self.skipTest('Real switching requires two audio outputs')
        from PySide6.QtTest import QTest
        from PySide6.QtMultimedia import QMediaPlayer
        w = self.window
        w.audio.setDevice(devices[0]); w.chime.setAudioDevice(devices[0])
        w.play_music()
        for _ in range(80):
            QTest.qWait(100)
            if w.player.isSeekable():
                break
        self.assertTrue(w.player.isSeekable())
        w.player.pause(); w.player.setPosition(5000)
        QTest.qWait(100)
        position = w.player.position()
        source = w.player.source()
        self.devices.defaultAudioOutput.return_value = devices[1]
        self.monitor.audioOutputsChanged.emit()
        QTest.qWait(200)
        self.assertEqual(w.audio.device(), devices[1])
        self.assertEqual(w.chime.audioDevice(), devices[1])
        self.assertEqual(w.player.playbackState(), QMediaPlayer.PlaybackState.PausedState)
        self.assertEqual(w.player.source(), source)
        self.assertLess(abs(w.player.position() - position), 200)
        w.player.play()
        QTest.qWait(200)
        position = w.player.position()
        self.devices.defaultAudioOutput.return_value = devices[0]
        self.monitor.audioOutputsChanged.emit()
        QTest.qWait(200)
        self.assertEqual(w.player.playbackState(), QMediaPlayer.PlaybackState.PlayingState)
        self.assertGreaterEqual(w.player.position(), position)
        w.remaining = 1
        w.tick()
        QTest.qWait(200)
        self.assertTrue(w.chime.isPlaying())
        self.devices.defaultAudioOutput.return_value = devices[1]
        self.monitor.audioOutputsChanged.emit()
        QTest.qWait(200)
        self.assertTrue(w.alarm_active)
        self.assertTrue(w.chime.isPlaying())
        self.assertEqual(w.player.playbackState(), QMediaPlayer.PlaybackState.PausedState)


if __name__ == '__main__':
    unittest.main()
