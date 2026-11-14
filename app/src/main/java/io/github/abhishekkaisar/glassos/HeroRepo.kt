package io.github.abhishekkaisar.glassos.personal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One Top Shelf slide. [art] is a private file; a static scene uses [sceneId] instead. */
class Slide(
    val key: String,
    val kind: Int,
    val art: File? = null,
    val sceneId: String? = null,
    val title: String = "",
    val subtitle: String = "",
    val url: String? = null,
    val logo: File? = null,
    /** App that OK on the hero opens (the title's streaming service, when installed). */
    val pkg: String? = null
) {
    companion object {
        const val STATIC = 0
        const val PHOTO = 1
        const val TITLE = 2
    }
}

/** A decoded slide: the screen-sized image, its pre-blurred copy for the glass, and the title logo. */
class Loaded(val slide: Slide, val sharp: Bitmap, val blurred: Bitmap, val logo: Bitmap?, val washed: Boolean = false) {
    var bornClock = 0L
    var index = 0
    /** True once its texture has been uploaded to the GPU (drawn once, invisibly). */
    var warmed = false
    /** Brightness behind the user pill, the chevron and the subtitle (see [Tone]), measured once on the loader thread. */
    val pillBg = Tone.measure(blurred, Tone.PILL)
    val chevronBg = Tone.measure(blurred, Tone.CHEVRON)
    val subBg = Tone.measure(blurred, Tone.SUBTITLE)
}

/**
 * Sources for the rotating Top Shelf: Cinemeta titles (default), optional TMDB or the Apple TV app's featured titles
 * (unofficial, off by default) or the built-in scenes. Titles are cached on disk and refreshed at most every
 * 6 hours. The user's own photos (copied to private storage) are one of the fallback choices.
 */
object HeroRepo {
    /** Fallback choice: the user's own photos in Pictures/Glass. */
    const val PHOTOS = "hero:photos"
    val APPLE_TV_PKGS = listOf("com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv")

    const val SRC_TMDB = "tmdb"
    const val SRC_CINEMETA = "cinemeta"
    const val SRC_APPLE = "apple"
    const val SRC_SCENES = "scenes"
    /** Wallpapers from subreddits (see [RedditRepo]) and museum paintings (see [ArtRepo]); both are plain images, no titles. */
    const val SRC_REDDIT = "reddit"
    const val SRC_ART = "art"
    /** The art screensaver's own paintings, when it does not use the Art wallpaper's filters. Not a Top Shelf choice. */
    const val SRC_ARTSAVER = "artsaver"
    val SOURCES = listOf(SRC_CINEMETA to "Movies & series (Cinemeta)", SRC_TMDB to "TMDB", SRC_APPLE to "Apple TV (unofficial)",
        SRC_REDDIT to "Reddit wallpapers", SRC_ART to "Art (museum paintings)", SRC_SCENES to "Built-in scenes")
    fun sourceLabel(id: String) = SOURCES.firstOrNull { it.first == id }?.second ?: id

    private const val REFRESH_MS = 6 * 3600_000L
    /** How long a source's cache stays fresh: Reddit and Art follow their own setting, the rest 6 hours. */
    private fun refreshMs(ctx: Context, src: String): Long = when (src) {
        SRC_REDDIT -> Prefs(ctx).redditRefreshHours.coerceAtLeast(1) * 3600_000L
        SRC_ART, SRC_ARTSAVER -> Prefs(ctx).artRefreshHours.coerceAtLeast(1) * 3600_000L
        else -> REFRESH_MS
    }

    /** What the last [refresh] of each source found, for the Settings pages ("14 images", "Could not reach reddit.com"). */
    val lastResult = java.util.concurrent.ConcurrentHashMap<String, String>()
    private const val RETRY_MS = 30 * 60_000L
    private const val MAX_TITLES = 8
    /** Unofficial: the US storefront of the tv.apple.com web app. It may stop working at any time. */
    private val STOREFRONTS = listOf("143441" to "en-US")
    /** Public key the tv.apple.com web app sends; re-read from the page if Apple rotates it. */
    private const val DEFAULT_UTSK = "6e3013c6d6fae3c2::::::235656c069bb0efb"
    private val EXTS = setOf("jpg", "jpeg", "png", "webp")

    fun isHero(id: String) = id.startsWith("hero:")

    private fun dir(ctx: Context, sub: String) = File(ctx.filesDir, "hero/$sub").apply { mkdirs() }

    /** Copies the user's images into private storage (when readable) and returns the private copies. */
    fun localImages(ctx: Context, canRead: Boolean): List<File> {
        val priv = dir(ctx, "local")
        if (canRead) {
            try {
                val root = WallpaperStore.userDir()
                val wanted = HashMap<String, File>()
                File(root, "wallpapers").listFiles()?.forEach { if (it.isFile && it.extension.lowercase() in EXTS) wanted["w_" + it.name] = it }
                root.listFiles()?.forEach {
                    if (it.isFile && it.extension.lowercase() in EXTS && !it.name.equals("avatar.png", true)) wanted["r_" + it.name] = it
                }
                for ((name, src) in wanted) {
                    val dst = File(priv, name)
                    if (!dst.exists() || dst.length() != src.length() || src.lastModified() > dst.lastModified()) {
                        val tmp = File(priv, "$name.tmp")
                        src.copyTo(tmp, overwrite = true)
                        tmp.renameTo(dst)
                    }
                }
                if (root.exists()) priv.listFiles()?.forEach { if (it.name !in wanted && !it.name.endsWith(".tmp")) it.delete() }
            } catch (e: Throwable) { }
        }
        return (priv.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: emptyList())
            .sortedWith(compareBy({ !it.name.startsWith("w_") }, { it.name.lowercase() }))
    }

    /** Titles from the on-disk cache (works offline). */
    fun cachedTitles(ctx: Context, src: String): List<Slide> {
        if (src == SRC_SCENES) return emptyList()
        val f = File(dir(ctx, src), "titles.json")
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONObject(f.readText()).getJSONArray("items")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val art = File(o.getString("art"))
                if (!art.exists()) return@mapNotNull null
                Slide(
                    key = "t:" + o.getString("id"), kind = o.optInt("kind", Slide.TITLE), art = art,
                    title = o.optString("title"), subtitle = o.optString("subtitle"),
                    url = o.optString("url").ifEmpty { null },
                    logo = o.optString("logo").ifEmpty { null }?.let { File(it) }?.takeIf { it.exists() },
                    pkg = o.optString("pkg").ifEmpty { null }
                )
            }
        } catch (e: Throwable) { emptyList() }
    }

    fun isStale(ctx: Context, src: String): Boolean {
        if (src == SRC_SCENES) return false
        val d = dir(ctx, src)
        val f = File(d, "titles.json")
        val attempt = File(d, "attempt").lastModified()
        val now = System.currentTimeMillis()
        val upgraded = src != SRC_CINEMETA || File(d, "catalog-v3").exists()
        if (upgraded && f.exists() && now - f.lastModified() < refreshMs(ctx, src)) return false
        return now - attempt >= RETRY_MS
    }

    /** Forgets [src]'s cache age, so the next check refetches (after a change of country, key or apps). */
    fun invalidate(ctx: Context, src: String) {
        val d = dir(ctx, src)
        File(d, "attempt").delete()
        File(d, "titles.json").takeIf { it.exists() }?.setLastModified(0)
    }

    /** Fetches [src]'s current titles and their art. Returns true when the cache changed. Call off the UI thread. */
    @Synchronized
    fun refresh(ctx: Context, src: String): Boolean {
        if (src == SRC_SCENES) return false
        val d = dir(ctx, src)
        try { File(d, "attempt").writeText(System.currentTimeMillis().toString()) } catch (e: Throwable) { }
        lastResult.remove(src)
        val items = when (src) {
            SRC_CINEMETA -> Cinemeta.fetch(Prefs(ctx).catalogContent, cachedTitles(ctx, src).map { it.key.removePrefix("t:") }.toSet())
            SRC_TMDB -> Tmdb.fetch(ctx)
            SRC_REDDIT -> RedditRepo.fetch(Prefs(ctx))
            SRC_ART -> ArtRepo.fetch(ArtRepo.wallpaperFilters(Prefs(ctx)), SRC_ART)
            SRC_ARTSAVER -> ArtRepo.fetch(ArtRepo.saverFilters(Prefs(ctx)), SRC_ARTSAVER)
            else -> appleItems(d) ?: return false
        }
        val keep = when (src) {
            SRC_REDDIT -> Prefs(ctx).redditCount
            SRC_ART -> Prefs(ctx).artCount
            SRC_ARTSAVER -> Prefs(ctx).saverCount
            else -> Int.MAX_VALUE
        }
        val changed = store(d, items, if (src == SRC_APPLE) "us" else Prefs(ctx).country, keep, parallel = src == SRC_REDDIT || src == SRC_ART || src == SRC_ARTSAVER)
        if (src == SRC_REDDIT || src == SRC_ART || src == SRC_ARTSAVER) {
            // The fetchers say why they found nothing (offline, blocked, filters too strict) in lastResult.
            val why = lastResult[src]
            lastResult[src] = when {
                changed -> "${cachedTitles(ctx, src).size} images"
                why != null -> why
                items.isEmpty() -> "Nothing found"
                else -> "Found ${items.size} but none could be downloaded or were big enough"
            }
        }
        if (changed && src == SRC_CINEMETA) {
            File(d, "catalog-v3").writeText("3")
            android.util.Log.i("GlassCatalog", "Cinemeta cached titles: ${cachedTitles(ctx, src).size}")
        }
        return changed
    }

    private fun appleItems(d: File): List<Item>? {
        val utskFile = File(d, "utsk")
        var utsk = if (utskFile.exists()) utskFile.readText().trim() else DEFAULT_UTSK
        var json: String? = null
        var sfUsed = ""
        loop@ for ((sf, locale) in STOREFRONTS) {
            for (attempt in 0..1) {
                val (code, body) = get(canvasUrl(sf, locale, utsk))
                if (code == 200 && body != null) { json = body; sfUsed = sf; break@loop }
                // A rotated key fails on every storefront; re-read it from the web app once.
                if (attempt == 0 && sf == STOREFRONTS.last().first) {
                    val fresh = pageUtsk() ?: break
                    if (fresh == utsk) break
                    utsk = fresh
                    try { utskFile.writeText(fresh) } catch (e: Throwable) { }
                } else break
            }
        }
        val text = json ?: return null
        return try { parse(text) } catch (e: Throwable) { null }
    }

    /**
     * Downloads [items] and writes titles.json. With [parallel] (the picture sources, whose images are big and slow to
     * render at the server) four download at a time and titles.json is rewritten after every group, so a reader such
     * as the screensaver can start on the first pictures while the rest still arrive.
     */
    private fun store(d: File, items: List<Item>, region: String, max: Int = Int.MAX_VALUE, parallel: Boolean = false): Boolean {
        if (items.isEmpty()) return false
        val out = JSONArray()
        // One item's art (and logo) downloaded; null when its art could not be fetched or was rejected.
        fun fetch(it: Item): JSONObject? {
            val base = it.id.replace(Regex("[^A-Za-z0-9.]"), "_")
            val art = File(d, if (it.id.startsWith("cinemeta_")) "${base}_hq.jpg" else "$base.jpg")
            if (!art.exists() && !downloadArt(it, art)) return null
            var logo: File? = null
            if (it.logo != null) {
                val lf = File(d, "${base}_logo.png")
                if (lf.exists() || download(it.logo, lf)) logo = lf
            }
            return JSONObject().apply {
                put("id", it.id); put("title", it.title); put("subtitle", it.subtitle)
                put("url", it.url); put("art", art.path); put("logo", logo?.path ?: ""); put("pkg", it.pkg ?: ""); put("kind", it.kind)
            }
        }
        fun write() {
            val f = File(d, "titles.json")
            val tmp = File(d, "titles.json.tmp")
            tmp.writeText(JSONObject().put("region", region).put("fetchedAt", System.currentTimeMillis()).put("items", out).toString())
            tmp.renameTo(f)
        }
        for (group in items.chunked(if (parallel) 4 else 1)) {
            if (out.length() >= max) break
            val got = arrayOfNulls<JSONObject>(group.size)
            if (group.size == 1) got[0] = fetch(group[0])
            else group.mapIndexed { i, it -> Thread { got[i] = fetch(it) }.also { t -> t.start() } }.forEach { t -> t.join() }
            for (o in got) if (o != null && out.length() < max) out.put(o)
            if (parallel && out.length() > 0) write()
        }
        if (out.length() == 0) return false
        write()
        // Keep only what titles.json uses (pictures fetched beyond [max], and last time's, go).
        val keep = HashSet<String>()
        for (i in 0 until out.length()) {
            val o = out.getJSONObject(i)
            keep.add(File(o.getString("art")).name)
            o.optString("logo").takeIf { it.isNotEmpty() }?.let { keep.add(File(it).name) }
        }
        d.listFiles()?.forEach { if ((it.name.endsWith(".jpg") || it.name.endsWith(".png")) && it.name !in keep) it.delete() }
        return true
    }

    private fun canvasUrl(sf: String, locale: String, utsk: String) =
        "https://uts-api.itunes.apple.com/uts/v3/canvases/Roots/watchNow?caller=web&locale=$locale&pfm=web&sf=$sf&v=100&utsk=$utsk"

    class Item(val id: String, val title: String, val subtitle: String, val url: String, val art: String, val logo: String?, val pkg: String? = null,
        /** [Slide.TITLE] (title, logo, Open) or [Slide.PHOTO] (just the picture, with a slow zoom). */
        val kind: Int = Slide.TITLE,
        /** Reject the downloaded picture when it is narrower than this (0: no limit). */
        val minWidth: Int = 0,
        /** Measure the downloaded picture and reject portrait ones (the source gave no size). */
        val verify: Boolean = false)

    private fun fill(template: String, w: Int, h: Int, c: String, f: String) =
        template.replace("{w}", w.toString()).replace("{h}", h.toString()).replace("{c}", c).replace("{f}", f)

    private fun parse(text: String): List<Item> {
        val shelves = JSONObject(text).getJSONObject("data").getJSONObject("canvas").getJSONArray("shelves")
        val out = ArrayList<Item>()
        for (s in 0 until shelves.length()) {
            val shelf = shelves.getJSONObject(s)
            if (!shelf.optString("displayType").startsWith("epicStage")) continue
            val arr = shelf.optJSONArray("items") ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type = o.optString("type")
                if (type != "Show" && type != "Movie") continue
                val imgs = o.optJSONObject("images") ?: continue
                val bg = imgs.optJSONObject("shelfImageBackground") ?: imgs.optJSONObject("coverArt16X9") ?: continue
                val art = fill(bg.optString("url"), 1920, 1080, "sr", "jpg")
                if (!art.startsWith("https://")) continue
                val logo = imgs.optJSONObject("contentLogo")?.let { l ->
                    val lw = l.optInt("width", 0); val lh = l.optInt("height", 0)
                    if (lw <= 0 || lh <= 0) null else {
                        val w = 900
                        fill(l.optString("url"), w, (w.toLong() * lh / lw).toInt().coerceAtLeast(1), "", "png")
                    }
                }
                val subtitle = o.optString("explainabilityLine").takeIf { it.isNotBlank() && it != "null" }
                    ?: if (type == "Movie") "Movie" else "TV Show"
                out.add(Item(o.getString("id"), o.optString("title"), subtitle, o.optString("url").substringBefore('?'), art, logo, APPLE_TV_PKGS[0]))
                if (out.size >= MAX_TITLES) return out
            }
            if (out.isNotEmpty()) return out
        }
        return out
    }

    private fun pageUtsk(): String? {
        val (code, body) = get("https://tv.apple.com/us")
        if (code != 200 || body == null) return null
        return Regex("\"utsk\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    }

    /** Reddit and the museum APIs ask clients to say who they are; a generic Java agent gets refused. */
    const val USER_AGENT = "android:io.github.abhishekkaisar.glassos:1.0 (TV launcher wallpaper fetch)"

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        if (!url.contains("apple.com")) {
            connectTimeout = 10_000; readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            return@apply
        }
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 7.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
        setRequestProperty("Accept", "application/json, text/html, */*")
        setRequestProperty("Origin", "https://tv.apple.com")
        setRequestProperty("Referer", "https://tv.apple.com/")
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String?> = try {
        val c = open(url)
        for ((k, v) in headers) c.setRequestProperty(k, v)
        try {
            val code = c.responseCode
            code to if (code == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally { c.disconnect() }
    } catch (e: Throwable) { -1 to null }

    fun download(url: String, to: File): Boolean = try {
        val c = open(url)
        try {
            if (c.responseCode != 200) false else {
                val tmp = File(to.path + ".tmp")
                c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp.renameTo(to)
            }
        } finally { c.disconnect() }
    } catch (e: Throwable) { false }

    /** [download] for wallpapers: pictures wider than [MAX_ART_W] (Reddit has 8K ones) are shrunk to keep the cache small. */
    private fun downloadArt(item: Item, to: File): Boolean {
        if (!download(item.art, to)) return false
        if (item.minWidth > 0 || item.verify) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try { BitmapFactory.decodeFile(to.path, o) } catch (e: Throwable) { }
            val w = o.outWidth
            val h = o.outHeight
            if (w <= 0 || h <= 0 || w < item.minWidth || (item.verify && w * 10 < h * 13)) { to.delete(); return false }
        }
        shrink(to)
        return true
    }

    private const val MAX_ART_W = 2560

    private fun shrink(f: File) {
        try {
            if (f.length() < 1_200_000L) return
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth <= MAX_ART_W) return
            var sample = 1
            while (o.outWidth / (sample * 2) >= MAX_ART_W) sample *= 2
            val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
            val out = if (src.width > MAX_ART_W) Bitmap.createScaledBitmap(src, MAX_ART_W, (src.height.toLong() * MAX_ART_W / src.width).toInt().coerceAtLeast(1), true) else src
            val tmp = File(f.path + ".tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            tmp.renameTo(f)
            if (out !== src) src.recycle()
            out.recycle()
        } catch (e: Throwable) { }
    }

    /** The user's own photos as slides. */
    fun photoSlides(ctx: Context, canRead: Boolean): List<Slide> = localImages(ctx, canRead).map {
        Slide("a:" + it.name + ":" + it.length(), Slide.PHOTO, art = it)
    }

    /** Loads a slide at screen size and pre-blurs it. Call off the UI thread. */
    fun load(ctx: Context, s: Slide, w: Int, h: Int): Loaded? = try {
        if (s.sceneId != null) {
            val wp = WallpaperStore.load(ctx, s.sceneId, w, h)
            Loaded(s, wp.sharp, wp.blurred, null)
        } else {
            val sharp = s.art?.let { WallpaperStore.decodeCrop(it, w, h) }
            if (sharp == null) null else {
                // The glass samples the art without the title wash, so blur first, then bake the wash in.
                val blurred = Blur.makeBackdrop(sharp)
                val washed = s.kind == Slide.TITLE && bakeWash(sharp)
                Loaded(s, sharp, blurred, s.logo?.let { decodeLogo(it, w * 36 / 100, h * 20 / 100) }, washed)
            }
        }
    } catch (e: Throwable) { null }

    /**
     * Draws the title slides' soft top-left wash into the decoded art once, so the GPU does not draw an extra
     * 1190x670 radial gradient on every crossfade frame. Same gradient and geometry as [HeroView.washPaint].
     */
    private fun bakeWash(b: Bitmap): Boolean = try {
        val c = android.graphics.Canvas(b)
        val p = HeroView.washPaint(b.width)
        val rad = b.width * 0.62f
        c.drawRect(0f, 0f, rad, minOf(rad, b.height.toFloat()), p)
        true
    } catch (e: Throwable) { false }

    private fun decodeLogo(f: File, maxW: Int, maxH: Int): Bitmap? = try {
        val src = BitmapFactory.decodeFile(f.path)
        if (src == null) null else {
            val s = minOf(maxW / src.width.toFloat(), maxH / src.height.toFloat(), 1f)
            if (s >= 0.999f) src else Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true).also { src.recycle() }
        }
    } catch (e: Throwable) { null }

    /** Settings thumbnail: the first of the user's photos, or null. */
    fun thumb(ctx: Context, id: String, canRead: Boolean, w: Int, h: Int): Bitmap? {
        val f = photoSlides(ctx, canRead).firstOrNull()?.art ?: return null
        return WallpaperStore.decodeCrop(f, w, h)
    }
}
