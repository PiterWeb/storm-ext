package com.stormunblessed

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*
import kotlin.collections.map

class AnimeAV1Provider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Película")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getDubStatus(title: String): DubStatus {
//            return if (title.contains("Latino") || title.contains("Castellano"))
//                DubStatus.Dubbed
//            else DubStatus.Subbed
            return DubStatus.Subbed
        }
    }

    override var mainUrl = "https://animeav1.com"
    val cdnUrl = "https://cdn.animeav1.com"
    override var name = "AnimeAV1"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/catalogo?category=pelicula&order=latest_released", "Películas"),
            Pair("$mainUrl/catalogo?category=tv-anime", "Animes"),
            Pair("$mainUrl/catalogo?&status=emision&order=score", "En emision"),
        )
        val items = ArrayList<HomePageList>()
        val isHorizontal = true
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl).document.select("main section:nth-child(1) div.grid article").mapNotNull {
                    val title = it.selectFirst("header div")?.text() ?: return@mapNotNull null
                    val poster = it.selectFirst("div figure img")?.attr("src") ?: return@mapNotNull null
                    val epRegex = Regex("(/(\\d+)$)")
                    val url = it.selectFirst("a")?.attr("href")?.replace(epRegex, "") ?: return@mapNotNull null
                    val epNum =
                        it.selectFirst("div div div span")?.text()?.toIntOrNull()
                    newAnimeSearchResponse(title, url) {
                        this.posterUrl = fixUrl(poster)
                        addDubStatus(getDubStatus(title), epNum)
                    }
                }, isHorizontal)
        )

        urls.amap { (url, name) ->
            val doc = app.get(url).document
            val home = doc.select("main section div.grid article").mapNotNull {
                val title = it.selectFirst("header h3")?.text() ?: return@mapNotNull null
                val poster = it.selectFirst("div figure img")?.attr("src") ?: return@mapNotNull null
                newAnimeSearchResponse(
                    title,
                    fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
                ) {
                    this.posterUrl = fixUrl(poster)
                    addDubStatus(getDubStatus(title))
                }
            }

            items.add(HomePageList(name, home))
        }
        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    data class SearchObject(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("slug") val slug: String
    )

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/api/search",
            data = mapOf(Pair("query", query))
        ).text
        val json = parseJson<List<SearchObject>>(response)
        return json.map { searchr ->
            val title = searchr.title
            val href = "$mainUrl/media/${searchr.slug}"
            val image = "$cdnUrl/covers/${searchr.id}.jpg"
            newAnimeSearchResponse(title, href) {
                this.posterUrl = fixUrl(image)
                addDubStatus(getDubStatus(title))
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/catalogo?search=$query").document
        val sss = doc.select("main section div.grid article").map { ll ->
            val title = ll.selectFirst("header h3")?.text() ?: ""
            val image = ll.selectFirst("div figure img")?.attr("src") ?: ""
            val href = ll.selectFirst("a")?.attr("href") ?: ""
            newAnimeSearchResponse(title, href){
                this.posterUrl = image
                addDubStatus(getDubStatus(title))
            }
        }
        return sss
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val episodes = ArrayList<Episode>()
        val title = doc.selectFirst("main article h1")!!.text()
        val poster = doc.selectFirst("main article figure img")?.attr("src")!!
        val description = doc.selectFirst("main article div.entry p")?.text()
        val type = doc.selectFirst("main article div.text-sm span")?.text() ?: ""
        val status = when (doc.selectFirst("main article div.text-sm span:nth-child(7)")?.text()) {
            "En emisión" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val genre = doc.select("main article header a.btn-line-o")
            .map { it?.text()?.trim().toString() }

        val epRegex = Regex("(/(\\d+)$)")

        doc.select("main section.from-mute article").forEach { episodeElement ->
            var link = episodeElement.selectFirst("a")?.attr("href") ?: return@forEach
            link = "${mainUrl}${link}"
            val epNum = epRegex.find(link)?.destructured?.component1()?.toIntOrNull() ?: return@forEach

            episodes.add(
                newEpisode(
                    link,
                ){
                    this.episode = epNum
                }
            )
        }

        return newAnimeLoadResponse(title, url, getType(type)) {
            posterUrl = fixUrl(poster)
            addEpisodes(DubStatus.Subbed, episodes.reversed())
            showStatus = status
            plot = description
            tags = genre
        }
    }

    data class MainServers(
            @JsonProperty("SUB")
            val sub: List<Sub>?,
            @JsonProperty("DUB")
            val dub: List<Sub>?
    )

    data class Sub(
            val server: String,
            val url: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        app.get(data).document.select("script").amap { script ->
            if (script.data().contains("embeds:{")
            ) {
                val serversRegex = Regex("embeds:(\\{(?:DUB|SUB):\\[.*?]\\})")
                val serversPlain = serversRegex.find(script.data())?.destructured?.component1() ?: return@amap
                val json = parseJson<MainServers>(serversPlain)
                json.sub?.amap {
                    val url = it.url
                    loadExtractor(url, data, subtitleCallback, callback)
                }
                json.dub?.amap {
                    val url = it.url
                    loadExtractor(url, data, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}
