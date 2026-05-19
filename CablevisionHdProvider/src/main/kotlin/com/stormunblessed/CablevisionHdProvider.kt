package com.stormunblessed

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import java.net.URL

class CablevisionHdProvider : MainAPI() {

    override var mainUrl = "https://www.cablevisionhd.com"
    override var name = "CablevisionHd"
    override var lang = "mx"

    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Live,
    )

    private fun decodeBase64UntilUnchanged(encodedString: String): String {
        var decodedString = encodedString
        var previousDecodedString = ""
        while (decodedString != previousDecodedString) {
            previousDecodedString = decodedString
            decodedString = try {
                val decodedBytes = Base64.decode(decodedString, Base64.DEFAULT)
                String(decodedBytes)
            } catch (e: IllegalArgumentException) {
                // If decoding fails (e.g., not valid base64), break the loop
                break
            }
        }

        return decodedString
    }

    val nowAllowed = setOf(
        "Únete al chat",
        "Donar con Paypal",
        "Lizard Premium",
        "Vuelvete Premium (No ADS)",
        "Únete a Whatsapp",
        "Únete a Telegram",
        "¿Nos invitas el cafe?",
        "Mundo Latam",
    )

    val deportesCat = setOf(
        "TUDN",
        "WWE",
        "Afizzionados",
        "Gol Perú",
        "Gol TV",
        "TNT SPORTS",
        "Fox Sports Premium",
        "TYC Sports",
        "Movistar Deportes (Perú)",
        "Movistar La Liga",
        "Movistar Liga De Campeones",
        "Dazn F1",
        "Dazn La Liga",
        "Bein La Liga",
        "Bein Sports Extra",
        "Directv Sports",
        "Directv Sports 2",
        "Directv Sports Plus",
        "Espn Deportes",
        "Espn Extra",
        "Espn Premium",
        "Espn",
        "Espn 2",
        "Espn 3",
        "Espn 4",
        "Espn Mexico",
        "Espn 2 Mexico",
        "Espn 3 Mexico",
        "Fox Deportes",
        "Fox Sports",
        "Fox Sports 2",
        "Fox Sports 3",
        "Fox Sports Mexico",
        "Fox Sports 2 Mexico",
        "Fox Sports 3 Mexico",
    )

    val entretenimientoCat = setOf(
        "Telefe",
        "El Trece",
        "Televisión Pública",
        "Telemundo Puerto rico",
        "Univisión",
        "Univisión Tlnovelas",
        "Pasiones",
        "Caracol",
        "RCN",
        "Latina",
        "America TV",
        "Willax TV",
        "ATV",
        "Las Estrellas",
        "Tl Novelas",
        "Galavision",
        "Azteca 7",
        "Azteca Uno",
        "Canal 5",
        "Distrito Comedia",
    )

    val noticiasCat = setOf(
        "Telemundo 51",
    )

    val peliculasCat = setOf(
        "Movistar Accion",
        "Movistar Drama",
        "Universal Channel",
        "TNT",
        "TNT Series",
        "Star Channel",
        "Star Action",
        "Star Series",
        "Cinemax",
        "Space",
        "Syfy",
        "Warner Channel",
        "Warner Channel (México)",
        "Cinecanal",
        "FX",
        "AXN",
        "AMC",
        "Studio Universal",
        "Multipremier",
        "Golden",
        "Golden Plus",
        "Golden Edge",
        "Golden Premier",
        "Golden Premier 2",
        "Sony",
        "DHE",
        "NEXT HD",
    )

    val infantilCat = setOf(
        "Cartoon Network",
        "Tooncast",
        "Cartoonito",
        "Disney Channel",
        "Disney JR",
        "Nick",
    )

    val educacionCat = setOf(
        "Discovery Channel",
        "Discovery World",
        "Discovery Theater",
        "Discovery Science",
        "Discovery Familia",
        "History",
        "History 2",
        "Animal Planet",
        "Nat Geo",
        "Nat Geo Mundo",
    )

    val dos47Cat = setOf(
        "24/7",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("Deportes", mainUrl),
            Pair("Entretenimiento", mainUrl),
            Pair("Noticias", mainUrl),
            Pair("Peliculas", mainUrl),
            Pair("Infantil", mainUrl),
            Pair("Educacion", mainUrl),
            Pair("24/7", mainUrl),
            Pair("Todos", mainUrl),
        )
        urls.amap { (name, url) ->
            val script = app.get(url).document.select("script")
                .firstOrNull { it.html().contains("const homeChannels = ") }?.html()
            var channelsString =
                script?.substringAfter("const homeChannels = `")?.substringBefore("`;")
            channelsString += script?.substringAfter("const showChannels = `")
                ?.substringBefore("`;")
            val doc = Jsoup.parse(channelsString)

            val home = doc.select("a.channel-card").filterNot { element ->
                val text = element.selectFirst("p")?.text()
                    ?: ""
                nowAllowed.any {
                    text.contains(it, ignoreCase = true)
                } || text.isBlank()
            }.filter {
                val text = it.selectFirst("p")?.text()?.trim()
                    ?: ""
                when (name) {
                    "Deportes" -> {
                        deportesCat.any {
                            text.contains(it, ignoreCase = true)
                        }
                    }

                    "Entretenimiento" -> {
                        entretenimientoCat.any {
                            text.contains(it, ignoreCase = true)
                        }
                    }

                    "Noticias" -> {
                        noticiasCat.any {
                            text.contains(it, ignoreCase = true)
                        }
                    }

                    "Peliculas" -> {
                        peliculasCat.any {
                            text.contains(it, ignoreCase = true)
                        }
                    }

                    "Infantil" -> {
                        infantilCat.any {
                            text.contains(it, ignoreCase = true)
                        }
                    }

                    "Educacion" -> {
                        educacionCat.any {
                            text.contains(it, ignoreCase = true)
                        }
                    }

                    "24/7" -> {
                        dos47Cat.any {
                            text.contains(it, ignoreCase = true)
                        }
                    }

                    "Todos" -> true
                    else -> true
                }
            }.map {
                val title = it.selectFirst("p")?.text()
                    ?: ""
                val img = it.selectFirst("img")?.attr("src")
                    ?: ""
                val link = it.attr("href")
                newLiveSearchResponse(
                    title,
                    link,
                    TvType.Live,
                ) {
                    this.posterUrl = fixUrl(img)
                }
            }
            items.add(HomePageList(name, home, true))
        }

        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val script = app.get(mainUrl).document.select("script")
            .firstOrNull { it.html().contains("const homeChannels = ") }?.html()
        var channelsString = script?.substringAfter("const homeChannels = `")?.substringBefore("`;")
        channelsString += script?.substringAfter("const showChannels = `")?.substringBefore("`;")
        val doc = Jsoup.parse(channelsString)


        return doc.select("a.channel-card").filterNot { element ->
            val text = element.selectFirst("p")?.text()
                ?: ""
            nowAllowed.any {
                text.contains(it, ignoreCase = true)
            } || text.isBlank()
        }.filter { element ->
            element.selectFirst("p")?.text()?.contains(query, ignoreCase = true)
                ?: false
        }.map {
            val title = it.selectFirst("p")?.text()
                ?: ""
            val img = it.selectFirst("img")?.attr("src")
                ?: ""
            val link = it.attr("href")
            newLiveSearchResponse(
                title,
                link,
                TvType.Live,
            ) {
                this.posterUrl = fixUrl(img)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val poster =
            doc.selectFirst(".space-x-4 img")
                ?.attr("src") ?: ""
        val title =
            doc.selectFirst("head meta[property=og:title]")
                ?.attr("content")
                ?: ""
        val desc =
            doc.selectFirst("head meta[property=og:description]")
                ?.attr("content")
                ?: ""

        return newMovieLoadResponse(
            title,
            url, TvType.Live, url
        ) {
            this.posterUrl = fixUrl(poster)
            this.backgroundPosterUrl = fixUrl(poster)
            this.plot = desc
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select(".transition").amap {
            val trembedlink = it.attr("href")
            val name = it.text()
            if (trembedlink.contains("/stream")) {
                val tremrequest = app.get(
                    trembedlink, headers = mapOf(
                        "Host" to "www.cablevisionhd.com",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Referer" to data,
                        "Alt-Used" to "www.cablevisionhd.com",
                        "Connection" to "keep-alive",
                        "Cookie" to "TawkConnectionTime=0; twk_idm_key=qMfE5UE9JTs3JUBCtVUR1",
                        "Upgrade-Insecure-Requests" to "1",
                        "Sec-Fetch-Dest" to "iframe",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "same-origin",
                    )
                ).document
                val trembedlink2 = tremrequest.selectFirst("iframe")?.attr("src") ?: ""
                val finalReq = app.get(
                    trembedlink2, headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0",
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Referer" to mainUrl,
                        "Connection" to "keep-alive",
                        "Upgrade-Insecure-Requests" to "1",
                        "Sec-Fetch-Dest" to "iframe",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "cross-site",
                    )
                ).document
                finalReq.select("script").amap {
                    val script = it.html();
                    if (script.contains("function(p,a,c,k,e,d)")) {
                        val jsUnpacker = JsUnpacker(script)
                        if (jsUnpacker.detect()) {
                            val regex = """MARIOCSCryptOld\("(.*?)"\)""".toRegex()
                            val match = regex.find(jsUnpacker.unpack() ?: "")
                            val hash = match?.groupValues?.get(1) ?: ""
                            val extractedurl = decodeBase64UntilUnchanged(hash)
                            if (extractedurl.isNotBlank()) {
                                callback(
                                    newExtractorLink(
                                        name,
                                        name,
                                        extractedurl,
                                    )
                                )
                            }
                        }
                    } else if (script.trim().startsWith("jwplayer.key = '")) {
                        var url =
                            script.substringAfter("setupPlayer(\"").substringBefore("\");")
                        callback(
                            newExtractorLink(
                                name,
                                name,
                                url,
                            )
                        )
                    } else if (script.trim().startsWith("var src = \"")) {
                        var url =
                            fixUrl(script.substringAfter("var src = \"").substringBefore("\";").replace("\\/", "/").replace("\\:", ":"))
                        callback(
                            newExtractorLink(
                                name,
                                name,
                                url,
                            )
                        )
                    } else if (script.trim().startsWith("var playbackURL = ")) {
                        script.substringAfter("atob(\"").substringBefore("\")").let {
                            val extractedurl = decodeBase64UntilUnchanged(it)
                            if (extractedurl.isNotBlank()) {
                                callback(
                                    newExtractorLink(
                                        name,
                                        name,
                                        extractedurl,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    fun getBaseUrl(urlString: String): String {
        val url = URL(urlString)
        return "${url.protocol}://${url.host}"
    }

    fun getHostUrl(urlString: String): String {
        val url = URL(urlString)
        return url.host
    }
}