package io.github.abhishekkaisar.glassos.personal

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Famous paintings as a background and screensaver, in the spirit of Samsung's Frame TV. Google Arts & Culture has
 * no public interface, so the paintings come from the Art Institute of Chicago's open collection
 * (api.artic.edu, public-domain works only, free images). The pictures are filtered by what the user ticks in
 * Settings: subject, age, movement and painter. Only landscape-shaped paintings are kept (the home is 16:9).
 */
object ArtRepo {
    /** Subject to the words that find it in the museum's subject, term and title fields. */
    val GENRES = listOf(
        "Landscape" to "landscape landscapes",
        "Portrait" to "portrait portraits",
        "Still life" to "still life",
        "Seascape" to "seascape seascapes marine sea ships harbor",
        "Cityscape" to "cityscape cityscapes city street urban town",
        "Flowers and gardens" to "flowers garden gardens floral",
        "Animals" to "animal animals horse horses dog cat bird birds",
        "Mythology" to "mythology myth mythological",
        "Religion" to "religion religious christ madonna saint",
        "Everyday life" to "genre scene scenes daily life interior domestic",
        "Winter and snow" to "winter snow",
        "Abstract" to "abstract abstraction"
    )

    class Era(val label: String, val from: Int, val to: Int)
    val ERAS = listOf(
        Era("Before 1400", -3000, 1399),
        Era("Renaissance 1400-1599", 1400, 1599),
        Era("Baroque 1600-1749", 1600, 1749),
        Era("Neoclassical and Romantic 1750-1849", 1750, 1849),
        Era("Realism and Impressionism 1850-1899", 1850, 1899),
        Era("Modern 1900-1949", 1900, 1949),
        Era("Postwar and contemporary 1950 on", 1950, 2100)
    )

    val STYLES = listOf(
        "Impressionism", "Post-Impressionism", "Romanticism", "Realism", "Baroque", "Rococo", "Neoclassicism",
        "Expressionism", "Cubism", "Surrealism", "Symbolism", "Fauvism", "Pointillism", "Art Nouveau",
        "Abstract Expressionism", "Pop art", "Hudson River School", "Renaissance"
    )

    /** Famous painters, written as the museum writes them. Anyone else can be added by name in Settings. */
    val ARTISTS = listOf(
        "Claude Monet", "Vincent van Gogh", "Pierre-Auguste Renoir", "Edgar Degas", "Georges Seurat", "Paul Cézanne",
        "Paul Gauguin", "Édouard Manet", "Camille Pissarro", "Mary Cassatt", "Berthe Morisot", "Gustave Caillebotte",
        "Edward Hopper", "Georgia O'Keeffe", "Winslow Homer", "Thomas Cole", "John Singer Sargent", "James McNeill Whistler",
        "J. M. W. Turner", "John Constable", "Caspar David Friedrich", "Gustave Courbet", "Jean-François Millet",
        "Rembrandt van Rijn", "Johannes Vermeer", "Francisco de Goya", "El Greco", "Canaletto", "Henri Matisse",
        "Pablo Picasso", "Marc Chagall", "Wassily Kandinsky", "Piet Mondrian", "Odilon Redon", "Paul Signac"
    )

    val COUNTS = listOf(12, 24, 48)
    val REFRESH_HOURS = listOf(6, 24, 72, 168)
    val SAVER_SECONDS = listOf(10, 30, 60, 300, 900)

    private const val API = "https://api.artic.edu/api/v1/artworks/search"
    private const val IIIF = "https://www.artic.edu/iiif/2"
    private const val PAGE = 100
    private const val MAX_PAGE = 10
    private val FIELDS = JSONArray(listOf("id", "title", "artist_title", "date_display", "image_id", "thumbnail"))

    /** Fetches paintings that match the filters. Returns an empty list (and sets [HeroRepo.lastResult]) when there are none. */
    fun fetch(prefs: Prefs): List<HeroRepo.Item> {
        val want = prefs.artCount
        var failure: String? = null
        // Highlights first; if the museum has none for these filters, any matching painting.
        for (highlights in if (prefs.artHighlights) listOf(true, false) else listOf(false)) {
            val body = query(prefs, highlights)
            val (code0, text0) = post(JSONObject(body.toString()).put("fields", JSONArray(listOf("id"))).put("limit", 1).toString())
            if (code0 != 200 || text0 == null) { failure = describe(code0); break }
            val total = try { JSONObject(text0).getJSONObject("pagination").getInt("total") } catch (e: Throwable) { 0 }
            if (total <= 0) continue
            val pages = ((total + PAGE - 1) / PAGE).coerceIn(1, MAX_PAGE)
            val order = (1..pages).shuffled().take(3)
            val found = LinkedHashMap<String, HeroRepo.Item>()
            for (p in order) {
                val (code, text) = post(JSONObject(body.toString()).put("fields", FIELDS).put("limit", PAGE).put("page", p).toString())
                if (code != 200 || text == null) { failure = describe(code); break }
                val data = try { JSONObject(text).getJSONArray("data") } catch (e: Throwable) { continue }
                for (i in 0 until data.length()) item(data.optJSONObject(i))?.let { found.putIfAbsent(it.id, it) }
                if (found.size >= want * 2) break
            }
            if (found.isNotEmpty()) return found.values.shuffled().take(want)
            if (failure != null) break
        }
        HeroRepo.lastResult[HeroRepo.SRC_ART] = failure ?: "No paintings matched. Loosen the filters."
        return emptyList()
    }

    private fun describe(code: Int) = if (code == -1) "Could not reach api.artic.edu" else "The museum answered HTTP $code"

    /** The Elasticsearch body for the ticked filters: every group must match, and within a group any choice may. */
    private fun query(prefs: Prefs, highlights: Boolean): JSONObject {
        val must = JSONArray()
        must.put(JSONObject().put("term", JSONObject().put("is_public_domain", true)))
        must.put(JSONObject().put("match_phrase", JSONObject().put("artwork_type_title", "Painting")))
        must.put(JSONObject().put("exists", JSONObject().put("field", "image_id")))
        if (highlights) must.put(JSONObject().put("term", JSONObject().put("is_boosted", true)))
        anyOf(prefs.artGenres.mapNotNull { g -> GENRES.firstOrNull { it.first == g }?.second }.map { words ->
            JSONObject().put("multi_match", JSONObject().put("query", words).put("fields", JSONArray(listOf("subject_titles", "term_titles", "title"))))
        })?.let { must.put(it) }
        anyOf(prefs.artEras.mapNotNull { e -> ERAS.firstOrNull { it.label == e } }.map { e ->
            JSONObject().put("range", JSONObject().put("date_start", JSONObject().put("gte", e.from).put("lte", e.to)))
        })?.let { must.put(it) }
        anyOf(prefs.artStyles.map { s ->
            JSONObject().put("multi_match", JSONObject().put("query", s).put("type", "phrase").put("fields", JSONArray(listOf("style_titles", "term_titles"))))
        })?.let { must.put(it) }
        anyOf(prefs.artArtists.filter { it.isNotBlank() }.map { a ->
            JSONObject().put("multi_match", JSONObject().put("query", a).put("operator", "and").put("fields", JSONArray(listOf("artist_title", "artist_display"))))
        })?.let { must.put(it) }
        return JSONObject().put("query", JSONObject().put("bool", JSONObject().put("must", must)))
    }

    private fun anyOf(clauses: List<JSONObject>): JSONObject? {
        if (clauses.isEmpty()) return null
        return JSONObject().put("bool", JSONObject().put("should", JSONArray(clauses)).put("minimum_should_match", 1))
    }

    private fun item(o: JSONObject?): HeroRepo.Item? {
        o ?: return null
        val image = o.optString("image_id").takeIf { it.isNotBlank() && it != "null" } ?: return null
        val th = o.optJSONObject("thumbnail")
        val w = th?.optInt("width", 0) ?: 0
        val h = th?.optInt("height", 0) ?: 0
        // Landscape-shaped only: a portrait painting cropped to 16:9 would lose its subject.
        if (w <= 0 || h <= 0 || w * 100 < h * 115) return null
        val id = o.optInt("id", 0).takeIf { it > 0 } ?: return null
        val artist = o.optString("artist_title").takeIf { it.isNotBlank() && it != "null" } ?: "Unknown artist"
        val date = o.optString("date_display").takeIf { it.isNotBlank() && it != "null" }
        return HeroRepo.Item("art_$id", o.optString("title"), if (date != null) "$artist, $date" else artist,
            "https://www.artic.edu/artworks/$id", "$IIIF/$image/full/1686,/0/default.jpg", null, null, Slide.PHOTO)
    }

    private fun post(json: String): Pair<Int, String?> = try {
        val c = URL(API).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 25_000
            c.doOutput = true
            c.setRequestProperty("User-Agent", HeroRepo.USER_AGENT)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            code to if (code == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally { c.disconnect() }
    } catch (e: Throwable) { -1 to null }
}
