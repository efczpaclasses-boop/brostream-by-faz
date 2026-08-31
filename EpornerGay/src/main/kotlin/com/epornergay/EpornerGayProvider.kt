package com.epornergay

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.net.URLEncoder

class EpornerGayProvider : MainAPI() {
    override var mainUrl = "https://gay0day.com"
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
    private enum class Source { GAY0DAY, EPORNER, BOYFRIEND, PORNHUB, TRENDY, PORNONE, FXGGXT }

    data class ItemData(
        val source: String = "",
        val url: String = "",
        val title: String = "",
        val poster: String? = null,
        val keywords: String = "",
    )

    override val mainPage = mainPageOf(
        "G0|/categories/gay/" to "🔥 Fresh Gay Men",
        "G0|/categories/hunk/" to "🔥 Hot Guys & Hunks",
        "G0|/categories/amateur/" to "🏠 Amateur Men",
        "G0|/categories/homemade/" to "🎥 Homemade — Non-Studio",
        "G0|/categories/muscle/" to "💪 Muscle Men",
        "G0|/categories/jock/" to "Jocks",
        "G0|/categories/daddy/" to "🐻 Daddies",
        "G0|/categories/bear/" to "Bears",
        "G0|/categories/hairy/" to "Hairy Men",
        "G0|/categories/straight-guys/" to "Straight & Curious Guys",
        "G0|/categories/big-cock/" to "Big Dick",
        "G0|/categories/bareback/" to "Bareback",
        "G0|/categories/group-sex/" to "Group & Orgies",
        "G0|/categories/solo-male/" to "Solo Men",
        "G0|/categories/outdoor/" to "Outdoor & Public",
        "G0|/categories/brazilian/" to "Brazilian Men",
        "G0|/categories/latino/" to "Latino Men",
        "G0|/categories/indian/" to "Indian Men",
        "G0|/categories/blowjob/" to "Hottest Blowjobs",
        "G0|/categories/compilation/" to "Compilations",
        "G0|/categories/cumshot/" to "Cumshots",
        "G0|/categories/rough-sex/" to "Rough & Hardcore",
        "G0|/categories/glory-hole/" to "Gloryholes",
        "G0|/categories/pov/" to "POV",
        "G0|/categories/massage/" to "Massage",
        "G0|/categories/twink/" to "Twinks",
        "G0|/categories/interracial/" to "Interracial Men",
        "G0|/categories/asian/" to "Asian Men",
        "G0|/categories/mature/" to "Mature Men",
        "G0|/categories/uncut/" to "Uncut Men",
        "G0|/categories/hd-porn/" to "HD Gay Men",
        "G0|/categories/handjob/" to "Handjobs",
        "G0|/categories/rimming/" to "Rimming",
        "G0|/categories/threesome/" to "Threesomes",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data.startsWith("G0|")) {
            val items = gay0day(page, request.data.removePrefix("G0|"))
                .filter(::isMenOnly).distinctBy(::dedupeKey)
            return newHomePageResponse(
                HomePageList(request.name, items.map { it.toSearchResponse() }, true),
                hasNext = items.isNotEmpty(),
            )
        }
        if (request.data.startsWith("EP|")) {
            val parts = request.data.split('|', limit = 3)
            val items = eporner(page, parts.getOrElse(1) { "latest" }, parts.getOrElse(2) { "gay" })
                .filter(::isMenOnly).distinctBy(::dedupeKey)
            return newHomePageResponse(
                HomePageList(request.name, items.map { it.toSearchResponse() }, true),
                hasNext = items.isNotEmpty(),
            )
        }
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
        if (request.data.startsWith("BFK|")) {
            val keywords = request.data.removePrefix("BFK|").split(',')
                .map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val items = boyfriendKeyword(page, keywords)
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

    private suspend fun gay0day(page: Int, path: String): List<ItemData> {
        val url = if (page <= 1) "$mainUrl$path" else "$mainUrl${path.trimEnd('/')}/$page/"
        return app.get(url, headers = headers, timeout = 30).document.select("div.item").mapNotNull { el ->
            val a = el.selectFirst("a[href][title]") ?: return@mapNotNull null
            val href = absolute(a.attr("href"), mainUrl) ?: return@mapNotNull null
            if (!href.contains("/videos/")) return@mapNotNull null
            val title = a.attr("title").ifBlank { a.text().trim() }
            val image = el.selectFirst("img.thumb, img")
            val poster = image?.attr("data-src").orEmpty().ifBlank { image?.attr("src").orEmpty() }
            if (title.isBlank()) null else ItemData(
                Source.GAY0DAY.name, href, title, absolute(poster, mainUrl), "gay men"
            )
        }
    }

    private suspend fun aggregate(page: Int, order: String, query: String): List<ItemData> {
        // Only BoyfriendTV is used for visible aggregate rows. It is a dedicated
        // gay-men catalogue; the broader feeds occasionally leak female content.
        val sources = listOf(Source.BOYFRIEND)
        val lists = sources.amap { source ->
            runCatching {
                when (source) {
                    Source.GAY0DAY -> gay0day(
                        page,
                        if (query == "all") "/categories/gay/"
                        else "/search/${query.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}/"
                    )
                    Source.EPORNER -> eporner(page, order, query)
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

    private suspend fun eporner(page: Int, order: String, query: String): List<ItemData> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.eporner.com/api/v2/video/search/?query=$encoded" +
            "&per_page=40&page=$page&order=$order&gay=1&lq=1"
        val response = mapper.readValue(app.get(url, headers = headers, timeout = 30).text, EpornerSearchResponse::class.java)
        return response.videos.mapNotNull { video ->
            if (video.url.isBlank() || video.title.isBlank()) null else ItemData(
                source = Source.EPORNER.name,
                url = video.url,
                title = video.title,
                poster = video.defaultThumb?.src,
                keywords = video.keywords,
            )
        }
    }

    private suspend fun boyfriendTv(page: Int, order: String, query: String): List<ItemData> {
        val url = if (query.startsWith("/")) {
            val path = query.substringBefore("?")
            val existingQuery = query.substringAfter("?", "")
            val separator = if (existingQuery.isBlank()) "?" else "?${existingQuery}&"
            "$mainUrl${path}${separator}page=$page"
        } else if (query != "all") {
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

    private suspend fun boyfriendKeyword(page: Int, keywords: List<String>): List<ItemData> {
        val first = ((page - 1).coerceAtLeast(0) * 5) + 1
        val pages = (first until first + 5).toList().amap { sourcePage ->
            runCatching { boyfriendTv(sourcePage, "latest", "all") }.getOrDefault(emptyList())
        }.flatten()
        return pages.filter { item ->
            val title = item.title.lowercase()
            keywords.any(title::contains)
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
        val slug = query.lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (slug.isBlank()) return emptyList()
        return gay0day(1, "/search/$slug/").filter(::isMenOnly).distinctBy(::dedupeKey)
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
            Source.GAY0DAY.name -> "G0"
            Source.EPORNER.name -> "EP"
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
            Source.GAY0DAY -> loadGay0Day(item.url, callback)
            Source.EPORNER -> loadEporner(item.url, callback)
            Source.BOYFRIEND -> loadBoyfriendTv(item.url, callback)
            Source.PORNHUB -> loadPornHub(item.url, callback)
            Source.TRENDY -> loadSimpleVideo(item, "source", callback)
            Source.PORNONE -> loadSimpleVideo(item, "#pornone-video-player source", callback)
            Source.FXGGXT -> loadFxggxt(item.url, subtitleCallback, callback)
            null -> false
        }
    }

    private suspend fun loadGay0Day(url: String, callback: (ExtractorLink) -> Unit): Boolean {
        val page = app.get(url, headers = headers, timeout = 30)
        var emitted = false
        page.document.select("video source[src], source[type*=video][src]").forEach { source ->
            val stream = absolute(source.attr("src"), url) ?: return@forEach
            val label = source.attr("label").ifBlank {
                Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE).find(stream)?.groupValues?.get(1) ?: "HD"
            }
            emitLink("Gay0Day", label, stream, url, stream.contains(".m3u8"), callback)
            emitted = true
        }
        if (!emitted) {
            val stream = Regex("\\\"contentUrl\\\"\\s*:\\s*\\\"([^\\\"]+)")
                .find(page.text)?.groupValues?.get(1)?.replace("\\/", "/")
            if (!stream.isNullOrBlank()) {
                emitLink("Gay0Day", "HD", stream, url, stream.contains(".m3u8"), callback)
                emitted = true
            }
        }
        return emitted
    }

    private suspend fun loadEporner(url: String, callback: (ExtractorLink) -> Unit): Boolean {
        val page = app.get(url, headers = headers, timeout = 30).text
        val videoId = Regex("EP\\.video\\.player\\.vid\\s*=\\s*'([^']+)'")
            .find(page)?.groupValues?.get(1) ?: return false
        val hash = Regex("EP\\.video\\.player\\.hash\\s*=\\s*'([^']+)'")
            .find(page)?.groupValues?.get(1) ?: return false
        val xhr = "https://www.eporner.com/xhr/video/$videoId?hash=${base36(hash)}"
        val sources = JSONObject(app.get(xhr, referer = url, headers = headers).text)
            .optJSONObject("sources")?.optJSONObject("mp4") ?: return false
        var emitted = false
        val qualities = sources.keys()
        while (qualities.hasNext()) {
            val source = sources.optJSONObject(qualities.next()) ?: continue
            val stream = source.optString("src")
            if (!stream.startsWith("http")) continue
            emitLink("Eporner", source.optString("labelShort", "HD"), stream, url, false, callback)
            emitted = true
        }
        return emitted
    }

    private fun base36(hash: String): String {
        require(hash.length >= 32)
        return (0 until 4).joinToString("") { index ->
            BigInteger(hash.substring(index * 8, index * 8 + 8), 16).toString(36)
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
        val text = " ${item.title.lowercase()} ${item.keywords.lowercase()} "
        val excluded = listOf(
            "lesbian", " girl", "girls", "woman", "women", "female", "milf", "mom ", "mommy",
            "mother", "wife", "wives", "daughter", "sister", "girlfriend", "bride", "babe",
            "bitch", "chick", "lady", "ladies", "pussy", "vagina", "clit", "tits", "boobs",
            "breasts", "pregnant", "shemale", "trans", "tranny", "futa", "straight couple",
            "stepmom", "step mom", "stepdaughter", "step daughter", "schoolgirl", "school girl",
            "cougar", "granny", "boy and girl", "guy and girl", "man and woman",
        )
        if (item.url.isBlank() || item.title.isBlank() || excluded.any(text::contains)) return false
        if (item.source == Source.GAY0DAY.name) return true
        val maleSignals = listOf(
            " man", "men ", " male", " guy", "guys", " boy", "boys", " gay", " cock", "dick",
            " daddy", "daddies", " bear", " cub", " hunk", " stud", " jock", " twink", "bro ",
            " brother", "him ", " his ", " he ", "jerk", "wank", "masturbat",
        )
        return maleSignals.any(text::contains)
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
