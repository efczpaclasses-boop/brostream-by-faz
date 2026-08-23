package com.epornergay

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

class EpornerGayProvider : MainAPI() {
    override var mainUrl = "https://www.boyfriendtv.com"
    override var name = "BroStream by Faz"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val mapper = jacksonObjectMapper()
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private val headers = mapOf("User-Agent" to userAgent, "Accept" to "text/html,application/xhtml+xml")
    private val pornHubCookies = mapOf("hasVisited" to "1", "accessAgeDisclaimerPH" to "1", "platform" to "pc")

    private enum class Feed { FRESH, AMATEUR, TOP, LONG, BOYFRIEND, TRENDY, PORNONE }
    private enum class Source { BOYFRIEND, PORNHUB, TRENDY, PORNONE, FXGGXT }

    data class ItemData(
        val source: String = "",
        val url: String = "",
        val title: String = "",
        val poster: String? = null,
    )

    override val mainPage = mainPageOf(
        "FX|/?s=muscle+daddy+bear+gay+men" to "🔥 Manly Men — New",
        "FX|/tag/amateur-gay-porn/" to "🏠 Real Amateur & Homemade",
        "FX|/?s=daddy+bear+hairy+gay+men" to "🐻 Daddies, Bears & Hairy Men",
        "FX|/?s=muscle+jock+hunk+gay+men" to "💪 Muscle & Jocks",
        "FX|/tag/straight-guys-gay-porn/" to "Straight Guys — Gay Only",
        "FX|/tag/big-dick-gay-porn/" to "Big Dick",
        "FX|/tag/bareback-gay-porn/" to "Bareback",
        "FX|/tag/group-gay-porn/" to "Group & Orgies",
        "FX|/category/gay-only-fans/" to "🎥 Creator-Made & OnlyFans",
        "FX|/?s=solo" to "Solo Men — Non-Studio",
        "FX|/?s=homemade" to "Real Couples — Non-Studio",
        "FX|/?s=outdoor" to "Outdoor & Public — Non-Studio",
        Feed.FRESH.name to "Fresh Gay Men — All Sources",
        Feed.AMATEUR.name to "More Amateur Men",
        "FX|/?s=brazilian+gay+men" to "Brazilian Men",
        "FX|/?s=latino+gay+men" to "Latino Men",
        "FX|/?s=indian+gay+men" to "Indian Men",
        "FX|/?s=best+gay+blowjob" to "Hottest Blowjobs",
        "FX|/?s=gay+cum+compilation" to "Cum Compilations",
        "FX|/?s=rough+gay+blowjob" to "Rough Blowjobs",
        "FX|/?s=loud+moaning+gay+blowjob" to "Loud Moaning Blowjobs",
        Feed.TOP.name to "Popular Gay Men",
        Feed.LONG.name to "Long Gay Videos",
        Feed.BOYFRIEND.name to "BoyfriendTV",
        Feed.TRENDY.name to "TrendyPorn Gay",
        Feed.PORNONE.name to "PornOne Gay",
        "FX|/?filter=latest/" to "FXGGXT — Latest Gay",
        "FX|/tag/hunk-gay-porn-videos/" to "Hunks",
        "FX|/tag/interracial-gay-porn/" to "Interracial",
        "FX|/tag/muscle-gay-porn/" to "Muscle",
        "FX|/tag/twink-gay-porn/" to "Twinks",
        "FX|/category/adulttime/" to "AdultTime",
        "FX|/category/asgmax/" to "ASGmax",
        "FX|/category/?s=bareback%2B" to "Bareback+",
        "FX|/category/bel-ami/" to "Bel Ami",
        "FX|/category/breederbros/" to "Breeder Bros",
        "FX|/category/clubbangboys/" to "Club Bang Boys",
        "FX|/category/cocksuremen/" to "Cock Sure Men",
        "FX|/category/cocky-boys/" to "Cocky Boys",
        "FX|/category/corbin-fisher/" to "Corbin Fisher",
        "FX|/category/creamybros/" to "Creamy Bros",
        "FX|/category/cumdumpsluts/" to "Cumdump Sluts",
        "FX|/category/ericvideos/" to "Eric Videos",
        "FX|/category/facedownassup/" to "Face Down Ass Up",
        "FX|/category/fraternity-x/" to "Fraternity X",
        "FX|/category/just-for-fans/" to "JustForFans",
        "FX|/category/kristenbjorn/" to "Kristen Bjorn",
        "FX|/category/falcon-studios/" to "Falcon Studios",
        "FX|/category/gay-only-fans/" to "Gay OnlyFans",
        "FX|/category/treasure-island-media/" to "Treasure Island Media",
        "FX|/category/slamrush/" to "Slam Rush",
        "FX|/category/seehimfuck/" to "See Him Fuck",
        "FX|/category/voyr/" to "VOYR",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data.startsWith("FX|")) {
            val items = fxggxt(page, request.data.removePrefix("FX|"))
                .filter(::isMenOnly).distinctBy(::dedupeKey)
            return newHomePageResponse(
                HomePageList(request.name, items.map { it.toSearchResponse() }, true),
                hasNext = items.isNotEmpty(),
            )
        }
        if (request.data.startsWith("BF|")) {
            val items = boyfriendTv(page, "latest", request.data.removePrefix("BF|"))
                .filter(::isMenOnly).distinctBy(::dedupeKey)
            return newHomePageResponse(
                HomePageList(request.name, items.map { it.toSearchResponse() }, true),
                hasNext = items.isNotEmpty(),
            )
        }
        val feed = runCatching { Feed.valueOf(request.data) }.getOrDefault(Feed.FRESH)
        val items = when (feed) {
            Feed.FRESH -> aggregate(page, "latest", "all")
            Feed.AMATEUR -> aggregate(page, "latest", "amateur")
            Feed.TOP -> aggregate(page, "top-rated", "all")
            Feed.LONG -> aggregate(page, "longest", "all")
            Feed.BOYFRIEND -> boyfriendTv(page, "latest", "all")
            Feed.TRENDY -> trendy(page)
            Feed.PORNONE -> pornOne(page)
        }.filter(::isMenOnly).distinctBy(::dedupeKey)

        return newHomePageResponse(
            HomePageList(request.name, items.map { it.toSearchResponse() }, true),
            hasNext = items.isNotEmpty(),
        )
    }

    private suspend fun aggregate(page: Int, order: String, query: String): List<ItemData> {
        val sources = when {
            query != "all" -> listOf(Source.BOYFRIEND, Source.FXGGXT)
            order != "latest" -> listOf(Source.BOYFRIEND, Source.FXGGXT)
            else -> listOf(Source.BOYFRIEND, Source.FXGGXT, Source.TRENDY, Source.PORNONE)
        }
        val lists = sources.amap { source ->
            runCatching {
                when (source) {
                    Source.BOYFRIEND -> boyfriendTv(page, order, query)
                    Source.PORNHUB -> pornHub(page, order, query)
                    Source.TRENDY -> trendy(page, query)
                    Source.PORNONE -> pornOne(page, query)
                    Source.FXGGXT -> fxggxt(
                        page,
                        if (query == "all") "/?filter=latest/"
                        else "/?s=${URLEncoder.encode("gay men $query", "UTF-8")}"
                    )
                }
            }.getOrDefault(emptyList())
        }
        return interleave(lists)
    }

    private suspend fun boyfriendTv(page: Int, order: String, query: String): List<ItemData> {
        val url = if (query != "all") {
            "$mainUrl/search/?q=${URLEncoder.encode(query, "UTF-8")}&page=$page"
        } else {
            val sort = when (order) {
                "top-rated" -> "best-videos/"
                "longest" -> "?sort=longest&"
                else -> "?sort=mr&"
            }
            if (sort.endsWith("/")) "$mainUrl/$sort?page=$page" else "$mainUrl/$sort" + "page=$page"
        }
        return app.get(url, headers = headers).document.select("li.mtile-x7").mapNotNull { el ->
            val a = el.selectFirst("a.mtile-x7__title[href], a[href*=/videos/][title]")
                ?: return@mapNotNull null
            val title = a.attr("title").ifBlank { a.text().trim() }
            val href = absolute(a.attr("href"), mainUrl) ?: return@mapNotNull null
            val poster = el.selectFirst("img[src]")?.attr("src")
            if (title.isBlank()) null else ItemData(Source.BOYFRIEND.name, href, title, poster)
        }
    }

    private suspend fun pornHub(page: Int, order: String, query: String): List<ItemData> {
        val sort = when (order) {
            "top-rated" -> "o=tr&t=w"
            "longest" -> "o=lg"
            else -> "o=cm"
        }
        val url = if (query == "all") {
            "https://www.pornhub.com/gay/video?$sort&hd=1&ajax=1&page=$page"
        } else if (query == "amateur") {
            "https://www.pornhub.com/gay/video?p=homemade&o=cm&hd=1&ajax=1&page=$page"
        } else {
            val q = URLEncoder.encode(query, "UTF-8")
            "https://www.pornhub.com/gay/video/search?search=$q&$sort&ajax=1&page=$page"
        }
        val doc = app.get(url, cookies = pornHubCookies, headers = headers).document
        return doc.select("li.pcVideoListItem, li.videoBox, ul.videos li.videoBox").mapNotNull { el ->
            val a = el.selectFirst("a[href][title], a[href][data-title], a.js-link[href]") ?: return@mapNotNull null
            val href = absolute(a.attr("href"), "https://www.pornhub.com") ?: return@mapNotNull null
            val title = a.attr("title").ifBlank { a.attr("data-title") }.ifBlank { a.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            val img = el.selectFirst("img[data-thumb_url], img[data-mediumthumb], img[data-src], img[src]")
            val poster = img?.attr("data-thumb_url").orEmpty()
                .ifBlank { img?.attr("data-mediumthumb").orEmpty() }
                .ifBlank { img?.attr("data-src").orEmpty() }
                .ifBlank { img?.attr("src").orEmpty() }
            ItemData(Source.PORNHUB.name, href, title, absolute(poster, "https://www.pornhub.com"))
        }
    }

    private suspend fun trendy(page: Int, query: String = "all"): List<ItemData> {
        val url = if (query == "all") {
            "https://www.trendyporn.com/channels/21/gay/page$page.html"
        } else {
            "https://www.trendyporn.com/search/${URLEncoder.encode(query, "UTF-8")}/page$page.html"
        }
        return app.get(url, headers = headers).document.select("div.well-sm").mapNotNull { el ->
            val a = el.selectFirst("a[href][title]") ?: return@mapNotNull null
            val title = a.attr("title").ifBlank { a.text() }
            val href = absolute(a.attr("href"), "https://www.trendyporn.com") ?: return@mapNotNull null
            val images = el.select("img")
            val poster = images.attr("data-original").ifBlank { images.attr("src") }
            ItemData(Source.TRENDY.name, href, title, absolute(poster, "https://www.trendyporn.com"))
        }
    }

    private suspend fun pornOne(page: Int, query: String = "all"): List<ItemData> {
        val url = if (query == "all") {
            "https://pornone.com/gay/$page"
        } else {
            "https://pornone.com/gay/search?q=${URLEncoder.encode(query, "UTF-8")}&page=$page"
        }
        return app.get(url, headers = headers, timeout = 30).document
            .select(".popbop.vidLinkFX").mapNotNull { el ->
                val title = el.selectFirst(".videotitle")?.text()?.trim().orEmpty()
                val href = absolute(el.attr("href"), "https://pornone.com") ?: return@mapNotNull null
                val image = el.selectFirst(".imgvideo")
                val poster = image?.attr("data-src").orEmpty().ifBlank { image?.attr("src").orEmpty() }
                if (title.isBlank()) null else ItemData(
                    Source.PORNONE.name, href, title, absolute(poster, "https://pornone.com")
                )
            }
    }

    private suspend fun fxggxt(page: Int, path: String): List<ItemData> {
        val base = "https://fxggxt.com"
        val firstUrl = "$base$path"
        val url = when {
            page <= 1 -> firstUrl
            path.contains("?s=") -> "$base/page/$page/?s=${path.substringAfter("?s=")}"
            else -> firstUrl.trimEnd('/') + "/page/$page/"
        }
        return app.get(url, headers = headers, timeout = 30).document
            .select("article.loop-video.thumb-block").mapNotNull { el ->
                val a = el.selectFirst("a[href]") ?: return@mapNotNull null
                val href = absolute(a.attr("href"), base) ?: return@mapNotNull null
                val title = a.selectFirst("header.entry-header span")?.text()?.trim()
                    .orEmpty().ifBlank { a.attr("title").ifBlank { a.text().trim() } }
                val image = a.selectFirst(".post-thumbnail-container img, img")
                val poster = image?.attr("data-src").orEmpty().ifBlank { image?.attr("src").orEmpty() }
                if (title.isBlank()) null else ItemData(Source.FXGGXT.name, href, title, poster)
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return aggregate(1, "latest", query).filter(::isMenOnly).distinctBy(::dedupeKey)
            .map { it.toSearchResponse() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    private fun ItemData.toSearchResponse(): SearchResponse {
        return newMovieSearchResponse("[$sourceLabel] $title", toJson(), TvType.NSFW) {
            posterUrl = poster
            quality = SearchQuality.HD
        }
    }

    private val ItemData.sourceLabel: String
        get() = when (source) {
            Source.BOYFRIEND.name -> "BF"
            Source.PORNHUB.name -> "PH"
            Source.TRENDY.name -> "TP"
            Source.PORNONE.name -> "PO"
            Source.FXGGXT.name -> "FX"
            else -> source
        }

    private fun ItemData.toJson(): String = mapper.writeValueAsString(this)
    private fun parseItem(value: String): ItemData? = runCatching { mapper.readValue(value, ItemData::class.java) }.getOrNull()

    override suspend fun load(url: String): LoadResponse? {
        val item = parseItem(url) ?: return null
        val cookies = if (item.source == Source.PORNHUB.name) pornHubCookies else emptyMap()
        val document = app.get(item.url, headers = headers, cookies = cookies).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.substringBefore(" - ")?.trim().orEmpty().ifBlank { item.title }
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            .ifBlank { item.poster.orEmpty() }
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
        val tags = document.select("a[data-label=Category], a[href*=category], .tags a")
            .map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            posterUrl = poster
            plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val item = parseItem(data) ?: return false
        return when (runCatching { Source.valueOf(item.source) }.getOrNull()) {
            Source.BOYFRIEND -> loadBoyfriendTv(item.url, callback)
            Source.PORNHUB -> loadPornHub(item.url, callback)
            Source.TRENDY -> loadSimpleVideo(item, "source", callback)
            Source.PORNONE -> loadSimpleVideo(item, "#pornone-video-player source", callback)
            Source.FXGGXT -> loadFxggxt(item.url, subtitleCallback, callback)
            null -> false
        }
    }

    private suspend fun loadFxggxt(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val doc = app.get(url, headers = headers, timeout = 30).document
        val links = doc.select(
            "iframe[src], .video-player iframe[src], .entry-content iframe[src], " +
                "a[href*=dood], a[href*=voe], a[href*=vide0], a[href*=d-s.io]"
        ).mapNotNull { el ->
            absolute(el.attr("src").ifBlank { el.attr("href") }, url)
        }.distinct()
        links.amap { link ->
            runCatching { loadExtractor(link, url, subtitleCallback, callback) }
        }
        return links.isNotEmpty()
    }

    private suspend fun loadBoyfriendTv(url: String, callback: (ExtractorLink) -> Unit): Boolean {
        val page = app.get(url, headers = headers).text
        val embed = Regex("""\"embedUrl\"\s*:\s*\"([^\"]+)""").find(page)?.groupValues?.get(1)
            ?.replace("\\/", "/") ?: return false
        val embedPage = app.get(embed, referer = url, headers = headers).text
        val stream = Regex("""\"hlsAuto\"\s*:\s*\"([^\"]+)""").find(embedPage)?.groupValues?.get(1)
            ?.replace("\\/", "/")?.replace("_TPL_", "master") ?: return false
        emitLink("BoyfriendTV", "Auto", stream, embed, true, callback)
        return true
    }

    private suspend fun loadPornHub(url: String, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(url, headers = headers, cookies = pornHubCookies).document
        val scripts = doc.select("script").joinToString("\n") { it.data() }
        val raw = Regex("\"mediaDefinitions\"\\s*:\\s*(\\[.*?\\])", RegexOption.DOT_MATCHES_ALL)
            .find(scripts)?.groupValues?.get(1) ?: return false
        val definitions = JSONArray(raw)
        var emitted = false
        for (index in 0 until definitions.length()) {
            val obj = definitions.optJSONObject(index) ?: continue
            var stream = obj.optString("videoUrl")
            if (stream.endsWith(".json")) {
                stream = runCatching { JSONObject(app.get(stream, headers = headers).text).optString("videoUrl") }
                    .getOrDefault(stream)
            }
            if (stream.startsWith("http")) {
                emitLink("Pornhub", obj.optString("quality", "HD"), stream, url, stream.contains(".m3u8"), callback)
                emitted = true
            }
        }
        return emitted
    }

    private suspend fun loadSimpleVideo(item: ItemData, selector: String, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(item.url, headers = headers).document
        var emitted = false
        doc.select(selector).forEach { source ->
            val stream = source.attr("src")
            if (stream.startsWith("http")) {
                val label = source.attr("res").ifBlank { source.attr("label") }.ifBlank { "HD" }
                emitLink(item.sourceLabel, label, stream, item.url, stream.contains(".m3u8"), callback)
                emitted = true
            }
        }
        return emitted
    }

    private suspend fun emitLink(
        source: String,
        label: String,
        url: String,
        referer: String,
        isM3u8: Boolean,
        callback: (ExtractorLink) -> Unit,
    ) {
        val type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
        callback(newExtractorLink(source, "$source $label", url, type) {
            this.referer = referer
            quality = Regex("(\\d+)[pP]?").find(label)?.groupValues?.get(1)?.toIntOrNull()
                ?: Qualities.Unknown.value
            headers = mapOf("User-Agent" to userAgent)
        })
    }

    private fun isMenOnly(item: ItemData): Boolean {
        val text = " ${item.title.lowercase()} "
        val excluded = listOf(
            "lesbian", " girl", "girls", "woman", "women", "female", "milf", "mom ", "mommy",
            "mother", "wife", "wives", "daughter", "sister", "girlfriend", "bride", "babe",
            "bitch", "chick", "lady", "ladies", "pussy", "vagina", "clit", "tits", "boobs",
            "breasts", "pregnant", "shemale", "trans", "tranny", "futa", "straight couple",
        )
        if (item.url.isBlank() || item.title.isBlank() || excluded.any(text::contains)) return false

        return true
    }

    private fun dedupeKey(item: ItemData): String {
        return item.title.lowercase().replace(Regex("[^a-z0-9]"), "").take(80)
    }

    private fun interleave(lists: List<List<ItemData>>): List<ItemData> {
        val output = mutableListOf<ItemData>()
        val largest = lists.maxOfOrNull { it.size } ?: 0
        for (index in 0 until largest) {
            lists.forEach { list -> list.getOrNull(index)?.let(output::add) }
        }
        return output
    }

    private fun absolute(value: String?, base: String): String? {
        if (value.isNullOrBlank()) return null
        return runCatching { URI(base).resolve(value).toString() }.getOrNull()
    }
}
