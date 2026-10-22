package com.miniedit

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.transformer.*
import com.google.common.collect.ImmutableList
import java.io.File
import kotlin.math.max
import kotlin.math.min

class Clip(val uri: Uri, var speed: Float = 1f, var flipH: Boolean = false, var flipV: Boolean = false)

val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)
val FONTS = arrayOf("sans-serif", "serif", "monospace", "cursive", "sans-serif-condensed")
val ANIMS = arrayOf("Không", "Mờ dần", "Trượt lên", "Phóng to", "Gõ chữ")
val COLORS = intArrayOf(Color.WHITE, Color.YELLOW, Color.RED, Color.CYAN, Color.GREEN)

@UnstableApi
class AnimText(s: String, font: String, color: Int, private val anim: Int) : TextOverlay() {
    private val raw = s
    private val full = style(s, font, color)
    private val fnt = font
    private val col = color
    private fun style(t: String, f: String, c: Int) = SpannableString(t).apply {
        val e = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        setSpan(ForegroundColorSpan(c), 0, length, e)
        setSpan(AbsoluteSizeSpan(72), 0, length, e)
        setSpan(TypefaceSpan(f), 0, length, e)
        setSpan(StyleSpan(Typeface.BOLD), 0, length, e)
    }
    private fun p(t: Long) = min(1f, t / 1_000_000f)
    override fun getText(presentationTimeUs: Long): SpannableString {
        if (anim != 4) return full
        val n = max(1, (raw.length * p(presentationTimeUs)).toInt())
        return style(raw.substring(0, n), fnt, col)
    }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val k = p(presentationTimeUs)
        val b = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, -0.6f)
        when (anim) {
            1 -> b.setAlphaScale(k)
            2 -> b.setBackgroundFrameAnchor(0f, -0.6f - 0.4f * (1 - k)).setAlphaScale(k)
            3 -> b.setScale(0.4f + 0.6f * k, 0.4f + 0.6f * k)
        }
        return b.build()
    }
}

@UnstableApi
class MainActivity : ComponentActivity() {
    private val videos = mutableListOf<Clip>()
    private val audios = mutableListOf<Clip>()
    private var fontIdx = 0; private var animIdx = 0; private var colorIdx = 0
    private var running = false
    private lateinit var vBox: LinearLayout
    private lateinit var aBox: LinearLayout
    private lateinit var vv: VideoView
    private lateinit var status: TextView
    private lateinit var edit: EditText

    private val pickV = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { l ->
        l.forEach { videos.add(Clip(it)) }; refresh()
    }
    private val pickA = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { l ->
        l.forEach { audios.add(Clip(it)) }; refresh()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun btn(t: String, f: (Button) -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; minWidth = 0; minimumWidth = 0
        setOnClickListener { f(this) }
    }
    private fun title(t: String) = TextView(this).apply { text = t; textSize = 16f; setPadding(0, dp(12), 0, dp(4)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(24), dp(12), dp(12)) }
        vv = VideoView(this)
        root.addView(vv, LinearLayout.LayoutParams(-1, dp(220)))
        val top = LinearLayout(this)
        top.addView(btn("+ Video") { pickV.launch("video/*") })
        top.addView(btn("+ Âm thanh") { pickA.launch("audio/*") })
        root.addView(top)
        root.addView(title("Video (⚡tốc độ ↔lật ngang ↕lật dọc ↑lên ⧉nhân đôi ✕xóa)"))
        vBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(vBox)
        root.addView(title("Âm thanh"))
        aBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(aBox)
        root.addView(title("Văn bản"))
        edit = EditText(this).apply { hint = "Nhập chữ (để trống = không thêm)" }; root.addView(edit)
        val tr = LinearLayout(this)
        tr.addView(btn("Font: ${FONTS[0]}") { it.text = "Font: ${FONTS[++fontIdx % FONTS.size]}" })
        tr.addView(btn("Hiệu ứng: ${ANIMS[0]}") { it.text = "Hiệu ứng: ${ANIMS[++animIdx % ANIMS.size]}" })
        tr.addView(btn("Màu") { colorIdx++; it.setTextColor(COLORS[colorIdx % COLORS.size]); it.setBackgroundColor(Color.DKGRAY) })
        root.addView(HorizontalScrollView(this).apply { addView(tr) })
        root.addView(btn("XUẤT VIDEO") { export() })
        status = TextView(this); root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun refresh() { rows(vBox, videos, true); rows(aBox, audios, false) }

    private fun rows(box: LinearLayout, items: MutableList<Clip>, video: Boolean) {
        box.removeAllViews()
        items.forEachIndexed { i, c ->
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(TextView(this).apply {
                text = "${if (video) "V" else "A"}${i + 1} · ${c.speed}x" + (if (c.flipH) " ↔" else "") + (if (c.flipV) " ↕" else "")
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener { vv.setVideoURI(c.uri); vv.start() }
            })
            r.addView(btn("⚡") { c.speed = SPEEDS[(SPEEDS.indexOf(c.speed) + 1) % SPEEDS.size]; refresh() })
            if (video) {
                r.addView(btn("↔") { c.flipH = !c.flipH; refresh() })
                r.addView(btn("↕") { c.flipV = !c.flipV; refresh() })
            }
            r.addView(btn("↑") { if (i > 0) { items.add(i - 1, items.removeAt(i)); refresh() } })
            r.addView(btn("⧉") { items.add(i + 1, Clip(c.uri, c.speed, c.flipH, c.flipV)); refresh() })
            r.addView(btn("✕") { items.removeAt(i); refresh() })
            box.addView(r)
        }
    }

    private fun sonic(speed: Float): List<AudioProcessor> =
        if (speed == 1f) emptyList() else listOf(SonicAudioProcessor().apply { setSpeed(speed) })

    private fun export() {
        if (videos.isEmpty()) { Toast.makeText(this, "Chưa có video", Toast.LENGTH_SHORT).show(); return }
        if (running) return
        val hasAudio = audios.isNotEmpty()
        val vItems = videos.map { c ->
            val vf = mutableListOf<Effect>()
            if (c.speed != 1f) vf.add(SpeedChangeEffect(c.speed))
            if (c.flipH || c.flipV) vf.add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(if (c.flipH) -1f else 1f, if (c.flipV) -1f else 1f).build()
            )
            EditedMediaItem.Builder(MediaItem.fromUri(c.uri))
                .setRemoveAudio(hasAudio)
                .setEffects(Effects(sonic(c.speed), vf)).build()
        }
        val seqs = mutableListOf(EditedMediaItemSequence(vItems))
        if (hasAudio) seqs.add(EditedMediaItemSequence(audios.map { c ->
            EditedMediaItem.Builder(MediaItem.fromUri(c.uri))
                .setRemoveVideo(true).setEffects(Effects(sonic(c.speed), emptyList())).build()
        }))
        val cb = Composition.Builder(seqs)
        val txt = edit.text.toString()
        if (txt.isNotBlank()) {
            val o = AnimText(txt, FONTS[fontIdx % FONTS.size], COLORS[colorIdx % COLORS.size], animIdx % ANIMS.size)
            cb.setEffects(Effects(emptyList(), listOf(OverlayEffect(ImmutableList.of<TextureOverlay>(o)))))
        }
        val out = File(cacheDir, "out_${System.currentTimeMillis()}.mp4")
        val t = Transformer.Builder(this).setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    running = false; save(out)
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    running = false; status.text = "Lỗi: ${exportException.message}"
                }
            }).build()
        running = true
        t.start(cb.build(), out.absolutePath)
        val h = Handler(Looper.getMainLooper()); val ph = ProgressHolder()
        h.post(object : Runnable {
            override fun run() {
                if (!running) return
                t.getProgress(ph); status.text = "Đang xuất... ${ph.progress}%"
                h.postDelayed(this, 500)
            }
        })
    }

    private fun save(f: File) {
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MiniEdit")
        }
        val u = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)!!
        contentResolver.openOutputStream(u)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
        f.delete()
        status.text = "Xong! Đã lưu vào Movies/MiniEdit"
        vv.setVideoURI(u); vv.start()
    }
}
