package com.stormunblessed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class SoloLatinoProvider : MainAPI() {
    override var mainUrl = "https://sololatino.net"
    override var name = "SoloLatino"
    override var lang = "mx"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
    )

    override val mainPage = mainPageOf(
        "peliculas?" to "Peliculas",
        "series?" to "Series",
        "animes?" to "Animes",
        "peliculas?genero=animacion&sort=popular" to "Cartoons",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").document
        val home = document.select("div.card")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("div.card__info p.card__title")?.text() ?: "xd"
        val link = this.selectFirst("a")?.attr("href") ?: "xd"
        val img = this.selectFirst("a div.card__poster-wrap img.card__poster")?.attr("src")
        return newMovieSearchResponse(title, link, TvType.Movie) {
            this.posterUrl = img
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/buscar?q=$query").document
        val results =
            document.select("div.card").mapNotNull { it.toSearchResult() }
        return results
    }

    class MainTemporada(elements: Map<String, List<MainTemporadaElement>>) :
        HashMap<String, List<MainTemporadaElement>>(elements)

    data class MainTemporadaElement(
        val title: String? = null,
        val image: String? = null,
        val season: Int? = null,
        val episode: Int? = null
    )

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val tvType = if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries
        val title = doc.selectFirst(".w-44")!!.attr("alt")
        val poster = doc.selectFirst(".w-44")!!.attr("src")
        val backimage = doc.selectFirst(".detail-hero__bg")?.attr("style")?.substringAfter("url('")?.substringBefore("');")
        val description = doc.selectFirst("p.text-sm.leading-relaxed.max-w-2xl")!!.text()
        val year = doc.selectFirst("div.flex.flex-wrap.items-center.text-sm span")?.text()?.toIntOrNull()
        val tags = doc.select("a.py-1").map { it.text() }
        val recommendations = doc.select("div[id*=scroll-related] div.card").mapNotNull { it.toSearchResult() }
        val episodes = doc.select("div[data-season-panel]").flatMap {
            val season = it.attr("data-season-panel").toIntOrNull()
            it.select("a.ep-item").mapIndexed { idx, it ->
                val url = it.attr("href")
                val title = it.selectFirst("p.text-sm.font-semibold.text-white.leading-tight")?.text()
                val img = it.selectFirst("img.ep-thumb")?.attr("src")
                newEpisode(url){
                        this.name = title
                        this.season = season
                        this.episode = idx+1
                        this.posterUrl = img
                    }
            }
        }

        return when (tvType) {
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(
                    title,
                    url, tvType, episodes,
                ) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backimage ?: poster
                    this.plot = description
                    this.tags = tags
                    this.year = year
                    this.recommendations = recommendations
                }
            }

            TvType.Movie -> {
                newMovieLoadResponse(title, url, tvType, url) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = backimage ?: poster
                    this.plot = description
                    this.tags = tags
                    this.year = year
                    this.recommendations = recommendations
                }
            }

            else -> null
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("button.server-btn").amap{
            val url = it.attr("data-server-url")
            if (url.startsWith("https://embed69.org/")) {
                Embed69Extractor.load(url, data, subtitleCallback, callback)
            } else if (url.startsWith("https://xupalace.org/video")) {
                val regex = """(go_to_player|go_to_playerVast)\('(.*?)'""".toRegex()
                regex.findAll(app.get(url).document.html()).map { it.groupValues.get(2) }
                    .toList().amap {
                        loadExtractor(fixHostsLinks(it), data, subtitleCallback, callback)
                    }
            } else { // https://xupalace.org/uqlink.php or others
                app.get(url).document.selectFirst("iframe")?.attr("src")?.let {
                    loadExtractor(fixHostsLinks(it), data, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}