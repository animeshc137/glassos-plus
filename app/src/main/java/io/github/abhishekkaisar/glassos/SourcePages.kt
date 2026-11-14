package io.github.abhishekkaisar.glassos.personal

import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** One settings sub-page: a heading and the rows [build] adds. */
class Page(val title: String, val build: PageBuilder.() -> Unit)

/**
 * Settings sub-pages (Reddit wallpapers, Art, Screensaver) shown in the settings panel in place of the list, with
 * their own Back stack. Changes to a source's filters are collected and applied once, when the pages are left.
 */
class PagesHost(private val act: HomeScreen) : ScrollView(act.ctx) {
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = true; clipToPadding = false }
    private val stack = ArrayList<Page>()
    private val focusAt = ArrayList<Int>()
    private val changedSources = LinkedHashSet<String>()

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isSmoothScrollingEnabled = true
        val pad = dpi(22f)
        list.setPadding(pad, dpi(8f), pad, dpi(8f))
        addView(list, ViewGroup.LayoutParams(-1, -2))
        if (android.os.Build.VERSION.SDK_INT >= 23) setOnScrollChangeListener { _, _, _, _, _ -> invalidateDeep(list) }
    }

    fun open(page: Page) {
        stack.clear(); focusAt.clear(); changedSources.clear()
        stack.add(page)
        render(-1)
    }

    fun push(page: Page) {
        focusAt.add(focusedIndex())
        stack.add(page)
        render(-1)
    }

    /** Back one page. Returns false when the first page was left (the caller then hides the pages). */
    fun back(): Boolean {
        hideKeyboard()
        if (stack.size <= 1) { stack.clear(); return false }
        stack.removeAt(stack.size - 1)
        render(focusAt.removeAt(focusAt.size - 1))
        return true
    }

    /** Rebuilds the current page (a choice changed which rows are shown), keeping the focused row. */
    fun rebuild() = render(focusedIndex())

    /** A filter of [src] changed: its cache is refetched when the pages are left. */
    fun changed(src: String) { changedSources.add(src) }

    /** Called when the pages are left: refetches what changed. */
    fun applyChanges() {
        hideKeyboard()
        val srcs = changedSources.toList()
        changedSources.clear()
        stack.clear(); focusAt.clear()
        for (s in srcs) {
            if (act.prefs.topShelf == s) act.refetchTopShelf() else HeroRepo.invalidate(context, s)
        }
    }

    private fun hideKeyboard() {
        for (i in 0 until list.childCount) (list.getChildAt(i) as? TextInput)?.hideKeyboard()
    }

    private fun focusedIndex(): Int {
        var v: View? = findFocus()
        while (v != null && v.parent !== list) v = v.parent as? View
        return if (v == null) -1 else list.indexOfChild(v)
    }

    private fun render(focusIndex: Int) {
        hideKeyboard()
        val page = stack.lastOrNull() ?: return
        list.removeAllViews()
        list.addView(TextView(context).apply {
            text = page.title; textSize = 26f; typeface = Fonts.medium; setTextColor(0xFFFFFFFF.toInt()); setPadding(dpi(6f), 0, 0, dpi(8f))
        })
        page.build(PageBuilder(act, this, list))
        scrollTo(0, 0)
        post {
            val target = list.getChildAt(focusIndex)?.takeIf { it.isFocusable && it.visibility == View.VISIBLE }
                ?: (0 until list.childCount).map { list.getChildAt(it) }.firstOrNull { it.isFocusable }
            target?.requestFocus()
            invalidateDeep(list)
        }
    }
}

/** Adds rows to a [Page]. */
class PageBuilder(val act: HomeScreen, val host: PagesHost, private val list: LinearLayout) {
    private val ctx get() = act.ctx
    private fun rowLp() = LinearLayout.LayoutParams(-1, ctx.dpi(48f)).apply { bottomMargin = ctx.dpi(5f) }

    fun note(text: String) {
        list.addView(TextView(ctx).apply {
            this.text = text; textSize = 14f; typeface = Fonts.regular; setTextColor(0x99FFFFFF.toInt())
            setLineSpacing(0f, 1.15f); setPadding(ctx.dpi(6f), ctx.dpi(4f), ctx.dpi(6f), ctx.dpi(10f))
        })
    }

    fun action(label: String, value: String = "›", click: (RowView) -> Unit): RowView {
        val r = RowView(ctx, label)
        r.titleView.textSize = 17f
        r.titleView.maxLines = 1
        r.titleView.ellipsize = TextUtils.TruncateAt.END
        r.valueView.textSize = 14f
        r.valueView.text = value
        r.onClick = { click(r) }
        list.addView(r, rowLp())
        return r
    }

    fun toggle(label: String, get: () -> Boolean, set: (Boolean) -> Unit): RowView {
        fun text() = if (get()) "On" else "Off"
        return action(label, text()) { r -> set(!get()); r.valueView.text = text() }
    }

    /** OK steps through [options] (key to label). [after] runs once the new value is saved. */
    fun choice(label: String, options: List<Pair<String, String>>, get: () -> String, set: (String) -> Unit, after: (() -> Unit)? = null): RowView {
        fun text() = options.firstOrNull { it.first == get() }?.second ?: get()
        return action(label, text()) { r ->
            val i = options.indexOfFirst { it.first == get() }
            set(options[(i + 1) % options.size].first)
            r.valueView.text = text()
            after?.invoke()
        }
    }

    fun sub(label: String, value: () -> String, page: () -> Page): RowView =
        action(label, value()) { host.push(page()) }

    /** A text field row: type, then Done on the keyboard calls [submit] with the text. */
    fun input(hint: String, kind: TextInput.Kind, submit: (String) -> Unit): TextInput {
        val f = TextInput(ctx, hint, 60, kind, sizeDp = 17f)
        f.onDone = {
            f.hideKeyboard()
            val t = f.text.toString().trim()
            if (t.isNotEmpty()) submit(t)
        }
        list.addView(f, LinearLayout.LayoutParams(-1, ctx.dpi(46f)).apply { bottomMargin = ctx.dpi(8f) })
        return f
    }
}

/** The Reddit, Art and Screensaver pages. */
object SourcePages {
    private val ui = Handler(Looper.getMainLooper())

    private fun summary(v: List<String>): String = when (v.size) {
        0 -> "Any"
        1 -> v[0]
        else -> "${v.size} chosen"
    }

    private fun hours(h: Int): String = when {
        h == 1 -> "1 hour"
        h < 24 -> "$h hours"
        h == 24 -> "1 day"
        h % 24 == 0 -> "${h / 24} days"
        else -> "$h hours"
    }

    fun seconds(s: Int): String = when {
        s < 60 -> "$s seconds"
        s == 60 -> "1 minute"
        s < 3600 -> "${s / 60} minutes"
        s == 3600 -> "1 hour"
        else -> "${s / 3600} hours"
    }

    /** The choices for Wallpaper interval, as (seconds, text); the built-in 8 seconds is marked. */
    fun wallpaperSecondsOptions(): List<Pair<String, String>> = Prefs.WALLPAPER_SECONDS.map {
        it.toString() to seconds(it) + if (it == Prefs.DEFAULT_WALLPAPER_SECONDS) " (default)" else ""
    }

    private fun wallpaperTime(b: PageBuilder) {
        b.choice("Wallpaper interval", wallpaperSecondsOptions(), { b.act.prefs.wallpaperSeconds.toString() }, { b.act.setWallpaperSeconds(it.toInt()) })
    }
    private fun hoursOptions(v: List<Int>) = v.map { it.toString() to hours(it) }

    /** Fetches [src] now (ignoring the cache age) and says what it found. */
    private fun refreshNow(b: PageBuilder, src: String, row: RowView) {
        val act = b.act
        row.valueView.text = "Working…"
        Thread {
            HeroRepo.invalidate(act.ctx, src)
            val ok = try { HeroRepo.refresh(act.ctx, src) } catch (e: Throwable) { false }
            val msg = HeroRepo.lastResult[src] ?: if (ok) "Done" else "Nothing found"
            ui.post {
                row.valueView.text = "›"
                Toast.makeText(act.ctx, msg, Toast.LENGTH_LONG).show()
                if (ok && act.prefs.topShelf == src) act.setWallpaper(act.prefs.scene, save = false)
                b.host.rebuild()
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun useAsBackground(b: PageBuilder, src: String) {
        val act = b.act
        b.toggle("Use as background", { act.prefs.topShelf == src }) { on ->
            act.setTopShelf(if (on) src else HeroRepo.SRC_CINEMETA)
        }
    }

    /**
     * A list to tick from. [presets] are (stored value, shown text). Values the user adds go to the same list and show
     * below the presets; unticking one removes it. [clean] turns what was typed into a stored value, or null if invalid.
     */
    private fun pick(b: PageBuilder, presets: List<Pair<String, String>>, get: () -> List<String>, set: (List<String>) -> Unit,
                     addHint: String?, kind: TextInput.Kind, clean: (String) -> String?, label: (String) -> String, src: String) {
        if (addHint != null) {
            b.input(addHint, kind) { typed ->
                val v = clean(typed)
                if (v == null) Toast.makeText(b.act.ctx, "That name is not valid", Toast.LENGTH_SHORT).show()
                else {
                    if (get().none { it.equals(v, true) }) set(get() + v)
                    b.host.changed(src)
                    b.host.rebuild()
                }
            }
        }
        fun row(value: String, shown: String, custom: Boolean) {
            fun on() = get().any { it.equals(value, true) }
            b.action(shown, if (on()) "✓" else "") { r ->
                if (on()) set(get().filterNot { it.equals(value, true) }) else set(get() + value)
                b.host.changed(src)
                if (custom) b.host.rebuild() else r.valueView.text = if (on()) "✓" else ""
            }
        }
        for ((v, shown) in presets) row(v, shown, false)
        for (v in get()) if (presets.none { it.first.equals(v, true) }) row(v, label(v) + "  ·  yours", true)
    }

    // ---------- Reddit ----------

    fun reddit(): Page = Page("Reddit wallpapers") {
        val p = act.prefs
        note("Pictures from the subreddits you choose, like Projectivy. Videos, galleries and portrait pictures are skipped. Reddit does not mark NSFW posts in its feed, so only add safe-for-work subreddits.")
        useAsBackground(this, HeroRepo.SRC_REDDIT)
        sub("Subreddits", { summary(p.redditSubs.map { "r/$it" }) }) { subreddits() }
        wallpaperTime(this)
        choice("Sort by", RedditRepo.SORTS, { p.redditSort }, { p.redditSort = it }, after = { host.changed(HeroRepo.SRC_REDDIT); host.rebuild() })
        if (p.redditSort == "top") {
            choice("Top of", RedditRepo.PERIODS, { p.redditPeriod }, { p.redditPeriod = it }, after = { host.changed(HeroRepo.SRC_REDDIT) })
        }
        choice("Pictures to keep", RedditRepo.COUNTS.map { it.toString() to "$it" }, { p.redditCount.toString() }, { p.redditCount = it.toInt() },
            after = { host.changed(HeroRepo.SRC_REDDIT) })
        choice("Minimum size", RedditRepo.MIN_WIDTHS.map { it.first.toString() to it.second }, { p.redditMinWidth.toString() }, { p.redditMinWidth = it.toInt() },
            after = { host.changed(HeroRepo.SRC_REDDIT) })
        choice("Refresh every", hoursOptions(RedditRepo.REFRESH_HOURS), { p.redditRefreshHours.toString() }, { p.redditRefreshHours = it.toInt() })
        action("Refresh now", "›") { r -> refreshNow(this, HeroRepo.SRC_REDDIT, r) }
        HeroRepo.lastResult[HeroRepo.SRC_REDDIT]?.let { note("Last refresh: $it") }
    }

    private fun subreddits(): Page = Page("Subreddits") {
        val p = act.prefs
        note("OK ticks or unticks a subreddit. Type any other subreddit (with or without r/) and press Done to add it.")
        pick(this, RedditRepo.PRESETS.map { it.first to "r/${it.first}  ·  ${it.second}" }, { p.redditSubs }, { p.redditSubs = it },
            "Add a subreddit, e.g. Naturewallpaper", TextInput.Kind.PLAIN, { RedditRepo.clean(it) }, { "r/$it" }, HeroRepo.SRC_REDDIT)
    }

    // ---------- Art ----------

    /** One set of Art filters: the wallpaper's, or the screensaver's own. */
    private class Filt(val prefs: Prefs, val saver: Boolean) {
        val src: String get() = if (saver) HeroRepo.SRC_ARTSAVER else HeroRepo.SRC_ART
        var genres: List<String>
            get() = if (saver) prefs.saverGenres else prefs.artGenres
            set(v) { if (saver) prefs.saverGenres = v else prefs.artGenres = v }
        var eras: List<String>
            get() = if (saver) prefs.saverEras else prefs.artEras
            set(v) { if (saver) prefs.saverEras = v else prefs.artEras = v }
        var styles: List<String>
            get() = if (saver) prefs.saverStyles else prefs.artStyles
            set(v) { if (saver) prefs.saverStyles = v else prefs.artStyles = v }
        var artists: List<String>
            get() = if (saver) prefs.saverArtists else prefs.artArtists
            set(v) { if (saver) prefs.saverArtists = v else prefs.artArtists = v }
        var regions: List<String>
            get() = if (saver) prefs.saverRegions else prefs.artRegions
            set(v) { if (saver) prefs.saverRegions = v else prefs.artRegions = v }
        var hideNude: Boolean
            get() = if (saver) prefs.saverHideNude else prefs.artHideNude
            set(v) { if (saver) prefs.saverHideNude = v else prefs.artHideNude = v }
        var count: Int
            get() = if (saver) prefs.saverCount else prefs.artCount
            set(v) { if (saver) prefs.saverCount = v else prefs.artCount = v }
        fun clear() { genres = emptyList(); eras = emptyList(); styles = emptyList(); artists = emptyList(); regions = emptyList() }
    }

    /** Subject, Age, Movement, Painters, clearing them and how many to keep: the same rows for the wallpaper and the screensaver. */
    private fun filterRows(b: PageBuilder, f: Filt) {
        b.sub("Subject", { summary(f.genres) }) { genres(f) }
        b.sub("Age", { summary(f.eras) }) { eras(f) }
        b.sub("Movement", { summary(f.styles) }) { styles(f) }
        b.sub("Painters", { summary(f.artists) }) { painters(f) }
        b.sub("India & South Asia", { summary(f.regions) }) { india(f) }
        b.toggle("Hide nude artwork", { f.hideNude }) {
            f.hideNude = it
            b.host.changed(f.src)
        }
        b.action("Clear all filters", "›") { _ ->
            f.clear()
            b.host.changed(f.src)
            b.host.rebuild()
        }
        b.choice("Paintings to keep", ArtRepo.COUNTS.map { it.toString() to "$it" }, { f.count.toString() }, { f.count = it.toInt() },
            after = { b.host.changed(f.src) })
    }

    fun art(): Page = Page("Art paintings") {
        val p = act.prefs
        note("Famous paintings: the public-domain scans Google Arts & Culture made of museum paintings, as kept on Wikimedia Commons. Tick nothing to see a mix of famous painters. India & South Asia adds Hindu religious art and Indian painting; Hide nude artwork leaves out nudes (a guess from titles, so it can miss some).")
        useAsBackground(this, HeroRepo.SRC_ART)
        wallpaperTime(this)
        filterRows(this, Filt(p, false))
        choice("Refresh every", hoursOptions(ArtRepo.REFRESH_HOURS), { p.artRefreshHours.toString() }, { p.artRefreshHours = it.toInt() })
        action("Refresh now", "›") { r -> refreshNow(this, HeroRepo.SRC_ART, r) }
        sub("Screensaver", { if (screensaverActive(act)) "On" else "Off" }) { screensaver() }
        HeroRepo.lastResult[HeroRepo.SRC_ART]?.let { note("Last refresh: $it") }
    }

    private fun genres(f: Filt): Page = Page("Subject") {
        note("What the painting shows, guessed from words in its title and categories. Choose several to mix them.")
        pick(this, ArtRepo.GENRES.map { it.first to it.first }, { f.genres }, { f.genres = it }, null, TextInput.Kind.PLAIN, { it }, { it }, f.src)
    }

    private fun eras(f: Filt): Page = Page("Age") {
        note("When the painting was made.")
        pick(this, ArtRepo.ERAS.map { it.label to it.label }, { f.eras }, { f.eras = it }, null, TextInput.Kind.PLAIN, { it }, { it }, f.src)
    }

    private fun styles(f: Filt): Page = Page("Movement") {
        note("Shows the famous painters of that movement (pick painters too to narrow it).")
        pick(this, ArtRepo.MOVEMENTS.map { it.first to it.first }, { f.styles }, { f.styles = it }, null, TextInput.Kind.PLAIN, { it }, { it }, f.src)
    }

    private fun india(f: Filt): Page = Page("India & South Asia") {
        note("Hindu religious art and the art of India and the Indian subcontinent, from Wikimedia Commons: the gods, the miniature schools, Raja Ravi Varma, the Bengal School, folk art and more. They mix in with any painters you choose; with nothing else chosen you see only these.")
        pick(this, ArtRepo.INDIA.map { it.label to it.label }, { f.regions }, { f.regions = it }, null, TextInput.Kind.PLAIN, { it }, { it }, f.src)
    }

    private fun painters(f: Filt): Page = Page("Painters") {
        note("OK ticks a painter. Type any other painter's name and press Done to add them.")
        pick(this, ArtRepo.ARTISTS.map { it to it }, { f.artists }, { f.artists = it },
            "Add a painter, e.g. Katsushika Hokusai", TextInput.Kind.ARTIST, { t -> t.trim().replace("|", " ").takeIf { it.length >= 3 } }, { it }, f.src)
    }

    // ---------- Screensaver ----------

    fun component(act: HomeScreen) = ComponentName(act.ctx, ArtDream::class.java)

    private fun screensaverActive(act: HomeScreen): Boolean = try {
        val v = Settings.Secure.getString(act.ctx.contentResolver, "screensaver_components") ?: ""
        v.split(',').any { it == component(act).flattenToString() || it == component(act).flattenToShortString() }
    } catch (e: Throwable) { false }

    fun screensaver(): Page = Page("Art screensaver") {
        val p = act.prefs
        note("Shows famous paintings one at a time, full screen. Turn Frame on to see each one framed, with its title below.")
        action("Make it the screensaver", if (screensaverActive(act)) "Active" else "›") { r ->
            r.valueView.text = "Working…"
            Thread {
                val result = try {
                    AdbClient.connect(act.ctx, onPrompt = { ui.post { Toast.makeText(act.ctx, "Choose Allow on the TV's prompt (tick Always allow)", Toast.LENGTH_LONG).show() } }).use { adb ->
                        adb.shell("settings put secure screensaver_components ${component(act).flattenToString()}")
                        adb.shell("settings put secure screensaver_enabled 1")
                    }
                    null
                } catch (e: Throwable) { Running.error(e) }
                ui.post {
                    if (result != null) { r.valueView.text = "›"; Toast.makeText(act.ctx, result, Toast.LENGTH_LONG).show() }
                    else { r.valueView.text = if (screensaverActive(act)) "Active" else "›"; Toast.makeText(act.ctx, "Art screensaver set", Toast.LENGTH_SHORT).show() }
                }
            }.start()
        }
        action("Preview now") { _ ->
            act.ctx.startActivity(Intent(act.ctx, ArtPreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        toggle("Frame", { p.saverFrame }) { p.saverFrame = it }
        choice("Change painting every", ArtRepo.SAVER_SECONDS.map { it.toString() to seconds(it) }, { p.saverSeconds.toString() }, { p.saverSeconds = it.toInt() })
        toggle("Show title and painter", { p.saverCaption }) { p.saverCaption = it }
        toggle("Show clock", { p.saverClock }) { p.saverClock = it }
        toggle("Same filters as the Art wallpaper", { p.saverSameFilters }) {
            p.saverSameFilters = it
            host.changed(HeroRepo.SRC_ARTSAVER)
            host.rebuild()
        }
        val saverSrc = if (p.saverSameFilters) HeroRepo.SRC_ART else HeroRepo.SRC_ARTSAVER
        if (p.saverSameFilters) {
            note("The screensaver shows the same paintings as the Art wallpaper. Change them under Art paintings.")
        } else {
            note("Filters for the screensaver only. They work like the Art wallpaper's.")
            filterRows(this, Filt(p, true))
        }
        action("Refresh now", "›") { r -> refreshNow(this, saverSrc, r) }
        HeroRepo.lastResult[saverSrc]?.let { note("Last refresh: $it") }
        note("When it starts is set by Android: Settings > Device Preferences > Screen saver. 'Make it the screensaver' picks it there for you over the TV's own ADB. Fire TV may not allow third-party screensavers.")
    }
}
