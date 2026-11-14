package io.github.abhishekkaisar.glassos.personal

/**
 * Wallpapers from subreddits, like Projectivy's Reddit source: tick the subreddits you like or add your own, choose
 * Hot / New / Top / Rising (and the period for Top), how many pictures to keep and the smallest size to accept.
 *
 * Uses each subreddit's public RSS feed (`/r/<name>/top.rss`), so no account or key. Reddit's JSON listing refuses
 * apps without a login, but the feed answers. The feed does not say whether a post is NSFW or how big its picture is,
 * so only add safe-for-work subreddits; sizes come from the "[3840x2160]" most wallpaper posts put in their title, and
 * anything else is measured after it downloads (too small or portrait pictures are dropped then).
 * Reddit limits how fast it answers, so subreddits are fetched one at a time with a pause.
 */
object RedditRepo {
    /** Subreddit to a short description, for the picker. */
    val PRESETS = listOf(
        "EarthPorn" to "Landscapes",
        "CityPorn" to "Cities",
        "SpacePorn" to "Space",
        "SkyPorn" to "Skies",
        "WaterPorn" to "Water",
        "VillagePorn" to "Villages",
        "ImaginaryLandscapes" to "Painted landscapes",
        "wallpapers" to "Wallpapers",
        "wallpaper" to "Wallpapers",
        "Amoledbackgrounds" to "Dark and AMOLED",
        "MinimalWallpaper" to "Minimal",
        "ArtPorn" to "Paintings",
        "carporn" to "Cars",
        "Cyberpunk" to "Cyberpunk",
        "RoomPorn" to "Interiors"
    )
    val DEFAULT_SUBS = listOf("EarthPorn", "wallpapers")
    val SORTS = listOf("hot" to "Hot", "new" to "New", "top" to "Top", "rising" to "Rising")
    val PERIODS = listOf("day" to "Past day", "week" to "Past week", "month" to "Past month", "year" to "Past year", "all" to "All time")
    val COUNTS = listOf(10, 20, 40, 80)
    val MIN_WIDTHS = listOf(0 to "Any", 1280 to "1280 px", 1920 to "1920 px (Full HD)", 2560 to "2560 px", 3840 to "3840 px (4K)")
    val REFRESH_HOURS = listOf(1, 6, 12, 24, 72)

    private val NAME = Regex("^[A-Za-z0-9_]{2,21}$")
    private const val MAX_SUBS = 8
    private const val PAUSE_MS = 1200L
    private val ENTRY = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val TITLE = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val ID = Regex("<id>t3_([A-Za-z0-9]+)</id>")
    private val LINK = Regex("<link href=\"([^\"]+)\"")
    private val IMAGE = Regex("https://i\\.redd\\.it/[A-Za-z0-9._-]+\\.(?:jpe?g|png|webp)", RegexOption.IGNORE_CASE)
    private val SIZE = Regex("[\\[(]\\s*(\\d{3,5})\\s*[xX×*]\\s*(\\d{3,5})\\s*[\\])]")

    /** A subreddit name as typed: with or without "r/", letters, digits and underscore only. */
    fun clean(input: String): String? {
        val n = input.trim().removePrefix("/").removePrefix("r/").removePrefix("R/").trim('/', ' ')
        return n.takeIf { NAME.matches(it) }
    }

    /** Fetches the current pictures. Returns an empty list (and sets [HeroRepo.lastResult]) when there are none. */
    fun fetch(prefs: Prefs): List<HeroRepo.Item> {
        val subs = prefs.redditSubs.mapNotNull { clean(it) }.distinctBy { it.lowercase() }.take(MAX_SUBS)
        if (subs.isEmpty()) { HeroRepo.lastResult[HeroRepo.SRC_REDDIT] = "Choose at least one subreddit"; return emptyList() }
        val sort = prefs.redditSort.takeIf { s -> SORTS.any { it.first == s } } ?: "top"
        val lists = ArrayList<List<HeroRepo.Item>>()
        var failure: String? = null
        for ((i, sub) in subs.withIndex()) {
            if (i > 0) Thread.sleep(PAUSE_MS)
            var url = "https://www.reddit.com/r/$sub/$sort.rss?limit=100"
            if (sort == "top") url += "&t=${prefs.redditPeriod}"
            val (code, body) = HeroRepo.get(url)
            if (code == 200 && body != null) { lists.add(parse(body, sub, prefs.redditMinWidth)); continue }
            failure = when (code) {
                -1 -> "Could not reach reddit.com"
                429 -> "Reddit is limiting this TV right now. Try again in a few minutes."
                403 -> "Reddit refused the request (HTTP 403)"
                404, 301, 302 -> "r/$sub was not found (private or banned?)"
                else -> "Reddit answered HTTP $code"
            }
            // A limit applies to every subreddit after this one too.
            if (code == 429 || code == -1) break
        }
        // Take turns between subreddits so one does not fill the shelf.
        val out = LinkedHashMap<String, HeroRepo.Item>()
        var round = 0
        while (lists.any { round < it.size }) {
            for (l in lists) l.getOrNull(round)?.let { if (!out.containsKey(it.id)) out[it.id] = it }
            round++
        }
        if (out.isEmpty()) {
            HeroRepo.lastResult[HeroRepo.SRC_REDDIT] = failure ?: "No pictures matched. Lower the minimum size or add subreddits."
        }
        // The caller keeps the first pictures that download and measure up, so return spare candidates.
        return out.values.take(prefs.redditCount * 2)
    }

    private fun parse(xml: String, sub: String, minW: Int): List<HeroRepo.Item> {
        val items = ArrayList<HeroRepo.Item>()
        for (m in ENTRY.findAll(xml)) {
            val e = m.groupValues[1]
            val id = ID.find(e)?.groupValues?.get(1) ?: continue
            // A direct picture link (i.redd.it); videos, galleries, text and link posts have none.
            val image = IMAGE.find(e)?.value ?: continue
            val title = unescape(TITLE.find(e)?.groupValues?.get(1).orEmpty()).trim()
            val size = SIZE.find(title)
            if (size != null) {
                val w = size.groupValues[1].toInt()
                val h = size.groupValues[2].toInt()
                if (w < minW || w * 10 < h * 13) continue
            }
            items.add(HeroRepo.Item("reddit_$id", title, "r/$sub", LINK.find(e)?.groupValues?.get(1).orEmpty(), image,
                null, null, Slide.PHOTO, minWidth = minW, verify = true))
        }
        return items
    }

    private fun unescape(s: String): String = s.replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
