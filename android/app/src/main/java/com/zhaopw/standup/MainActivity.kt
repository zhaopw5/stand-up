package com.zhaopw.standup

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : Activity() {
    private lateinit var repository: TimerRepository
    private lateinit var library: MusicLibrary
    private lateinit var countdown: TextView
    private lateinit var phase: TextView
    private lateinit var minutes: EditText
    private lateinit var start: Button
    private lateinit var pause: Button
    private lateinit var permissionInfo: TextView
    private lateinit var musicInfo: TextView
    private lateinit var trackList: LinearLayout
    private lateinit var play: Button
    private lateinit var seek: SeekBar
    private lateinit var progress: TextView
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dragging = false
    private var visible = false
    private val tick = object : Runnable {
        override fun run() { if (visible) { update(); handler.postDelayed(this, 500) } }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply {
        orientation = if (resources.configuration.fontScale > 1.3f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun label(text: String, size: Float = 16f) = TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.rgb(55,55,55)); setPadding(0,dp(6),0,dp(6)) }
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; minHeight = dp(48); minimumWidth = dp(48); setOnClickListener { action() }
    }
    private fun LinearLayout.equal(view: View) {
        addView(view, if (orientation == LinearLayout.VERTICAL) LinearLayout.LayoutParams(-1,-2)
        else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
    private fun card() = column().apply {
        setPadding(dp(18),dp(16),dp(18),dp(16))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.rgb(230,230,230)) }
        layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(18) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TimerRepository(this)
        library = MusicLibrary(this)
        Notifications(this).createChannels()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.rgb(248,248,248)) }
        val content = column().apply { setPadding(dp(20),dp(20),dp(20),dp(24)) }
        scroll.addView(content)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(safe.left,safe.top,safe.right,safe.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(scroll)
        content.addView(label("stand up", 30f).apply { setTypeface(null,Typeface.BOLD) })
        content.addView(label("留一点时间，起来走走。", 16f))
        content.addView(card().apply {
            phase = label("准备开始").apply { gravity = Gravity.CENTER }; addView(phase)
            countdown = label("30:00", 56f).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; contentDescription = "剩余时间" }; addView(countdown)
            addView(label("每隔多少分钟提醒你？"))
            minutes = EditText(this@MainActivity).apply {
                id = View.generateViewId(); setText((repository.read().duration / 60000).toString())
                inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(); hint = "1–999 分钟"; minHeight = dp(48)
                contentDescription = "提醒间隔，1到999整数分钟"
            }; addView(minutes)
            addView(row().apply { listOf(15,30,45,60).forEach { value -> equal(button("$value") { minutes.setText(value.toString()) }) } })
            addView(row().apply {
                start = button("开始") {
                    val state = repository.read()
                    val duration = if (state.phase == Phase.PAUSED) (state.duration / 60000).toInt() else validMinutes() ?: return@button
                    repository.start(duration); update()
                }; equal(start)
                pause = button("暂停") { repository.pause(); update() }; equal(pause)
                equal(button("重置") { validMinutes()?.let { repository.reset(it); update() } })
            })
            addView(label("暂停后继续会保留剩余时间；重置会采用上面填写的新时长。", 13f))
        })
        content.addView(card().apply {
            addView(label("通知与后台提醒", 20f))
            permissionInfo = label(""); addView(permissionInfo)
            addView(button("允许通知 / 打开通知设置") {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
                else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            })
            if (Build.VERSION.SDK_INT >= 31) addView(button("允许准确提醒") {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
                    .onFailure { message("无法打开准确提醒设置，请在系统设置中搜索本应用的闹钟和提醒权限。") }
            })
            addView(label("到时暂停本应用音乐，播放一次提示音。通知可在锁屏显示，声音遵循手机静音与勿扰设置；部分手机还需允许后台运行。", 13f))
        })
        content.addView(card().apply {
            addView(label("陪你专注的音乐", 20f))
            musicInfo = label("正在连接播放器…"); addView(musicInfo)
            progress = label("00:00 / 00:00", 13f); addView(progress)
            seek = SeekBar(this@MainActivity).apply {
                max = 1000; minimumHeight = dp(48); contentDescription = "音乐播放进度"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
                    override fun onStopTrackingTouch(bar: SeekBar) { controller?.let { if (it.duration > 0) it.seekTo(it.duration * bar.progress / 1000) }; dragging = false }
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { }
                })
            }; addView(seek)
            addView(row().apply {
                equal(button("上一首") { controller?.seekToPreviousMediaItem() })
                play = button("播放") { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); it.prepare(); it.play() } } }; equal(play)
                equal(button("下一首") { controller?.seekToNextMediaItem() })
            })
            addView(label("播放方式"))
            addView(Spinner(this@MainActivity).apply {
                minimumHeight = dp(48)
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("顺序播放", "单曲循环", "列表循环"))
                setSelection(library.mode.coerceIn(0,2))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) { }
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { library.mode = position; controller?.repeatMode = position }
                }
            })
            trackList = column(); addView(trackList)
            addView(button("＋ 导入手机里的音频") {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 41)
            })
        })
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, MusicService::class.java))).buildAsync().also { future ->
            future.addListener({
                if (isDestroyed) return@addListener
                runCatching { future.get() }.onSuccess { controller = it; it.repeatMode = library.mode; refreshTracks(); update() }
                    .onFailure { musicInfo.text = "播放器连接失败，请关闭应用后重试。" }
            }, ContextCompat.getMainExecutor(this))
        }
        refreshTracks()
    }
    private fun validMinutes(): Int? = parseMinutes(minutes.text.toString()).also {
        if (it == null) { minutes.error = "请输入 1–999 的整数分钟"; minutes.requestFocus() } else minutes.error = null
    }
    override fun onResume() { super.onResume(); visible = true; repository.refreshSchedule(); handler.post(tick) }
    override fun onPause() { visible = false; handler.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() { controllerFuture?.let { MediaController.releaseFuture(it) }; super.onDestroy() }
    private fun update() {
        val state = repository.read()
        if (repository.deliver(state.generation)) { update(); return }
        countdown.text = formatRemaining(state.remaining(SystemClock.elapsedRealtime()))
        phase.text = when (state.phase) { Phase.IDLE -> "准备开始"; Phase.RUNNING -> "专注中，记得照顾身体"; Phase.PAUSED -> "计时已暂停"; Phase.DUE -> "该起身活动一下了" }
        start.text = if (state.phase == Phase.PAUSED) "继续" else "开始"
        start.isEnabled = state.phase != Phase.RUNNING
        pause.isEnabled = state.phase == Phase.RUNNING
        val notifications = Notifications(this).allowed()
        val exact = AlarmScheduler(this).exactAllowed()
        permissionInfo.text = buildString {
            if (repository.rebootNotice()) append("手机已重启，之前的计时已取消。请重新开始。\n")
            append(if (notifications) "✓ 通知已允许" else "⚠ 通知未允许：后台到时将无法显示或播放提醒，请点击下方允许通知。")
            append("\n")
            append(if (exact) "✓ 准确提醒已允许" else "⚠ 准确提醒未允许：后台提醒可能延迟，请点击下方允许准确提醒。")
        }
        controller?.let { player ->
            play.text = if (player.isPlaying) "暂停" else "播放"
            val error = library.error()
            musicInfo.text = error ?: player.currentMediaItem?.mediaMetadata?.title ?: "选择一首音乐"
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
            progress.text = "${formatRemaining(player.currentPosition)} / ${formatRemaining(duration)}"
            seek.isEnabled = duration > 0
            if (!dragging) seek.progress = if (duration > 0) (player.currentPosition * 1000 / duration).toInt() else 0
        }
    }
    private fun refreshTracks() {
        trackList.removeAllViews()
        library.tracks().forEach { track ->
            trackList.addView(row().apply {
                equal(button(track.title) {
                    controller?.let { player ->
                        val index = library.tracks().indexOfFirst { it.uri == track.uri }
                        player.seekTo(index, 0); library.error(null); player.prepare(); player.play()
                    }
                })
                if (library.removable(track)) addView(button("移除") {
                    AlertDialog.Builder(this@MainActivity).setTitle("移除这首音乐？").setMessage(track.title)
                        .setNegativeButton("取消",null).setPositiveButton("移除") { _,_ ->
                            val player = controller
                            val index = library.tracks().indexOfFirst { it.uri == track.uri }
                            if (player != null && index >= 0) player.removeMediaItem(index)
                            library.remove(track); refreshTracks()
                        }.show()
                })
            })
        }
    }
    @Deprecated("Activity result API used without an additional UI framework")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode != 41 || resultCode != RESULT_OK || data == null) return
        val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        val errors = mutableListOf<String>()
        uris.distinct().forEach { uri -> runCatching { library.import(uri) }.onFailure { errors.add("${uri.lastPathSegment ?: "音频"}：无法保留访问权限或读取文件") } }
        controller?.let { player ->
            val selected = player.currentMediaItem?.mediaId
            val position = player.currentPosition
            val tracks = library.tracks()
            player.setMediaItems(tracks.map { it.mediaItem() }, tracks.indexOfFirst { it.uri == selected }.coerceAtLeast(0), position)
            player.prepare()
        }
        refreshTracks()
        if (errors.isNotEmpty()) message(errors.joinToString("\n"))
    }
    private fun message(text: String) { AlertDialog.Builder(this).setMessage(text).setPositiveButton("知道了",null).show() }
}
