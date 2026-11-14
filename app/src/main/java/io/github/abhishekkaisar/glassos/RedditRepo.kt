package io.github.abhishekkaisar.glassos.personal

import org.json.JSONObject

/**
 * Wallpapers from subreddits, like Projectivy's Reddit source: tick the subreddits you like or add your own, choose
 * Hot / New / Top / Rising (and the period for Top), how many pictures to keep and the smallest size to accept.
 * Uses Reddit's public listing (`/r/a+b/top.json`), so no account or key. Posts marked NSFW, videos, stickied posts
 * and portrait pictures are always skipped.
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
    private const val PAGE = 100
    private const val MAX_PAGES = 3

    /** A subreddit name as typed: with or without "r/", letters, digits and underscore only. */
    fun clean(input: String): String? {
        val n = input.trim().removePrefix("/").removePrefix("r/").removePrefix("R/").trim('/', ' ')
        return n.takeIf { NAME.matches(it) }
    }

    /** Fetches the current pictures. Returns an empty list (and sets [HeroRepo.lastResult]) when there are none. */
    fun fetch(prefs: Prefs): List<HeroRepo.Item> {
        val subs = prefs.redditSubs.mapNotNull { clean(it) }.distinctBy { it.lowercase() }
        if (subs.isEmpty()) { HeroRepo.lastResult[HeroRepo.SRC_REDDIT] = "Choose at least one subreddit"; return emptyList() }
        val sort = prefs.redditSort.takeIf { s -> SORTS.any { it.first == s } } ?: "top"
        val want = prefs.redditCount
        val minW = prefs.redditMinWidth
        val out = LinkedHashMap<String, HeroRepo.Item>()
        var after: String? = null
        var failure: String? = null
        for (page in 0 until MAX_PAGES) {
            var url = "https://www.reddit.com/r/${subs.joinToString("+")}/$sort.json?limit=$PAGE&raw_json=1"
            if (sort == "top") url += "&t=${prefs.redditPeriod}"
            if (after != null) url += "&after=$after"
            val (code, body) = HeroRepo.get(url)
            if (code != 200 || body == null) {
                failure = when (code) {
                    -1 -> "Could not reach reddit.com"
                    403, 429 -> "Reddit refused the request (HTTP $code). Try again later."
                    404 -> "Subreddit not found or private"
                    else -> "Reddit answered HTTP $code"
                }
                break
            }
            val data = try { JSONObject(body).getJSONObject("data") } catch (e: Throwable) { failure = "Unexpected answer from Reddit"; break }
            val children = data.optJSONArray("children")
            if (children == null || children.length() == 0) break
            for (i in 0 until children.length()) {
                val d = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                val item = item(d, minW) ?: continue
                out.putIfAbsent(item.id, item)
                if (out.size >= want) break
            }
            after = data.optString("after").takeIf { it.isNotBlank() && it != "null" }
            if (out.size >= want || after == null) break
        }
        if (out.isEmpty()) {
            HeroRepo.lastResult[HeroRepo.SRC_REDDIT] = failure ?: "No pictures matched. Lower the minimum size or add subreddits."
        }
        return out.values.toList()
    }

    private fun item(d: JSONObject, minW: Int): HeroRepo.Item? {
        if (d.optBoolean("over_18") || d.optBoolean("is_video") || d.optBoolean("stickied") || d.optBoolean("spoiler")) return null
        val src = d.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)?.optJSONObject("source") ?: return null
        val w = src.optInt("width", 0)
        val h = src.optInt("height", 0)
        // Landscape only (the home is 16:9), and not smaller than asked.
        if (w <= 0 || h <= 0 || w * 10 < h * 13 || w < minW) return null
        val direct = d.optString("url")
        val url = if (isImage(direct)) direct else src.optString("url")
        if (!url.startsWith("https://")) return null
        val id = d.optString("id").ifEmpty { return null }
        val sub = d.optString("subreddit")
        return HeroRepo.Item("reddit_$id", d.optString("title"), if (sub.isEmpty()) "" else "r/$sub",
            "https://www.reddit.com" + d.optString("permalink"), url, null, null, Slide.PHOTO)
    }

    private fun isImage(u: String): Boolean {
        if (!u.startsWith("https://")) return false
        val path = u.substringBefore('?').lowercase()
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp")
    }
}
