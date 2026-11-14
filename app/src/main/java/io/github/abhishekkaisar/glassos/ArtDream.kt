package io.github.abhishekkaisar.glassos.personal

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.io.File

/**
 * The art screensaver: a painting filling the screen with a slow crossfade to the next one, a caption bottom left,
 * or (Frame on) each painting framed on a dark, blurred copy of itself with the title under it. Android starts it
 * when idle once it is chosen under Settings > Device Preferences > Screen saver (Settings > Art screensaver can
 * choose it over the TV's own ADB). The paintings are the Art wallpaper's (same filters) or, by default, the ones
 * the screensaver's own filters pick.
 */
class ArtDream : DreamService() {
    private var show: ArtShowView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val v = ArtShowView(this)
        show = v
        setContentView(v)
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); show?.start() }
    override fun onDreamingStopped() { show?.stop(); super.onDreamingStopped() }
    override fun onDetachedFromWindow() { show?.release(); show = null; super.onDetachedFromWindow() }
}

/** Settings > Art screensaver > Preview now: the same show in a normal full-screen window; any key closes it. */
class ArtPreviewActivity : Activity() {
    private var show: ArtShowView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val v = ArtShowView(this)
        show = v
        setContentView(v)
    }

    override fun onStart() { super.onStart(); show?.start() }
    override fun onStop() { show?.stop(); super.onStop() }
    override fun onDestroy() { show?.release(); show = null; super.onDestroy() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean { finish(); return true }
    override fun onTouchEvent(event: MotionEvent?): Boolean { finish(); return true }
}

class ArtShowView(ctx: Context) : View(ctx) {
    /**
     * A painting ready to draw. Framed: scaled to fit, with a blurred [backdrop] behind it. Not framed: [art] is
     * already cropped to the screen and there is no backdrop.
     */
    private class Frame(val art: Bitmap, val backdrop: Bitmap?, val title: String, val subtitle: String, val framed: Boolean)

    private val ui = Handler(Looper.getMainLooper())
    private val prefs = Prefs(ctx)
    private var cur: Frame? = null
    private var next: Frame? = null
    private var fade = 0f
    private var running = false
    private var released = false
    private var token = 0
    private var queue = ArrayList<Slide>()
    private var pos = 0
    private var message = "Loading paintings…"
    private var fader: ValueAnimator? = null

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    /** Darkens the bottom of a full-screen painting so the caption can be read over any picture. */
    private val scrim = Paint()
    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium; color = Color.WHITE; textSize = dp(22f) }
    private val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular; color = Color.WHITE; textSize = dp(16f) }
    private val clock = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.light; color = Color.WHITE; textSize = dp(34f); textAlign = Paint.Align.RIGHT }
    private val tick = object : Runnable {
        override fun run() { if (running && prefs.saverClock) { invalidate(); ui.postDelayed(this, 20_000) } }
    }

    init { setBackgroundColor(Color.BLACK) }

    fun start() {
        if (running || released) return
        running = true
        if (width > 0) loadNext(first = true)
        ui.post(tick)
    }

    fun stop() {
        running = false
        token++
        ui.removeCallbacksAndMessages(null)
        fader?.cancel()
        fader = null
        // Whatever was fading in is dropped; the shown painting stays until the next start.
        next = null
        fade = 0f
    }

    fun release() {
        stop()
        released = true
        cur = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scrim.shader = LinearGradient(0f, h * 0.68f, 0f, h.toFloat(), 0x00000000, 0xB8000000.toInt(), Shader.TileMode.CLAMP)
        if (running && cur == null && next == null && w > 0) loadNext(first = true)
    }

    /** Builds the next painting on a worker thread and fades it in, then waits [Prefs.saverSeconds] for the one after. */
    private fun loadNext(first: Boolean) {
        val t = ++token
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        // The Art wallpaper's paintings, or the screensaver's own when it has its own filters.
        val src = if (prefs.saverSameFilters) HeroRepo.SRC_ART else HeroRepo.SRC_ARTSAVER
        Thread {
            if (queue.isEmpty() || pos >= queue.size) {
                var slides = HeroRepo.cachedTitles(context, src)
                if (slides.isEmpty()) {
                    // Nothing cached yet (first run, or the filters changed): fetch now.
                    HeroRepo.refresh(context, src)
                    slides = HeroRepo.cachedTitles(context, src)
                } else if (HeroRepo.isStale(context, src)) {
                    Thread { HeroRepo.refresh(context, src) }.apply { priority = Thread.MIN_PRIORITY }.start()
                }
                queue = ArrayList(slides.shuffled())
                pos = 0
            }
            var frame: Frame? = null
            var tries = 0
            while (frame == null && tries < queue.size && t == token) {
                val s = queue[pos % queue.size]
                pos++
                tries++
                frame = build(s, w, h)
            }
            ui.post {
                if (t != token || !running) return@post
                if (frame == null) {
                    message = HeroRepo.lastResult[src] ?: "No paintings yet. Open Settings > Art screensaver."
                    invalidate()
                    // Try again later (offline, or the filters match nothing).
                    ui.postDelayed({ if (running && t == token) loadNext(first) }, 60_000)
                } else show(frame!!, first)
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun build(s: Slide, w: Int, h: Int): Frame? {
        try {
            val f: File = s.art ?: return null
            if (!prefs.saverFrame) {
                // Full screen: the painting fills the screen, cropped at the edges where its shape differs.
                val full = WallpaperStore.decodeCrop(f, w, h) ?: return null
                return Frame(full, null, s.title, s.subtitle, false)
            }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return null
            // The painting fits a box inside the screen, leaving room for the frame and the title line.
            val box = minOf(w * 0.84f / o.outWidth, h * 0.74f / o.outHeight)
            val tw = (o.outWidth * box).toInt().coerceAtLeast(1)
            val th = (o.outHeight * box).toInt().coerceAtLeast(1)
            var sample = 1
            while (o.outWidth / (sample * 2) >= tw && o.outHeight / (sample * 2) >= th) sample *= 2
            val raw = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val art = if (raw.width == tw && raw.height == th) raw else Bitmap.createScaledBitmap(raw, tw, th, true).also { if (it !== raw) raw.recycle() }
            val crop = WallpaperStore.decodeCrop(f, w / 2, h / 2)
            val back = if (crop != null) Blur.makeBackdrop(crop).also { crop.recycle() } else art
            return Frame(art, back, s.title, s.subtitle, true)
        } catch (e: Throwable) {
            return null
        }
    }

    private fun show(f: Frame, first: Boolean) {
        next = f
        fader?.cancel()
        fader = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (cur == null) 700 else 1600
            addUpdateListener { fade = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (cancelled || !running) return
                    cur = next; next = null; fade = 0f
                    invalidate()
                    ui.postDelayed({ if (running) loadNext(false) }, prefs.saverSeconds.coerceAtLeast(5) * 1000L)
                }
            })
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val a = cur
        val b = next
        if (a == null && b == null) {
            clock.textAlign = Paint.Align.CENTER
            clock.textSize = dp(22f)
            clock.alpha = 160
            c.drawText(message, width / 2f, height / 2f, clock)
            clock.textAlign = Paint.Align.RIGHT
            clock.textSize = dp(34f)
            clock.alpha = 255
            return
        }
        if (a != null) drawFrame(c, a, 1f)
        if (b != null) drawFrame(c, b, fade)
        if (prefs.saverClock) {
            val fmt = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
            clock.alpha = 200
            c.drawText(android.text.format.DateFormat.format(fmt, System.currentTimeMillis()).toString(), width - width * 0.05f, height - height * 0.05f, clock)
        }
    }

    /** Full screen: the painting, and its caption bottom left over a soft dark band. */
    private fun drawFull(c: Canvas, f: Frame, a: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        bitmapPaint.alpha = (255 * a).toInt()
        rect.set(0f, 0f, w, h)
        c.drawBitmap(f.art, null, rect, bitmapPaint)
        if (prefs.saverCaption && f.title.isNotEmpty()) {
            scrim.alpha = (255 * a).toInt()
            c.drawRect(0f, h * 0.68f, w, h, scrim)
            val x = w * 0.05f
            val avail = w * 0.7f
            val y = h - h * 0.075f - dp(26f)
            title.alpha = (240 * a).toInt()
            sub.alpha = (185 * a).toInt()
            c.drawText(TextUtils.ellipsize(f.title, title, avail, TextUtils.TruncateAt.END).toString(), x, y, title)
            if (f.subtitle.isNotEmpty()) c.drawText(TextUtils.ellipsize(f.subtitle, sub, avail, TextUtils.TruncateAt.END).toString(), x, y + dp(26f), sub)
        }
    }

    private fun drawFrame(c: Canvas, f: Frame, a: Float) {
        if (!f.framed) { drawFull(c, f, a); return }
        val backdrop = f.backdrop ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        bitmapPaint.alpha = (255 * a).toInt()
        rect.set(0f, 0f, w, h)
        c.drawBitmap(backdrop, null, rect, bitmapPaint)
        fill.color = alphaOf(Color.BLACK, 0.58f * a)
        c.drawRect(rect, fill)
        val pw = f.art.width.toFloat()
        val ph = f.art.height.toFloat()
        val l = (w - pw) / 2
        val t = (h - ph) / 2 - h * 0.03f
        // A soft shadow, then the frame.
        for (i in 1..8) {
            fill.color = alphaOf(Color.BLACK, 0.045f * a)
            val g = dp(3.5f) * i
            c.drawRect(l - g, t - g + dp(6f), l + pw + g, t + ph + g + dp(6f), fill)
        }
        val fr = dp(9f)
        fill.color = alphaOf(0xFF191714.toInt(), a)
        c.drawRect(l - fr, t - fr, l + pw + fr, t + ph + fr, fill)
        fill.color = alphaOf(0xFF4A4238.toInt(), a)
        c.drawRect(l - fr, t - fr, l + pw + fr, t - fr + dp(1.5f), fill)
        c.drawBitmap(f.art, l, t, bitmapPaint)
        if (prefs.saverCaption && f.title.isNotEmpty()) {
            val avail = pw + 2 * fr
            val y = t + ph + fr + dp(36f)
            title.alpha = (235 * a).toInt()
            sub.alpha = (160 * a).toInt()
            c.drawText(TextUtils.ellipsize(f.title, title, avail, TextUtils.TruncateAt.END).toString(), l - fr, y, title)
            if (f.subtitle.isNotEmpty()) c.drawText(TextUtils.ellipsize(f.subtitle, sub, avail, TextUtils.TruncateAt.END).toString(), l - fr, y + dp(26f), sub)
        }
    }
}
