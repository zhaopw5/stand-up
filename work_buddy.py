"""stand up：独立的倒计时与本地音乐播放器。"""
import sys
from pathlib import Path
from PySide6.QtCore import QEvent, QPoint, QPointF, QRect, QRectF, QSize, Qt, QTimer, QUrl, Signal
from PySide6.QtSvg import QSvgRenderer
from PySide6.QtGui import QColor, QFont, QFontMetrics, QIcon, QPainter, QPainterPath, QPen, QPolygon
from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer, QSoundEffect
from PySide6.QtWidgets import (QApplication, QComboBox, QFileDialog, QFrame,
    QHBoxLayout, QLabel, QListWidget, QListWidgetItem, QPushButton, QStyle,
    QStyledItemDelegate, QVBoxLayout, QWidget, QMessageBox, QSlider)

NUMBER_FONT = 'Helvetica Neue' if sys.platform == 'darwin' else 'Segoe UI'


class ChoiceBox(QComboBox):
    def paintEvent(self, event):
        super().paintEvent(event)
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor('#858585'))
        x, y = self.width() - 15, self.height() // 2
        painter.drawPolygon(QPolygon([QPoint(x - 5, y - 3),
                                     QPoint(x + 5, y - 3), QPoint(x, y + 4)]))


class MiniButton(QPushButton):
    def __init__(self, kind, tooltip, callback, parent):
        super().__init__(parent)
        self.kind = kind
        self.setToolTip(tooltip)
        self.setAccessibleName(tooltip)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.clicked.connect(callback)
        self.fluent_renderer = None
        if kind in ('compact', 'restore'):
            name = 'arrow-minimize-20-regular.svg' if kind == 'compact' else 'arrow-maximize-20-regular.svg'
            source = Path(__file__).resolve().parent / 'assets' / 'fluent' / name
            color = b'#333333' if kind == 'compact' else b'#999999'
            self.fluent_renderer = QSvgRenderer(source.read_bytes().replace(b'#212121', color), self)

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.fillRect(self.rect(), QColor('#dddddd' if self.underMouse() else '#f3f3f3'))
        if self.fluent_renderer is not None:
            side = 20 if self.kind == 'compact' else 16
            self.fluent_renderer.render(painter, QRectF(
                (self.width() - side) / 2, (self.height() - side) / 2, side, side))
            return
        icon_color = QColor('#333333' if self.kind == 'compact' or self.isChecked() else '#999999')
        if self.kind == 'music':
            icon_color = QColor('#999999' if self.isChecked() else '#cccccc')
        painter.setPen(icon_color)
        painter.setBrush(Qt.BrushStyle.NoBrush)
        scale = 1.8 if self.kind == 'compact' else 1.4
        painter.translate((self.width() - 10 * scale) / 2, (self.height() - 10 * scale) / 2)
        painter.scale(scale, scale)
        if self.kind == 'music':
            painter.drawLine(QPointF(4, 7.5), QPointF(4, 1.5))
            painter.drawLine(QPointF(4, 1.5), QPointF(9, 0.5))
            painter.drawLine(QPointF(9, 0.5), QPointF(9, 6.5))
            painter.setBrush(icon_color)
            painter.drawEllipse(QPointF(2.5, 8), 1.5, 1)
            painter.drawEllipse(QPointF(7.5, 7), 1.5, 1)
        elif self.kind == 'play':
            painter.drawPolygon(QPolygon([QPoint(2, 1), QPoint(9, 5), QPoint(2, 9)]))
        elif self.kind == 'pause':
            painter.drawRect(2, 1, 2, 8)
            painter.drawRect(7, 1, 2, 8)
        elif self.kind == 'pin':
            painter.translate(5, 5)
            painter.rotate(45)
            painter.translate(-5, -5)
            pin = QPainterPath(QPointF(3, 0.8))
            pin.lineTo(7, 0.8)
            pin.lineTo(6.5, 4.2)
            pin.lineTo(8.2, 6.2)
            pin.lineTo(1.8, 6.2)
            pin.lineTo(3.5, 4.2)
            pin.closeSubpath()
            painter.drawPath(pin)
            painter.drawLine(QPointF(5, 6.2), QPointF(5, 9.4))
            if self.isChecked():
                painter.fillPath(pin, icon_color)
        elif self.kind == 'close':
            painter.drawLine(2, 2, 8, 8)
            painter.drawLine(8, 2, 2, 8)
        elif self.kind == 'reset':
            pen = QPen(icon_color, 1.1)
            pen.setCapStyle(Qt.PenCapStyle.RoundCap)
            pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
            painter.setPen(pen)
            painter.setBrush(Qt.BrushStyle.NoBrush)
            arc = QPainterPath(QPointF(1.2, 4.0))
            arc.cubicTo(-0.2, 8.0, 4.0, 11.0, 7.4, 8.4)
            arc.cubicTo(10.8, 5.7, 9.0, 1.6, 6.0, 1.3)
            arc.cubicTo(4.5, 1.0, 3.0, 1.5, 1.8, 2.1)
            painter.drawPath(arc)
            arrow = QPainterPath(QPointF(2.3, 0.3))
            arrow.lineTo(1.8, 2.1)
            arrow.lineTo(4.0, 2.5)
            painter.drawPath(arrow)


class MiniPanel(QWidget):
    """113×66 的小窗：恢复、时间、倒计时暂停/继续与重置。"""
    def __init__(self, buddy):
        super().__init__(buddy)
        self.setFixedSize(113, 66)
        self.clock = QLabel(buddy.time.text(), self)
        self.clock.setGeometry(0, 15, 113, 34)
        self.clock.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.clock.setStyleSheet(f'font-family: "{NUMBER_FONT}"; font-size: 33px; font-weight: 600; color: #292929;')
        self.clock.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
        self.restore = MiniButton('restore', '恢复窗口', buddy.compact, self)
        self.pin_button = MiniButton('pin', '置顶窗口', buddy.toggle_pin, self)
        self.pin_button.setCheckable(True)
        self.pause_button = MiniButton('pause', '暂停倒计时', buddy.toggle_timer, self)
        self.music_button = MiniButton('music', '播放背景音乐', buddy.toggle_music, self)
        self.music_button.setCheckable(True)
        self.reset_button = MiniButton('reset', '重置倒计时', buddy.reset, self)
        for button, x, y in ((self.restore, 5, 4), (self.pin_button, 86, 4),
                             (self.pause_button, 5, 49), (self.music_button, 45, 49),
                             (self.reset_button, 86, 49)):
            button.setGeometry(x, y, 22, 16)

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self.window().windowHandle().startSystemMove()
        super().mousePressEvent(event)

    def set_time(self, text):
        font = QFont(NUMBER_FONT)
        font.setWeight(QFont.Weight.DemiBold)
        pixels = 33
        font.setPixelSize(pixels)
        while QFontMetrics(font).horizontalAdvance(text) > self.clock.width() - 4 and pixels > 10:
            pixels -= 1
            font.setPixelSize(pixels)
        self.clock.setStyleSheet(f'font-family: "{NUMBER_FONT}"; font-size: {pixels}px; font-weight: 600; color: #292929;')
        self.clock.setText(text)


class TrackDelegate(QStyledItemDelegate):
    """绘制歌曲行，保留列表原生的选择与拖动排序能力。"""
    def sizeHint(self, option, index):
        return QSize(200, 48)

    def paint(self, painter, option, index):
        painter.save()
        selected = bool(option.state & QStyle.StateFlag.State_Selected)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        hovered = bool(option.state & QStyle.StateFlag.State_MouseOver)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor('#e8e8e8' if selected else '#f1f1f1' if hovered else '#ffffff'))
        painter.drawRoundedRect(option.rect.adjusted(0, 2, -1, -2), 10, 10)
        divider = option.rect.right() - 74
        painter.setPen(QColor('#888888'))
        painter.drawText(option.rect.adjusted(12, 0, -1, 0), Qt.AlignmentFlag.AlignVCenter,
                         f'{index.row() + 1:02d}')
        painter.setPen(QColor('#333333' if selected else '#444444'))
        text_rect = option.rect.adjusted(46, 0, -84, 0)
        title = index.data()
        title = option.fontMetrics.elidedText(title, Qt.TextElideMode.ElideRight, text_rect.width())
        painter.drawText(text_rect, Qt.AlignmentFlag.AlignVCenter, title)
        painter.setPen(QColor('#555555' if hovered else '#888888'))
        painter.drawText(QRect(divider, option.rect.top(), 74, option.rect.height()),
                         Qt.AlignmentFlag.AlignCenter, '删除')
        painter.restore()

    def editorEvent(self, event, model, option, index):
        if (event.type() == QEvent.Type.MouseButtonRelease
                and event.button() == Qt.MouseButton.LeftButton
                and event.position().x() >= option.rect.right() - 74):
            self.parent().delete_requested.emit(index.row())
            return True
        return False


class TrackList(QListWidget):
    delete_requested = Signal(int)

    def paintEvent(self, event):
        super().paintEvent(event)
        if not self.count():
            painter = QPainter(self.viewport())
            painter.setPen(QColor('#888888'))
            painter.drawText(self.viewport().rect(), Qt.AlignmentFlag.AlignCenter,
                             '暂无音乐，点击上方「添加音乐」')


class WorkBuddy(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle('stand up')
        icon_name = 'app-icon.png' if sys.platform == 'darwin' else 'app-icon.ico'
        self.setWindowIcon(QIcon(str(Path(__file__).resolve().parent / 'assets' / icon_name)))
        self.remaining = 1800
        self.countdown_started = False
        self.list_visible = True
        self.compact_mode = False
        self.pinned = False
        self.normal_size = QSize(480, 560)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.tick)
        self.audio = QAudioOutput(self)
        self.player = QMediaPlayer(self)
        self.player.setAudioOutput(self.audio)
        self.chime = QSoundEffect(self)
        self.chime.setSource(QUrl.fromLocalFile(str(
            Path(__file__).resolve().parent / 'assets' / 'gentle-chime.wav')))
        self.chime.setVolume(0.35)
        self.chime.setLoopCount(QSoundEffect.Loop.Infinite.value)
        self.alarm_active = False
        self.reminder_dialog = None
        self.player.mediaStatusChanged.connect(self.next_track)
        self.setObjectName('app')
        self.setStyleSheet('''
            QWidget { font-family: "Microsoft YaHei UI"; font-size: 14px;
                      color: #343e39; background: transparent; }
            QWidget#app, QWidget#shell { background: #f4f5f1; }
            QLabel#title { font-size: 22px; font-weight: 600; color: #243f34; }
            QFrame#timerPanel { background: #ffffff; border: none; border-radius: 20px; }
            QLabel#clock { font-family: "Segoe UI"; font-size: 46px;
                          font-weight: 600; color: #263e34; }
            QPushButton { border: 1px solid transparent; border-radius: 10px;
                          padding: 8px 12px; background: #e9ece6; }
            QPushButton:hover { background: #dde4dc; }
            QPushButton:pressed { background: #ccd8ce; }
            QPushButton:focus { border: 1px solid #7a9b88; }
            QPushButton#primary { background: #326c57; color: white; font-weight: 600; }
            QPushButton#primary:hover { background: #285944; }
            QPushButton#primary:pressed { background: #204936; }
            QPushButton#apply, QPushButton#add { background: #e7f0e9; color: #32654e; }
            QPushButton#apply:hover, QPushButton#add:hover { background: #d6e8dc; }
            QPushButton#quiet { background: transparent; color: #768177; }
            QPushButton#quiet:hover { background: #e5e9e1; color: #35483b; }
            QComboBox { border: 1px solid transparent; border-radius: 10px;
                        padding: 6px 24px 6px 12px; background: #f0f2ed; }
            QComboBox:hover { background: #e7ece4; }
            QComboBox:focus { border: 1px solid #7a9b88; }
            QComboBox#duration { font-family: "Segoe UI"; font-size: 23px; }
            QComboBox::drop-down { width: 24px; border: none; }
            QComboBox::down-arrow { image: none; }
            QComboBox QAbstractItemView { background: white; color: #343e39;
                selection-background-color: #e5f0eb; selection-color: #254f43;
                border: 1px solid #dce4da; padding: 5px; }
            QFrame#listHeader QLabel { font-size: 15px; font-weight: 600; }
            QListWidget { border: none; outline: none; background: transparent; }
            QComboBox#mode { font-size: 13px; background: #e9ece6; }
            QPushButton#compact { font-size: 12px; color: #748175;
                                  padding: 4px; border-radius: 8px; }
            QScrollBar:vertical { background: transparent; width: 6px; margin: 0; }
            QScrollBar::handle:vertical { background: #c8d2c7; min-height: 24px; border-radius: 3px; }
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }
            QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical { background: transparent; }
            QToolTip { color: #343e39; background: #ffffff; border: 1px solid #dce4da; padding: 5px; }
        ''')
        # 统一灰白色板；保留原有主次对比，不改变控件结构。
        palette = {
            '#343e39': '#3b3b3b', '#f4f5f1': '#f3f3f3', '#243f34': '#292929',
            '#263e34': '#292929', '#e9ece6': '#e9e9e9', '#dde4dc': '#dedede',
            '#ccd8ce': '#cecece', '#7a9b88': '#999999', '#326c57': '#454545',
            '#285944': '#333333', '#204936': '#222222', '#e7f0e9': '#eeeeee',
            '#32654e': '#444444', '#d6e8dc': '#dddddd', '#768177': '#737373',
            '#e5e9e1': '#e5e5e5', '#35483b': '#444444', '#f0f2ed': '#f0f0f0',
            '#e7ece4': '#e7e7e7', '#e5f0eb': '#e8e8e8', '#254f43': '#333333',
            '#dce4da': '#dedede', '#748175': '#737373', '#c8d2c7': '#cccccc',
        }
        self.normal_style = self.styleSheet()
        for original, gray in palette.items():
            self.normal_style = self.normal_style.replace(original, gray)
        if sys.platform == 'darwin':
            self.normal_style = self.normal_style.replace('Microsoft YaHei UI', 'PingFang SC').replace('Segoe UI', NUMBER_FONT)
        self.setStyleSheet(self.normal_style)
        self.build_ui()
        self.mini = MiniPanel(self)
        self.player.playbackStateChanged.connect(self.sync_music_button)
        self.mini.hide()
        self.add_tracks([str(Path(__file__).resolve().parent / 'assets' / 'quiet-steps-loop.wav')])
        self.list.item(0).setText('慢步 · 内置轻音乐')
        self.mode.setCurrentText('列表循环')
        self.setMinimumSize(460, 540)
        self.resize(self.normal_size)

    @staticmethod
    def button(text, callback, name=''):
        button = QPushButton(text)
        button.setObjectName(name)
        button.setCursor(Qt.CursorShape.PointingHandCursor)
        button.clicked.connect(callback)
        return button

    def build_ui(self):
        outer = QVBoxLayout(self)
        self.outer_layout = outer
        outer.setContentsMargins(10, 10, 10, 10)
        shell = QWidget(objectName='shell')
        self.shell = shell
        outer.addWidget(shell)
        layout = QVBoxLayout(shell)
        self.shell_layout = layout
        layout.setContentsMargins(12, 8, 12, 10)
        layout.setSpacing(14)
        self.title_bar = QWidget()
        title_layout = QHBoxLayout(self.title_bar)
        title_layout.setContentsMargins(0, 0, 0, 0)
        title_layout.addSpacing(56)
        title = QLabel('stand up', objectName='title')
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        title_layout.addWidget(title, 1)
        self.compact_button = MiniButton('compact', '切换到小窗', self.compact, self.title_bar)
        self.compact_button.setFixedWidth(56)
        self.compact_button.setFixedHeight(30)
        title_layout.addWidget(self.compact_button)
        layout.addWidget(self.title_bar)
        panel = QFrame(objectName='timerPanel')
        panel_layout = QVBoxLayout(panel)
        self.panel_layout = panel_layout
        panel_layout.setContentsMargins(18, 18, 18, 12)
        panel_layout.setSpacing(10)
        top = QHBoxLayout()
        top.setSpacing(12)
        self.time = QLabel('30:00', objectName='clock')
        self.time.setAlignment(Qt.AlignmentFlag.AlignCenter)
        top.addWidget(self.time, 1)
        self.duration = ChoiceBox(objectName='duration')
        self.duration.addItems(['15', '30', '45', '60'])
        self.duration.setCurrentText('30')
        self.duration.setEditable(True)
        self.duration.setInsertPolicy(QComboBox.InsertPolicy.NoInsert)
        self.duration.lineEdit().setMaxLength(3)
        self.duration.lineEdit().setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.duration.lineEdit().setPlaceholderText('分钟')
        self.duration.setFixedSize(108, 50)
        self.duration.setStyleSheet('QComboBox { padding: 6px 4px; } QLineEdit { padding: 0px; border: none; background: transparent; }')
        self.duration.setToolTip('输入 1～999 分钟。开始新一轮或重置时使用；暂停后继续会保留剩余时间。')
        self.duration.setAccessibleName('倒计时时长，单位分钟，可直接输入')
        top.addWidget(self.duration)
        top.addWidget(QLabel('分钟'))
        panel_layout.addLayout(top)
        self.timer_controls = QWidget()
        controls = QHBoxLayout(self.timer_controls)
        controls.setContentsMargins(0, 0, 0, 0)
        controls.setSpacing(10)
        self.start_button = self.button('开始', self.start_timer, 'primary')
        controls.addWidget(self.start_button)
        controls.addWidget(self.button('暂停', self.pause_timer))
        controls.addWidget(self.button('重置', self.reset, 'quiet'))
        panel_layout.addWidget(self.timer_controls)
        layout.addWidget(panel)
        reminder_row = QHBoxLayout()
        reminder_row.addWidget(QLabel('到时提醒'))
        self.reminder_mode = ChoiceBox()
        self.reminder_mode.addItems(['提示音', '桌面弹窗'])
        self.reminder_mode.setAccessibleName('到时提醒方式')
        self.reminder_mode.setToolTip('提示音循环至暂停；桌面弹窗保持显示至确认。两种方式都会暂停背景音乐。')
        reminder_row.addWidget(self.reminder_mode, 1)
        layout.addLayout(reminder_row)
        self.playlist_section = QWidget()
        playlist_layout = QVBoxLayout(self.playlist_section)
        playlist_layout.setContentsMargins(0, 0, 0, 0)
        playlist_layout.setSpacing(6)
        header = QFrame(objectName='listHeader')
        header_layout = QHBoxLayout(header)
        header_layout.setContentsMargins(2, 0, 0, 0)
        header_layout.setSpacing(6)
        music_heading = QLabel('<span style="font-size:16px;color:#333333;font-weight:700">背景音乐</span>'
                               '<span style="font-size:12px;color:#888888"> · 播放列表</span>')
        header_layout.addWidget(music_heading, 1)
        self.toggle_list_btn = self.button('折叠列表', self.toggle_list, 'quiet')
        header_layout.addWidget(self.toggle_list_btn)
        header_layout.addWidget(self.button('+ 添加音乐', self.choose, 'add'))
        playlist_layout.addWidget(header)
        self.list = TrackList()
        self.list.setMouseTracking(True)
        self.list.setItemDelegate(TrackDelegate(self.list))
        self.list.setDragDropMode(QListWidget.DragDropMode.InternalMove)
        self.list.setDefaultDropAction(Qt.DropAction.MoveAction)
        self.list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.list.setMinimumHeight(80)
        self.list.delete_requested.connect(self.remove)
        self.list.itemDoubleClicked.connect(lambda item: self.play_music())
        playlist_layout.addWidget(self.list, 1)
        layout.addWidget(self.playlist_section, 1)
        self.music_status = QLabel('尚未播放背景音乐')
        self.music_status.setStyleSheet('font-size:12px;color:#777777;')
        self.music_status.setWordWrap(True)
        layout.addWidget(self.music_status)
        progress = QHBoxLayout()
        self.elapsed = QLabel('00:00')
        self.total = QLabel('00:00')
        self.seek = QSlider(Qt.Orientation.Horizontal)
        self.seek.setRange(0, 0)
        self.seek.setEnabled(False)
        self.seek.setAccessibleName('背景音乐播放进度')
        self.seek.setStyleSheet('QSlider::groove:horizontal {height:4px;background:#dddddd;border-radius:2px;} QSlider::sub-page:horizontal {background:#888888;border-radius:2px;} QSlider::handle:horizontal {width:12px;margin:-4px 0;background:#666666;border-radius:6px;}')
        self.seek.sliderMoved.connect(lambda value: self.elapsed.setText(self.format_music_time(value)))
        self.seek.sliderReleased.connect(lambda: self.player.setPosition(self.seek.value()))
        self.seek.actionTriggered.connect(lambda action: self.player.setPosition(self.seek.sliderPosition()) if not self.seek.isSliderDown() else None)
        self.player.positionChanged.connect(self.update_music_position)
        self.player.durationChanged.connect(self.update_music_duration)
        self.player.seekableChanged.connect(lambda value: self.seek.setEnabled(value and self.player.duration() > 0))
        self.player.errorOccurred.connect(self.music_error)
        progress.addWidget(self.elapsed)
        progress.addWidget(self.seek, 1)
        progress.addWidget(self.total)
        layout.addLayout(progress)
        bottom = QHBoxLayout()
        self.bottom_layout = bottom
        bottom.setSpacing(8)
        self.play = self.button('播放', self.toggle_selected_music, 'primary')
        bottom.addWidget(self.play)
        bottom.addStretch()
        self.mode = ChoiceBox(objectName='mode')
        self.mode.addItems(['顺序播放', '单曲循环', '列表循环'])
        self.mode.setToolTip('播放模式')
        self.mode.setAccessibleName('播放模式')
        bottom.addWidget(self.mode)
        self.restore_button = self.button('恢复窗口', self.compact)
        self.restore_button.hide()
        bottom.addWidget(self.restore_button)
        layout.addLayout(bottom)

    def choose(self):
        paths, _ = QFileDialog.getOpenFileNames(
            self, '添加背景音乐', '', '音乐文件 (*.mp3 *.wav *.ogg *.m4a)')
        first = self.list.count()
        self.add_tracks(paths)
        if paths:
            self.list.setCurrentRow(first)

    def add_tracks(self, paths):
        # 完整路径跟随列表项移动，避免同名歌曲排序后指向错误文件。
        for path in paths:
            item = QListWidgetItem(Path(path).name)
            item.setData(Qt.ItemDataRole.UserRole, path)
            item.setToolTip(path + '\n双击播放 · 拖动排序')
            self.list.addItem(item)
        if self.list.count() and self.list.currentRow() < 0:
            self.list.setCurrentRow(0)

    def sync_music_button(self, *args):
        playing = self.player.playbackState() == QMediaPlayer.PlaybackState.PlayingState
        button = self.mini.music_button
        button.setChecked(playing)
        label = '暂停背景音乐' if playing else '播放 / 继续背景音乐'
        button.setToolTip(label)
        button.setAccessibleName(label)
        button.update()
        self.play.setText('暂停' if playing else '播放')

    @staticmethod
    def format_music_time(milliseconds):
        seconds = max(0, milliseconds // 1000)
        return f'{seconds // 60:02d}:{seconds % 60:02d}'

    def update_music_position(self, position):
        if not self.seek.isSliderDown():
            self.seek.setValue(position)
            self.elapsed.setText(self.format_music_time(position))

    def update_music_duration(self, duration):
        self.seek.setRange(0, max(0, duration))
        self.total.setText(self.format_music_time(duration))
        self.seek.setEnabled(duration > 0 and self.player.isSeekable())

    def music_error(self, *args):
        self.music_status.setText('播放失败：' + (self.player.errorString() or '无法读取此音频文件'))
        self.music_status.setToolTip(self.player.source().toLocalFile())
        self.sync_music_button()

    def toggle_selected_music(self):
        item = self.list.currentItem()
        selected = QUrl.fromLocalFile(item.data(Qt.ItemDataRole.UserRole)) if item else QUrl()
        if not selected.isEmpty() and selected != self.player.source():
            self.play_music()
        else:
            self.toggle_music()

    def toggle_music(self):
        if self.player.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
            self.player.pause()
        elif not self.player.source().isEmpty():
            self.player.play()
        else:
            self.play_music()
        self.sync_music_button()

    def play_music(self):
        if not self.list.count():
            self.choose()
        item = self.list.currentItem()
        if item is None:
            return
        source = QUrl.fromLocalFile(item.data(Qt.ItemDataRole.UserRole))
        if not Path(source.toLocalFile()).is_file():
            self.music_status.setText('播放失败：文件不存在，请重新添加歌曲。')
            return
        self.music_status.setText('当前音乐：' + item.text())
        self.music_status.setToolTip(source.toLocalFile())
        if source != self.player.source():
            self.player.setSource(source)
        self.player.play()

    def remove(self, row):
        item = self.list.item(row)
        if item is None:
            return
        source = QUrl.fromLocalFile(item.data(Qt.ItemDataRole.UserRole))
        if source == self.player.source():
            self.player.stop()
            self.player.setSource(QUrl())
        self.list.takeItem(row)
        self.list.viewport().update()

    def next_track(self, status):
        if self.alarm_active:
            return
        if status != QMediaPlayer.MediaStatus.EndOfMedia or not self.list.count():
            return
        if self.mode.currentText() == '单曲循环':
            self.player.setPosition(0)
            self.player.play()
            return
        row = next((i for i in range(self.list.count())
                    if QUrl.fromLocalFile(self.list.item(i).data(Qt.ItemDataRole.UserRole))
                    == self.player.source()), -1)
        following = row + 1
        if following >= self.list.count():
            if self.mode.currentText() == '顺序播放':
                self.player.stop()
                return
            following = 0
        self.list.setCurrentRow(following)
        self.play_music()

    def toggle_list(self):
        self.list_visible = not self.list_visible
        self.list.setVisible(self.list_visible)
        self.toggle_list_btn.setText('折叠列表' if self.list_visible else '展开列表')
        self.setMinimumHeight(540 if self.list_visible else 390)
        self.resize(self.width(), 560 if self.list_visible else 390)

    def start_timer(self):
        if not self.countdown_started or self.remaining == 0:
            if not self.reset():
                return
        if self.remaining > 0:
            self.countdown_started = True
            self.timer.start(1000)
        self.sync_timer_button()

    def pause_timer(self):
        self.timer.stop()
        self.stop_alarm()
        self.sync_timer_button()

    def toggle_timer(self):
        if self.timer.isActive() or self.alarm_active:
            self.pause_timer()
        else:
            self.start_timer()

    def sync_timer_button(self):
        button = self.mini.pause_button
        running = self.timer.isActive() or self.alarm_active
        button.kind = 'pause' if running else 'play'
        label = '停止提醒' if self.alarm_active else '暂停倒计时' if running else '开始 / 继续倒计时'
        button.setToolTip(label)
        button.setAccessibleName(label)
        button.update()
        self.start_button.setEnabled(not self.timer.isActive())
        self.start_button.setText('计时中' if self.timer.isActive() else
                                  '继续' if self.countdown_started and self.remaining > 0 else '开始')

    def apply(self):
        if not self.timer.isActive():
            value = self.duration.currentText().strip()
            if not value.isascii() or not value.isdecimal() or not 1 <= int(value) <= 999:
                QMessageBox.information(self, '请输入有效时长', '请输入 1～999 的整数分钟，例如 5、100 或 999。')
                self.duration.setFocus()
                return False
            self.remaining = int(value) * 60
            self.countdown_started = False
            self.stop_alarm()
            self.sync_timer_button()
            self.update_time()
            self.setWindowTitle('stand up')
            return True
        return False

    def update_time(self):
        self.time.setText(f'{self.remaining // 60:02d}:{self.remaining % 60:02d}')
        self.mini.set_time(self.time.text())

    def tick(self):
        was_running_down = self.remaining > 0
        self.remaining = max(0, self.remaining - 1)
        self.update_time()
        if self.remaining == 0:
            self.timer.stop()
            self.sync_timer_button()
            self.setWindowTitle('stand up - 时间到了，请起身活动')
            if was_running_down:
                # 先暂停背景音乐并保留进度，再播放提醒，避免两路声音混在一起。
                self.player.pause()
                self.alarm_active = True
                if self.reminder_mode.currentIndex() == 0:
                    self.chime.play()
                else:
                    self.show_reminder()
                self.sync_timer_button()

    def show_reminder(self):
        if self.reminder_dialog is None:
            dialog = QMessageBox(self)
            dialog.setWindowTitle('stand up · 休息一下')
            dialog.setText('时间到了，站起来活动一下')
            dialog.setInformativeText('离开屏幕，伸个懒腰，走一走。')
            dialog.addButton('知道了', QMessageBox.ButtonRole.AcceptRole)
            dialog.setWindowFlags(Qt.WindowType.Dialog | Qt.WindowType.WindowStaysOnTopHint
                                  | Qt.WindowType.CustomizeWindowHint | Qt.WindowType.WindowTitleHint
                                  | Qt.WindowType.WindowCloseButtonHint)
            dialog.setWindowModality(Qt.WindowModality.NonModal)
            dialog.setStyleSheet('QMessageBox {background:#f3f3f3;} QLabel {color:#333333;font-size:18px;min-width:280px;} QPushButton {padding:8px 24px;background:#e3e3e3;border:0;border-radius:7px;font-size:15px;}')
            dialog.finished.connect(self.dismiss_reminder)
            self.reminder_dialog = dialog
        self.reminder_dialog.show()
        self.center_reminder()
        QTimer.singleShot(100, self.center_reminder)
        self.reminder_dialog.raise_()
        self.reminder_dialog.activateWindow()

    def center_reminder(self):
        dialog = self.reminder_dialog
        if dialog is not None and dialog.isVisible():
            area = self.screen().availableGeometry()
            frame = dialog.frameGeometry()
            target = area.center() - QPoint(frame.width() // 2, frame.height() // 2)
            dialog.move(dialog.pos() + target - frame.topLeft())

    def dismiss_reminder(self, *args):
        self.alarm_active = False
        self.chime.stop()
        self.sync_timer_button()

    def stop_alarm(self):
        self.alarm_active = False
        self.chime.stop()
        if self.reminder_dialog is not None and self.reminder_dialog.isVisible():
            self.reminder_dialog.close()

    def reset(self):
        self.stop_alarm()
        self.timer.stop()
        applied = self.apply()
        self.sync_timer_button()
        return applied

    def compact(self):
        position = self.pos()
        # 用切换前的小窗位置选屏，避免扩大后中心跨屏而选错显示器。
        target_screen = QApplication.screenAt(self.frameGeometry().center()) or self.screen()
        if not self.compact_mode:
            self.normal_size = self.size()
        self.compact_mode = not self.compact_mode
        if self.compact_mode:
            self.shell.hide()
            self.outer_layout.setContentsMargins(0, 0, 0, 0)
            self.setWindowFlag(Qt.WindowType.FramelessWindowHint, True)
            self.setWindowFlag(Qt.WindowType.WindowStaysOnTopHint, self.pinned)
            self.setFixedSize(113, 66)
            self.mini.set_time(self.time.text())
            self.sync_timer_button()
            self.mini.show()
            self.mini.move(0, 0)
        else:
            self.mini.hide()
            self.setWindowFlag(Qt.WindowType.FramelessWindowHint, False)
            self.setWindowFlag(Qt.WindowType.WindowStaysOnTopHint, False)
            self.setMaximumSize(16777215, 16777215)
            self.setMinimumSize(460, 440 if self.list_visible else 330)
            self.outer_layout.setContentsMargins(10, 10, 10, 10)
            self.shell.show()
            self.resize(self.normal_size)
        self.move(position)
        self.show()
        self.keep_inside_screen(target_screen.availableGeometry())
        # Windows 重建标题栏后外框尺寸才稳定，稍后按实际所在屏幕再校正。
        QTimer.singleShot(100, self.correct_screen_bounds)

    def toggle_pin(self):
        self.pinned = not self.pinned
        self.mini.pin_button.setChecked(self.pinned)
        label = '取消置顶' if self.pinned else '置顶窗口'
        self.mini.pin_button.setToolTip(label)
        self.mini.pin_button.setAccessibleName(label)
        if self.compact_mode:
            position = self.pos()
            self.setWindowFlag(Qt.WindowType.WindowStaysOnTopHint, self.pinned)
            self.move(position)
            self.show()
        self.mini.pin_button.update()

    def correct_screen_bounds(self):
        if self.isVisible():
            self.keep_inside_screen(self.screen().availableGeometry())

    @staticmethod
    def bounded_position(frame, area):
        """将窗口外框夹在当前屏幕可用区域内，支持负坐标副屏。"""
        rightmost = max(area.left(), area.right() + 1 - frame.width())
        bottommost = max(area.top(), area.bottom() + 1 - frame.height())
        return QPoint(min(max(frame.x(), area.left()), rightmost),
                      min(max(frame.y(), area.top()), bottommost))

    def keep_inside_screen(self, area):
        frame = self.frameGeometry()
        target = self.bounded_position(frame, area)
        # 使用外框差值移动，避免把内容区坐标与外框坐标混用。
        self.move(self.pos() + target - frame.topLeft())

    def closeEvent(self, event):
        self.stop_alarm()
        self.timer.stop()
        self.player.stop()
        event.accept()


if __name__ == '__main__':
    if sys.platform == 'win32':
        import ctypes
        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID('StandUpAndWalk.Desktop')
    app = QApplication(sys.argv)
    window = WorkBuddy()
    window.show()
    sys.exit(app.exec())



