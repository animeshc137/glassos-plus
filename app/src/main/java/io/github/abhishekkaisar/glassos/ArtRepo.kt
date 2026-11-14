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
    private const val COLLECTION_CATEGORIES_PER_REFRESH = 4

    /**
     * Words in a file name or its Commons categories that mark nude artwork. A guess from text, so it can miss a nude
     * and can hide a clothed picture (bathers, Venus): it errs on the side of hiding.
     */
    private val NUDE = Regex("\\b(?:nude\\w*|nudit\\w*|naked|nu|nue|nus|nudo|nudi|desnud\\w*|nackt\\w*|naakt|akt|odalisque\\w*|bather\\w*|bathing|baigneu\\w+|badende\\w*|bagnant\\w*|venus|venere|aphrodite|afrodite|adam and eve|adam et [e\u00e8]ve|leda|dana[e\u00eb]|susann?a|susanne|suzanna|bacchanal\\w*|bacchante\\w*|nymph\\w*|naiad\\w*|dryad\\w*|three graces|trois gr[a\u00e2]ces|tre grazie|judgement of paris|jugement de p[a\u00e2]ris|actaeon|act[e\u00e9]on|callisto)\\b", RegexOption.IGNORE_CASE)

    /** A ready-made set of Commons categories of Indian art. */
    class ArtSet(val label: String, val categories: List<String>)

    /** Hindu religious art and the art of India and the Indian subcontinent, by Commons category. Mixed in with any painters chosen. */
    val INDIA = listOf(
        ArtSet("Hindu deities (all)", listOf("Paintings of Krishna", "Paintings of Ganesha", "Paintings of Shiva", "Paintings of Vishnu",
            "Paintings of Hanuman", "Paintings of Rama", "Paintings of Durga", "Paintings of Lakshmi", "Paintings of Hindu deities", "Hindu paintings")),
        ArtSet("Krishna", listOf("Paintings of Krishna")),
        ArtSet("Ganesha", listOf("Paintings of Ganesha")),
        ArtSet("Shiva", listOf("Paintings of Shiva")),
        ArtSet("Vishnu", listOf("Paintings of Vishnu")),
        ArtSet("Rama and Hanuman", listOf("Paintings of Rama", "Paintings of Hanuman")),
        ArtSet("Durga and Lakshmi", listOf("Paintings of Durga", "Paintings of Lakshmi")),
        ArtSet("Raja Ravi Varma", listOf("Raja Ravi Varma")),
        ArtSet("Kangra and Pahari miniatures", listOf("Kangra painting", "Pahari painting")),
        ArtSet("Rajput miniatures", listOf("Rajput painting")),
        ArtSet("Mughal miniatures", listOf("Mughal miniatures")),
        ArtSet("Bengal School and modern India", listOf("Paintings by Nandalal Bose", "Paintings by Abanindranath Tagore",
            "Paintings by Rabindranath Tagore", "Paintings by Amrita Sher-Gil", "Paintings by Jamini Roy")),
        ArtSet("Folk art (Pattachitra, Madhubani)", listOf("Pattachitra", "Madhubani painting")),
        ArtSet("Himalayan thangkas", listOf("Thangka")),
        ArtSet("Landscapes of India", listOf("Landscape paintings of India")),
        ArtSet("Paintings of India", listOf("Paintings of India", "Paintings from India"))
    )

    /** One painting found on Commons, before the filters. */
    private class Found(val id: Long, val file: String, val painter: String, val width: Int, val height: Int,
                         val thumb: String, val page: String, val year: Int?, val categories: String, val collection: Boolean)

    /** One set of filters (empty list: any). The wallpaper and the screensaver each have one. */
    class Filters(val genres: List<String>, val eras: List<String>, val styles: List<String>, val artists: List<String>,
                  val regions: List<String>, val hideNude: Boolean)

    fun wallpaperFilters(p: Prefs) = Filters(p.artGenres, p.artEras, p.artStyles, p.artArtists, p.artRegions, p.artHideNude)

    /** The screensaver's own filters, or the wallpaper's when "same filters" is on. */
    fun saverFilters(p: Prefs) = if (p.saverSameFilters) wallpaperFilters(p)
        else Filters(p.saverGenres, p.saverEras, p.saverStyles, p.saverArtists, p.saverRegions, p.saverHideNude)

    /**
     * Fetches paintings that match [filters]. Returns an empty list (and sets [HeroRepo.lastResult] for [src]) when there are none.
     */
    fun fetch(filters: Filters, src: String): List<HeroRepo.Item> {
        val chosen = filters.artists
        val fromMovements = filters.styles.flatMap { s -> MOVEMENTS.firstOrNull { it.first == s }?.second ?: emptyList() }.distinct()
        // The chosen India collections' categories, each with its collection's name.
        val regionSources = filters.regions.flatMap { r -> INDIA.firstOrNull { it.label == r }?.categories?.map { c -> r to c } ?: emptyList() }.distinct()
        val painters: List<String> = when {
            chosen.isNotEmpty() && fromMovements.isNotEmpty() -> chosen.filter { c -> fromMovements.any { it.equals(c, true) } }
            chosen.isNotEmpty() -> chosen
            fromMovements.isNotEmpty() -> fromMovements
            regionSources.isNotEmpty() -> emptyList()
            else -> DEFAULT_POOL
        }
        if (painters.isEmpty() && regionSources.isEmpty()) {
            HeroRepo.lastResult[src] = "None of the chosen painters belongs to the chosen movements"
            return emptyList()
        }
        val eras = filters.eras.mapNotNull { e -> ERAS.firstOrNull { it.label == e } }
        val words = filters.genres.flatMap { g -> GENRES.firstOrNull { it.first == g }?.second ?: emptyList() }.map { it.lowercase() }
        // Whole words only ("sea" is not "seated", "saint" is not "Sainte-Victoire"); a plural counts.
        val subject = if (words.isEmpty()) null
        else Regex("\\b(?:" + words.joinToString("|") { Regex.escape(it) } + ")(?:e?s)?\\b", RegexOption.IGNORE_CASE)
        // The screensaver can show a portrait painting fitted to the screen; the wallpaper needs landscape ones.
        val landscapeOnly = src != HeroRepo.SRC_ARTSAVER
        // Look everything up side by side: each answer takes a second or two, and they add up one after another.
        val jobs = ArrayList<() -> Pair<Int, List<Found>>>()
        for (painter in painters.shuffled().take(PAINTERS_PER_REFRESH)) {
            jobs.add {
                val (code, files) = category(painter)
                code to (if (files.isEmpty() && code == 200) search(painter) else files)
            }
        }
        for ((label, cat) in regionSources.shuffled().take(COLLECTION_CATEGORIES_PER_REFRESH)) {
            jobs.add { members(cat, label, true) }
        }
        val results = arrayOfNulls<Pair<Int, List<Found>>>(jobs.size)
        jobs.mapIndexed { i, job -> Thread { results[i] = job() }.also { t -> t.start() } }.forEach { t -> t.join() }
        val found = LinkedHashMap<Long, Found>()
        var reached = false
        var limited = false
        for (r in results) {
            if (r == null) continue
            if (r.first == 200) reached = true
            if (r.first == 429) limited = true
            for (f in r.second) if (!found.containsKey(f.id)) found[f.id] = f
        }
        if (!reached) {
            HeroRepo.lastResult[src] = if (limited) "Wikimedia is limiting this TV right now. Try again in a few minutes." else "Could not reach commons.wikimedia.org"
            return emptyList()
        }
        val items = found.values.filter { f ->
            shapeOk(f, landscapeOnly) &&
                (eras.isEmpty() || f.year != null && eras.any { f.year in it.from..it.to }) &&
                (subject == null || f.collection || subject.containsMatchIn(title(f.file, f.painter) + " " + f.categories)) &&
                !(filters.hideNude && NUDE.containsMatchIn(f.file + " " + f.categories))
        }.shuffled().map { item(it) }
        if (items.isEmpty()) HeroRepo.lastResult[src] = "No paintings matched. Loosen the filters, add painters or collections" +
            (if (filters.hideNude) ", or turn off Hide nude artwork." else ".")
        return items
    }

    /** Big enough, and the right shape: landscape for the wallpaper, any ordinary shape for the screensaver. */
    private fun shapeOk(f: Found, landscapeOnly: Boolean): Boolean {
        val minLong = if (f.collection) 1200 else MIN_WIDTH
        return if (landscapeOnly) f.width >= minLong && f.width * 100 >= f.height * 115
        else maxOf(f.width, f.height) >= minLong && f.width * 100 >= f.height * 50 && f.width * 100 <= f.height * 260
    }

    private fun item(f: Found): HeroRepo.Item {
        // A collection's files are not named "Artist - Title - Google Art Project": the name is the title, the collection the byline.
        val title = if (f.collection) base(f.file).take(90) else title(f.file, f.painter)
        val artist = if (f.collection) f.painter else artist(f.file, f.painter)
        return HeroRepo.Item("art_${f.id}", title, if (f.year != null) "$artist, ${f.year}" else artist,
            f.page, f.thumb, null, null, Slide.PHOTO)
    }

    /**
     * The file name split into its parts without "Google Art Project":
     * "Claude Monet (French, Paris 1840-1926 Giverny) - Haystacks - Google Art Project.jpg" -> [Claude Monet (...), Haystacks].
     */
    private fun parts(file: String): List<String> =
        base(file).split(" - ").map { it.replace("Google Art Project", "").trim().trim('-', ' ') }.filter { it.isNotEmpty() }

    /** "Claude Monet (French, ...) - Haystacks" -> "Claude Monet"; a name without an artist part gives [fallback]. */
    private fun artist(file: String, fallback: String): String {
        val p = parts(file)
        val first = if (p.size >= 2) p[0].substringBefore(" (").trim() else ""
        return if (first.length in 3..40) first else fallback
    }

    /** ... -> "Haystacks"; "The White Horse by John Constable - Google Art Project" -> the whole name. */
    private fun title(file: String, fallback: String): String {
        val p = parts(file)
        return (if (p.size >= 2) p.drop(1).joinToString(" - ") else p.firstOrNull().orEmpty()).ifEmpty { fallback }
    }

    private fun base(file: String) = file.removePrefix("File:").substringBeforeLast('.').replace('_', ' ')

    /** The "Google Art Project works by [painter]" category: 50 files from a random end of it. */
    private fun category(painter: String): Pair<Int, List<Found>> = members("Google Art Project works by $painter", painter, false)

    /** Up to 50 files of the Commons category [category] from a random end of it, labelled [label]. */
    private fun members(category: String, label: String, collection: Boolean): Pair<Int, List<Found>> {
        val sort = if (Math.random() < 0.5) "sortkey" else "timestamp"
        val dir = if (Math.random() < 0.5) "asc" else "desc"
        return query(label, collection, "generator" to "categorymembers", "gcmtitle" to "Category:$category",
            "gcmtype" to "file", "gcmlimit" to "50", "gcmsort" to sort, "gcmdir" to dir)
    }

    /** For a painter without a category of their own: files whose name has their name and "Google Art Project". */
    private fun search(painter: String): List<Found> {
        val last = painter.substringAfterLast(' ').lowercase()
        return query(painter, false, "generator" to "search", "gsrsearch" to "\"$painter\" \"Google Art Project\"",
            "gsrnamespace" to "6", "gsrlimit" to "50").second.filter { it.file.lowercase().contains(last) }
    }

    private fun query(label: String, collection: Boolean, vararg gen: Pair<String, String>): Pair<Int, List<Found>> {
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
                out.add(Found(p.optLong("pageid"), p.optString("title"), label, ii.optInt("width"), ii.optInt("height"),
                    thumb, ii.optString("descriptionurl"), year, cats, collection))
            }
        } catch (e: Throwable) { }
        return code to out
    }
}
