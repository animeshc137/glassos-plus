package io.github.abhishekkaisar.glassos.personal

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Famous paintings as a background and screensaver, in the spirit of Samsung's Frame TV. The pictures are the
 * high-resolution public-domain scans Google Arts & Culture's Art Project made of museum paintings, as kept on
 * Wikimedia Commons ("Google Art Project works by <painter>"). Google Arts & Culture itself has no public feed.
 *
 * The filters work like this:
 * - Painters: the painters ticked (or typed) in Settings; none ticked means a random pick of famous ones.
 * - Movement: the famous painters of that movement ([MOVEMENTS]); combined with painters, both must apply.
 * - Age: the year the painting was made, from its Commons record.
 * - Subject: words in the painting's title and Commons categories ([GENRES]). This is a best guess, not a catalogue.
 * Only landscape-shaped scans (the home is 16:9) of at least 1600 px are kept.
 */
object ArtRepo {
    /** Subject to the words that mark it in a painting's title or categories. */
    val GENRES = listOf(
        "Landscape" to listOf("landscape", "valley", "mountain", "hills", "meadow", "fields", "forest", "woods", "river", "lake", "countryside", "sunset", "sunrise", "view of", "view from"),
        "Seascape" to listOf("sea", "ship", "boat", "harbor", "harbour", "coast", "beach", "shore", "marine", "storm", "waves", "sailing", "fishing"),
        "Portrait" to listOf("portrait", "self-portrait", "lady", "woman", "girl", "boy", "madame", "mrs.", "young man", "old man"),
        "Still life" to listOf("still life", "still-life", "fruit", "apples", "bowl", "vase", "table", "bottle"),
        "Flowers and gardens" to listOf("flower", "garden", "iris", "sunflower", "roses", "lilies", "lily", "bouquet", "poppies", "poppy", "blossom"),
        "Cityscape" to listOf("city", "street", "boulevard", "paris", "london", "venice", "bridge", "canal", "village", "town", "cathedral", "square"),
        "Animals" to listOf("horse", "dog", "cat", "bird", "cow", "sheep", "lion", "stag", "deer", "hunt"),
        "Mythology" to listOf("venus", "apollo", "diana", "bacchus", "cupid", "jupiter", "hercules", "perseus", "nymph", "minerva", "mythology", "myth"),
        "Religion" to listOf("madonna", "christ", "saint", "virgin", "crucifixion", "annunciation", "nativity", "holy", "adoration", "angel", "resurrection", "last supper", "pietà"),
        "Everyday life" to listOf("peasant", "dance", "café", "cafe", "tavern", "kitchen", "market", "interior", "concert", "reading", "harvest", "farm", "wedding"),
        "Winter and snow" to listOf("snow", "winter", "ice", "skaters", "frozen"),
        "Abstract" to listOf("composition", "abstract", "improvisation", "impression")
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

    /** Movement to its famous painters, spelled as the preset list below spells them. */
    val MOVEMENTS = listOf(
        "Renaissance" to listOf("Leonardo da Vinci", "Sandro Botticelli", "Raphael", "Titian", "Jan van Eyck", "Pieter Bruegel the Elder", "Albrecht Dürer", "El Greco"),
        "Baroque" to listOf("Rembrandt", "Johannes Vermeer", "Frans Hals", "Peter Paul Rubens", "Caravaggio", "Diego Velázquez", "Claude Lorrain", "Nicolas Poussin", "Jan Steen", "Pieter de Hooch", "Canaletto"),
        "Dutch Golden Age" to listOf("Rembrandt", "Johannes Vermeer", "Frans Hals", "Jan Steen", "Pieter de Hooch"),
        "Rococo" to listOf("Jean-Honoré Fragonard", "Canaletto"),
        "Neoclassicism" to listOf("Jacques-Louis David"),
        "Romanticism" to listOf("Caspar David Friedrich", "Eugène Delacroix", "J. M. W. Turner", "John Constable", "Francisco Goya", "Ivan Aivazovsky", "Thomas Cole", "Albert Bierstadt", "Frederic Edwin Church"),
        "Hudson River School" to listOf("Thomas Cole", "Albert Bierstadt", "Frederic Edwin Church"),
        "Realism" to listOf("Gustave Courbet", "Jean-François Millet", "Winslow Homer", "John Singer Sargent"),
        "Impressionism" to listOf("Claude Monet", "Pierre-Auguste Renoir", "Edgar Degas", "Édouard Manet", "Camille Pissarro", "Alfred Sisley", "Mary Cassatt", "Berthe Morisot", "Gustave Caillebotte", "Joaquín Sorolla"),
        "Post-Impressionism" to listOf("Vincent van Gogh", "Paul Cézanne", "Paul Gauguin", "Georges Seurat", "Paul Signac"),
        "Symbolism and Art Nouveau" to listOf("Gustav Klimt", "Edvard Munch", "Odilon Redon", "Vilhelm Hammershøi"),
        "Expressionism and abstraction" to listOf("Wassily Kandinsky", "Edvard Munch", "Henri Matisse"),
        "Ukiyo-e (Japan)" to listOf("Katsushika Hokusai", "Utagawa Hiroshige")
    )

    /** Famous painters, spelled as their "Google Art Project works by ..." category on Commons. */
    val ARTISTS = listOf(
        "Claude Monet", "Vincent van Gogh", "Pierre-Auguste Renoir", "Edgar Degas", "Georges Seurat", "Paul Cézanne",
        "Paul Gauguin", "Édouard Manet", "Camille Pissarro", "Alfred Sisley", "Mary Cassatt", "Berthe Morisot",
        "Gustave Caillebotte", "Paul Signac", "Winslow Homer", "John Singer Sargent", "Thomas Cole", "Albert Bierstadt",
        "Frederic Edwin Church", "J. M. W. Turner", "John Constable", "Caspar David Friedrich", "Eugène Delacroix",
        "Gustave Courbet", "Jean-François Millet", "Rembrandt", "Johannes Vermeer", "Frans Hals", "Jan Steen",
        "Pieter de Hooch", "Peter Paul Rubens", "Caravaggio", "Diego Velázquez", "El Greco", "Francisco Goya",
        "Titian", "Leonardo da Vinci", "Sandro Botticelli", "Raphael", "Jan van Eyck", "Albrecht Dürer",
        "Pieter Bruegel the Elder", "Nicolas Poussin", "Claude Lorrain", "Canaletto", "Jean-Honoré Fragonard",
        "Jacques-Louis David", "Gustav Klimt", "Edvard Munch", "Odilon Redon", "Wassily Kandinsky", "Henri Matisse",
        "Katsushika Hokusai", "Utagawa Hiroshige", "Ivan Aivazovsky", "Joaquín Sorolla", "Vilhelm Hammershøi"
    )

    /** Who is shown when no painter or movement is chosen. */
    private val DEFAULT_POOL = listOf(
        "Claude Monet", "Vincent van Gogh", "Pierre-Auguste Renoir", "Edgar Degas", "Paul Cézanne", "Paul Gauguin",
        "Édouard Manet", "Camille Pissarro", "Alfred Sisley", "Winslow Homer", "John Constable", "Caspar David Friedrich",
        "Gustave Courbet", "Rembrandt", "Johannes Vermeer", "Frans Hals", "El Greco", "Caravaggio", "Titian",
        "Peter Paul Rubens", "Pieter Bruegel the Elder", "Katsushika Hokusai", "Utagawa Hiroshige", "Gustav Klimt",
        "Edvard Munch", "Joaquín Sorolla", "Frederic Edwin Church", "Albert Bierstadt", "Thomas Cole", "John Singer Sargent",
        "Eugène Delacroix", "Nicolas Poussin", "Claude Lorrain", "Ivan Aivazovsky", "Vilhelm Hammershøi"
    )

    val COUNTS = listOf(12, 24, 48)
    val REFRESH_HOURS = listOf(6, 24, 72, 168)
    val SAVER_SECONDS = listOf(10, 30, 60, 300, 900)

    private const val API = "https://commons.wikimedia.org/w/api.php"
    private const val MIN_WIDTH = 1600
    private const val PAINTERS_PER_REFRESH = 6
    private val YEAR = Regex("\\b(\\d{3,4})\\b")
    private val TAGS = Regex("<[^>]*>")

    /** One painting found on Commons, before the filters. */
    private class Found(val id: Long, val file: String, val painter: String, val width: Int, val height: Int,
                         val thumb: String, val page: String, val year: Int?, val categories: String)

    /** Fetches paintings that match the filters. Returns an empty list (and sets [HeroRepo.lastResult]) when there are none. */
    fun fetch(prefs: Prefs): List<HeroRepo.Item> {
        val chosen = prefs.artArtists
        val fromMovements = prefs.artStyles.flatMap { s -> MOVEMENTS.firstOrNull { it.first == s }?.second ?: emptyList() }.distinct()
        val painters: List<String> = when {
            chosen.isNotEmpty() && fromMovements.isNotEmpty() -> chosen.filter { c -> fromMovements.any { it.equals(c, true) } }
            chosen.isNotEmpty() -> chosen
            fromMovements.isNotEmpty() -> fromMovements
            else -> DEFAULT_POOL
        }
        if (painters.isEmpty()) {
            HeroRepo.lastResult[HeroRepo.SRC_ART] = "None of the chosen painters belongs to the chosen movements"
            return emptyList()
        }
        val eras = prefs.artEras.mapNotNull { e -> ERAS.firstOrNull { it.label == e } }
        val words = prefs.artGenres.flatMap { g -> GENRES.firstOrNull { it.first == g }?.second ?: emptyList() }.map { it.lowercase() }
        val found = LinkedHashMap<Long, Found>()
        var reached = false
        for ((i, painter) in painters.shuffled().take(PAINTERS_PER_REFRESH).withIndex()) {
            if (i > 0) Thread.sleep(400)
            val (code, files) = category(painter)
            if (code == 200) reached = true
            val list = if (files.isEmpty() && code == 200) { Thread.sleep(400); search(painter) } else files
            for (f in list) if (!found.containsKey(f.id)) found[f.id] = f
        }
        if (!reached) {
            HeroRepo.lastResult[HeroRepo.SRC_ART] = "Could not reach commons.wikimedia.org"
            return emptyList()
        }
        val items = found.values.filter { f ->
            f.width >= MIN_WIDTH && f.width * 100 >= f.height * 115 &&
                (eras.isEmpty() || f.year != null && eras.any { f.year in it.from..it.to }) &&
                (words.isEmpty() || matches(f, words))
        }.shuffled().map { item(it) }
        if (items.isEmpty()) HeroRepo.lastResult[HeroRepo.SRC_ART] = "No paintings matched. Loosen the filters or add painters."
        return items
    }

    private fun matches(f: Found, words: List<String>): Boolean {
        val text = (title(f.file, f.painter) + " " + f.categories).lowercase()
        return words.any { text.contains(it) }
    }

    private fun item(f: Found): HeroRepo.Item {
        val artist = artist(f.file, f.painter)
        return HeroRepo.Item("art_${f.id}", title(f.file, f.painter), if (f.year != null) "$artist, ${f.year}" else artist,
            f.page, f.thumb, null, null, Slide.PHOTO)
    }

    /** "Claude Monet (French, Paris 1840-1926 Giverny) - Haystacks - Google Art Project.jpg" -> "Claude Monet". */
    private fun artist(file: String, fallback: String): String {
        val first = base(file).split(" - ").firstOrNull()?.substringBefore(" (")?.trim().orEmpty()
        return if (first.length in 3..40) first else fallback
    }

    /** ... -> "Haystacks". */
    private fun title(file: String, fallback: String): String {
        val parts = base(file).split(" - ")
        val t = if (parts.size >= 2) parts.drop(1).joinToString(" - ") else parts[0]
        return t.replace("Google Art Project", "").trim().trimEnd('-', ' ').ifEmpty { fallback }
    }

    private fun base(file: String) = file.removePrefix("File:").substringBeforeLast('.').replace('_', ' ')

    /** The "Google Art Project works by [painter]" category: 50 files from a random end of it. */
    private fun category(painter: String): Pair<Int, List<Found>> {
        val sort = if (Math.random() < 0.5) "sortkey" else "timestamp"
        val dir = if (Math.random() < 0.5) "asc" else "desc"
        return query(painter, "generator" to "categorymembers", "gcmtitle" to "Category:Google Art Project works by $painter",
            "gcmtype" to "file", "gcmlimit" to "50", "gcmsort" to sort, "gcmdir" to dir)
    }

    /** For a painter without a category of their own: files whose name has their name and "Google Art Project". */
    private fun search(painter: String): List<Found> {
        val last = painter.substringAfterLast(' ').lowercase()
        return query(painter, "generator" to "search", "gsrsearch" to "\"$painter\" \"Google Art Project\"",
            "gsrnamespace" to "6", "gsrlimit" to "50").second.filter { it.file.lowercase().contains(last) }
    }

    private fun query(painter: String, vararg gen: Pair<String, String>): Pair<Int, List<Found>> {
        val params = listOf("action" to "query", "format" to "json", "prop" to "imageinfo",
            "iiprop" to "url|size|mime|extmetadata", "iiurlwidth" to "1920",
            "iiextmetadatafilter" to "DateTimeOriginal|Categories") + gen.toList()
        val url = API + "?" + params.joinToString("&") { URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8") }
        val (code, body) = HeroRepo.get(url)
        if (code != 200 || body == null) return code to emptyList()
        val out = ArrayList<Found>()
        try {
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return code to out
            for (key in pages.keys()) {
                val p = pages.getJSONObject(key)
                val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = ii.optString("mime")
                if (mime != "image/jpeg" && mime != "image/png") continue
                val thumb = ii.optString("thumburl").ifEmpty { ii.optString("url") }
                if (!thumb.startsWith("https://")) continue
                val meta = ii.optJSONObject("extmetadata")
                val date = meta?.optJSONObject("DateTimeOriginal")?.optString("value").orEmpty().replace(TAGS, " ")
                val year = YEAR.find(date)?.groupValues?.get(1)?.toIntOrNull()
                val cats = meta?.optJSONObject("Categories")?.optString("value").orEmpty().replace('|', ' ')
                out.add(Found(p.optLong("pageid"), p.optString("title"), painter, ii.optInt("width"), ii.optInt("height"),
                    thumb, ii.optString("descriptionurl"), year, cats))
            }
        } catch (e: Throwable) { }
        return code to out
    }
}
