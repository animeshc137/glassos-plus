package io.github.abhishekkaisar.glassos.personal

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)

    var directHomeKey: Boolean
        get() = sp.getBoolean("directHomeKey", false)
        set(v) { sp.edit().putBoolean("directHomeKey", v).apply() }

    var continueAutoScroll: Boolean
        get() = sp.getBoolean("continueAutoScroll", false)
        set(v) { sp.edit().putBoolean("continueAutoScroll", v).apply() }

    var catalogContent: String
        get() = sp.getString("catalogContent", "mixed") ?: "mixed"
        set(v) { sp.edit().putString("catalogContent", v).apply() }

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) { sp.edit().putBoolean("sound", v).apply() }

    /**
     * Fallback background, shown when the Top Shelf has no title art: a built-in scene (scene:..., default),
     * the user's own photos in Pictures/Glass (hero:photos) or one image (file:...).
     */
    var scene: String
        get() = sp.getString("fallback", DEFAULT_SCENE) ?: DEFAULT_SCENE
        set(v) { sp.edit().putString("fallback", v).apply() }

    /** Top Shelf source: [HeroRepo.SRC_TMDB] (default), [HeroRepo.SRC_APPLE] or [HeroRepo.SRC_SCENES]. */
    var topShelf: String
        get() = sp.getString("topShelf", HeroRepo.SRC_CINEMETA) ?: HeroRepo.SRC_CINEMETA
        set(v) { sp.edit().putString("topShelf", v).apply() }

    /** TMDB key entered in Settings; the build's own key (BuildConfig.TMDB_API_KEY) is used when this is empty. */
    var tmdbKey: String
        get() = sp.getString("tmdbKey", "") ?: ""
        set(v) { sp.edit().putString("tmdbKey", v.trim()).apply() }

    val effectiveTmdbKey: String get() = tmdbKey.ifEmpty { BuildConfig.TMDB_API_KEY }

    /** ISO 3166 country code for the Top Shelf region (streaming availability). */
    var country: String
        get() = sp.getString("country", "") ?: ""
        set(v) { sp.edit().putString("country", v).apply() }

    var replaceHome: Boolean
        get() = sp.getBoolean("replaceHome", false)
        set(v) { sp.edit().putBoolean("replaceHome", v).apply() }

    var dock: List<String>?
        get() = sp.getString("dock", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) { sp.edit().putString("dock", v?.joinToString(",")).apply() }

    /** The drawer's order once the user has moved an app (null: alphabetical). Apps not in it go at the end. */
    var order: List<String>?
        get() = sp.getString("order", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) { sp.edit().putString("order", v?.joinToString(",")).apply() }

    /** Apps left out of the drawer, the top row and the top row picker (Settings > Hidden apps). */
    var hidden: Set<String>
        get() = sp.getString("hidden", null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        set(v) { sp.edit().putString("hidden", v.joinToString(",")).apply() }

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) { sp.edit().putBoolean("setupDone", v).apply() }

    var askedStorage: Boolean
        get() = sp.getBoolean("askedStorage", false)
        set(v) { sp.edit().putBoolean("askedStorage", v).apply() }

    var columns: Int
        get() = LayoutMath.columns(sp.getInt("columns", 4))
        set(v) { sp.edit().putInt("columns", LayoutMath.columns(v)).apply() }
    /** Pixels at 1920 wide; scale with the screen, independent of Android density. */
    var sideMargin: Int
        get() = LayoutMath.clamp(sp.getInt("sideMargin", 80), 24, 128)
        set(v) { sp.edit().putInt("sideMargin", LayoutMath.clamp(v, 24, 128)).apply() }
    var tileGap: Int
        get() = LayoutMath.clamp(sp.getInt("tileGap", 40), 24, 64)
        set(v) { sp.edit().putInt("tileGap", LayoutMath.clamp(v, 24, 64)).apply() }
    var tileSize: Int
        get() = LayoutMath.clamp(sp.getInt("tileSize", 100), 80, 100)
        set(v) { sp.edit().putInt("tileSize", LayoutMath.clamp(v, 80, 100)).apply() }
    val layoutKey: String get() = "$columns:$sideMargin:$tileGap:$tileSize"
    var heroSelectedKey: String
        get() = sp.getString("heroSelectedKey", "") ?: ""
        set(v) { sp.edit().putString("heroSelectedKey", v).apply() }

    var titleDestination: String
        get() = sp.getString("titleDestination", TitleLinks.NUVIO) ?: TitleLinks.NUVIO
        set(v) { sp.edit().putString("titleDestination", v).apply() }

    // ---------- Reddit wallpapers ----------

    private fun list(key: String, def: List<String>): List<String> =
        sp.getString(key, null)?.split('|')?.filter { it.isNotBlank() } ?: def

    private fun putList(key: String, v: Collection<String>) { sp.edit().putString(key, v.joinToString("|")).apply() }

    /** Subreddits (without r/) the Reddit background draws from: presets that are ticked plus the user's own. */
    var redditSubs: List<String>
        get() = list("redditSubs", RedditRepo.DEFAULT_SUBS)
        set(v) = putList("redditSubs", v)

    /** hot, new, top or rising. */
    var redditSort: String
        get() = sp.getString("redditSort", "top") ?: "top"
        set(v) { sp.edit().putString("redditSort", v).apply() }

    /** For Top: day, week, month, year or all. */
    var redditPeriod: String
        get() = sp.getString("redditPeriod", "week") ?: "week"
        set(v) { sp.edit().putString("redditPeriod", v).apply() }

    var redditCount: Int
        get() = sp.getInt("redditCount", 20)
        set(v) { sp.edit().putInt("redditCount", v).apply() }

    /** Smallest image width accepted, in pixels (0: any). */
    var redditMinWidth: Int
        get() = sp.getInt("redditMinWidth", 1920)
        set(v) { sp.edit().putInt("redditMinWidth", v).apply() }

    var redditRefreshHours: Int
        get() = sp.getInt("redditRefreshHours", 6)
        set(v) { sp.edit().putInt("redditRefreshHours", v).apply() }

    // ---------- Art (museum paintings) ----------

    /** Subjects, e.g. "Landscape" (see [ArtRepo.GENRES]); empty: any. */
    var artGenres: List<String>
        get() = list("artGenres", emptyList())
        set(v) = putList("artGenres", v)

    /** Ages, e.g. "Baroque" (see [ArtRepo.ERAS]); empty: any. */
    var artEras: List<String>
        get() = list("artEras", emptyList())
        set(v) = putList("artEras", v)

    /** Movements, e.g. "Impressionism" (see [ArtRepo.MOVEMENTS]); empty: any. */
    var artStyles: List<String>
        get() = list("artStyles", emptyList())
        set(v) = putList("artStyles", v)

    /** Painters: presets that are ticked plus the user's own; empty: any. */
    var artArtists: List<String>
        get() = list("artArtists", emptyList())
        set(v) = putList("artArtists", v)

    var artCount: Int
        get() = sp.getInt("artCount", 24)
        set(v) { sp.edit().putInt("artCount", v).apply() }

    var artRefreshHours: Int
        get() = sp.getInt("artRefreshHours", 24)
        set(v) { sp.edit().putInt("artRefreshHours", v).apply() }

    // ---------- Art screensaver ----------

    /** Seconds each painting stays on the screensaver. */
    var saverSeconds: Int
        get() = sp.getInt("saverSeconds", 60)
        set(v) { sp.edit().putInt("saverSeconds", v).apply() }

    var saverCaption: Boolean
        get() = sp.getBoolean("saverCaption", true)
        set(v) { sp.edit().putBoolean("saverCaption", v).apply() }

    var saverClock: Boolean
        get() = sp.getBoolean("saverClock", false)
        set(v) { sp.edit().putBoolean("saverClock", v).apply() }

    companion object {
        const val DEFAULT_SCENE = "scene:desert"
    }
}
