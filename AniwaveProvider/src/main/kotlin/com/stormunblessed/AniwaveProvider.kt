package com.stormunblessed



import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import kotlinx.coroutines.delay
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.net.URLEncoder


class AniwaveProvider : MainAPI() {
    override var mainUrl = "https://aniwave.to"
    override var name = "Aniwave/9Anime"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)
    override val hasQuickSearch = true

    //private val vrfInterceptor by lazy { JsVrfInterceptor(mainUrl) }
    companion object {
        fun encode(input: String): String =
            java.net.URLEncoder.encode(input, "utf-8").replace("+", "%2B")
        private fun decode(input: String): String = java.net.URLDecoder.decode(input, "utf-8")
//        private const val consuNineAnimeApi = "https://api.consumet.org/anime/9anime"


    }

    override val mainPage = mainPageOf(
        "$mainUrl/ajax/home/widget/trending?page=" to "Trending",
        "$mainUrl/ajax/home/widget/updated-all?page=" to "All",
        "$mainUrl/ajax/home/widget/updated-sub?page=" to "Recently Updated (SUB)",
        "$mainUrl/ajax/home/widget/updated-dub?page=" to "Recently Updated (DUB)",
        "$mainUrl/ajax/home/widget/updated-china?page=" to "Recently Updated (Chinese)",
        "$mainUrl/ajax/home/widget/random?page=" to "Random",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = request.data + page
        //vrfInterceptor.wake()
        val home = Jsoup.parse(
            app.get(
                url,

            ).parsed<Response>().html!!
        ).select("div.item").mapNotNull { element ->
            val title = element.selectFirst(".info > .name") ?: return@mapNotNull null
            val link = title.attr("href").replace(Regex("\\/ep.*\$"),"")
            val poster = element.selectFirst(".poster > a > img")?.attr("src")
            val meta = element.selectFirst(".poster > a > .meta > .inner > .left")
            val subbedEpisodes = meta?.selectFirst(".sub")?.text()?.toIntOrNull()
            val dubbedEpisodes = meta?.selectFirst(".dub")?.text()?.toIntOrNull()

            newAnimeSearchResponse(title.text() ?: return@mapNotNull null, link) {
                this.posterUrl = poster
                addDubStatus(
                    dubbedEpisodes != null,
                    subbedEpisodes != null,
                    dubbedEpisodes,
                    subbedEpisodes
                )
            }
        }

        return newnewHomePageResponse (request.name, home, true)
    }

    data class Response(
        @JsonProperty("result") val html: String?,
        @JsonProperty("llaa"   ) var llaa   : String? = null,
        @JsonProperty("epurl" ) var epurl : String? = null
    )


    override suspend fun quickSearch(query: String): List<SearchResponse> {
        delay(1000)
        return search(query)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url =
            "$mainUrl/filter?keyword=${query}"
        return app.get(url,
        ).document.select("#list-items div.ani.poster.tip > a").mapNotNull {
            val link = fixUrl(it.attr("href") ?: return@mapNotNull null)
            val img = it.select("img")
            val title = img.attr("alt")
            val subbedEpisodes = it?.selectFirst(".sub")?.text()?.toIntOrNull()
            val dubbedEpisodes = it?.selectFirst(".dub")?.text()?.toIntOrNull()
            newAnimeSearchResponse(title, link) {
                posterUrl = img.attr("src")
                addDubStatus(
                    dubbedEpisodes != null,
                    subbedEpisodes != null,
                    dubbedEpisodes,
                    subbedEpisodes
                )

            }
        }
    }


    /*   private fun Int.toBoolean() = this == 1
     data class EpsInfo (
         @JsonProperty("llaa"     ) var llaa     : String?  = null,
         @JsonProperty("epurl"    ) var epurl    : String?  = null,
         @JsonProperty("needDUB" ) var needDub : Boolean? = null,
         )
     private fun Element.toGetEpisode(url: String, needDub: Boolean):Episode{
           //val ids = this.attr("data-ids").split(",", limit = 2)
           val epNum = this.attr("data-num")
               .toIntOrNull() // might fuck up on 7.5 ect might use data-slug instead
           val epTitle = this.selectFirst("span.d-title")?.text()
           val epurl = "$url/ep-$epNum"
           val data = "{\"llaa\":\"null\",\"epurl\":\"$epurl\",\"needDUB\":$needDub}"
          return newEpisode(
               data,
               epTitle,
               episode = epNum
           )
       } */


    override suspend fun load(url: String): LoadResponse {
        val validUrl = url.replace("https://9anime.to", mainUrl).replace("https://aniwave.to",mainUrl)
        val doc = app.get(validUrl,
        ).document

        val meta = doc.selectFirst("#w-info") ?: throw ErrorLoadingException("Could not find info")
        val ratingElement = meta.selectFirst(".brating > #w-rating")
        val id = ratingElement?.attr("data-id") ?: throw ErrorLoadingException("Could not find id")
        val binfo =
            meta.selectFirst(".binfo") ?: throw ErrorLoadingException("Could not find binfo")
        val info = binfo.selectFirst(".info") ?: throw ErrorLoadingException("Could not find info")
        val poster = binfo.selectFirst(".poster > span > img")?.attr("src")
        val backimginfo = doc.selectFirst("#player")?.attr("style")
        val backimgRegx = Regex("(http|https).*jpg")
        val backposter = backimgRegx.find(backimginfo.toString())?.value ?: poster
        val title = (info.selectFirst(".title") ?: info.selectFirst(".d-title"))?.text()
            ?: throw ErrorLoadingException("Could not find title")
        val vvhelp = consumetVrf(id)
        val vrf = encode(vvhelp.url)
        val episodeListUrl = "$mainUrl/ajax/episode/list/$id?${vvhelp.vrfQuery}=${vrf}"
        val body =
            app.get(episodeListUrl).parsedSafe<Response>()?.html
                ?: throw ErrorLoadingException("Could not parse json with Vrf=$vrf id=$id url=\n$episodeListUrl")

        val subEpisodes = ArrayList<Episode>()
        val dubEpisodes = ArrayList<Episode>()
        val softsubeps = ArrayList<Episode>()
        val genres = doc.select("div.meta:nth-child(1) > div:contains(Genre:) a").mapNotNull { it.text() }
        val recss = doc.select("div#watch-second .w-side-section div.body a.item").mapNotNull { rec ->
            val href = rec.attr("href")
            val rectitle = rec.selectFirst(".name")?.text() ?: ""
            val recimg = rec.selectFirst("img")?.attr("src")
            newAnimeSearchResponse(rectitle,fixUrl(href)){
                this.posterUrl = recimg
            }
        }
        val status = when (doc.selectFirst("div.meta:nth-child(1) > div:contains(Status:) span")?.text()) {
            "Releasing" -> ShowStatus.Ongoing
            "Completed" -> ShowStatus.Completed
            else -> null
        }

        val typetwo =  when(doc.selectFirst("div.meta:nth-child(1) > div:contains(Type:) span")?.text())  {
            "OVA" -> TvType.OVA
            "SPECIAL" -> TvType.OVA
            //"MOVIE" -> TvType.AnimeMovie
            else -> TvType.Anime
        }
        val duration = doc.selectFirst(".bmeta > div > div:contains(Duration:) > span")?.text()

        Jsoup.parse(body).body().select(".episodes > ul > li > a").apmap { element ->
            val ids = element.attr("data-ids").split(",", limit = 3)
            val dataDub = element.attr("data-dub").toIntOrNull()
            val epNum = element.attr("data-num")
                .toIntOrNull() // might fuck up on 7.5 ect might use data-slug instead
            val epTitle = element.selectFirst("span.d-title")?.text()
            //val filler = element.hasClass("filler")

            //season -1 HARDSUBBED
            //season 1 Dubbed
            //Season 2 SofSubbed
            //SUB, SOFT SUB and DUB
            if (dataDub == 1  && ids.size == 3) {
                ids.getOrNull(0)?.let { sub ->
                    val epdd = "{\"ID\":\"$sub\",\"type\":\"sub\"}"
                  subEpisodes.add(
                        newEpisode(epdd){
                            this.episode = epNum
                            this.name = epTitle
                            this.season = -1
                        }
                    )
                }

                ids.getOrNull(1)?.let { softsub ->
                    val epdd = "{\"ID\":\"$softsub\",\"type\":\"softsub\"}"
                    softsubeps.add(
                        newEpisode(epdd){
                            this.episode = epNum
                            this.name = epTitle
                            this.season = 2
                        }
                    )
                }

                ids.getOrNull(2)?.let { dub ->
                    val epdd = "{\"ID\":\"$dub\",\"type\":\"dub\"}"
                    dubEpisodes.add(
                        newEpisode(epdd){
                            this.episode = epNum
                            this.name = epTitle
                            this.season = 1
                        }
                    )
                }

            }


            //Just SUB and DUB
            if (dataDub == 1 && ids.size == 2) {
                ids.getOrNull(1)?.let { dub ->
                    val epdd = "{\"ID\":\"$dub\",\"type\":\"dub\"}"
                    dubEpisodes.add(
                        newEpisode(epdd){
                            this.episode = epNum
                            this.name = epTitle
                            this.season = 1
                        }
                    )
                }
                ids.getOrNull(0)?.let { sub ->
                    val epdd = "{\"ID\":\"$sub\",\"type\":\"sub\"}"
                    subEpisodes.add(
                        newEpisode(epdd){
                            this.episode = epNum
                            this.name = epTitle
                            this.season = -1
                        }
                    )
                }
            }

            //Just SUB and SOFT SUB
            if (dataDub == 0 && ids.size == 2) {
                ids.getOrNull(0)?.let { sub ->
                    val epdd = "{\"ID\":\"$sub\",\"type\":\"sub\"}"
                    subEpisodes.add(
                        newEpisode(epdd){
                            this.episode = epNum
                            this.name = epTitle
                            this.season = -1
                        }
                    )
                }

                ids.getOrNull(1)?.let { softsub ->
                    val epdd = "{\"ID\":\"$softsub\",\"type\":\"softsub\"}"
                    softsubeps.add(
                        newEpisode(epdd){
                            this.episode = epNum
                            this.name = epTitle
                            this.season = 2
                        }
                    )
                }
            }

            //Just SUB
            if (dataDub == 0 && ids.size == 1) {
                ids.getOrNull(0)?.let { sub ->
                    val epdd = "{\"ID\":\"$sub\",\"type\":\"sub\"}"
                    subEpisodes.add(
                        newEpisode(epdd){
                            this.episode = epNum
                            this.name = epTitle
                            this.season = -1
                        }
                    )
                }
            }
        }

        //season -1 HARDSUBBED
        //season 1 Dubbed
        //Season 2 SofSubbed


        println("SUBstat ${DubStatus.Subbed.name}")
        println("SUBstat ${DubStatus.Subbed.toString()}")

        val names = listOf(
            Pair(DubStatus.Subbed.toString(),-1),
            Pair(DubStatus.Dubbed.toString(),1),
            Pair("Soft ${DubStatus.Subbed}",2),
        )

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            addEpisodes(DubStatus.Subbed, subEpisodes)
            addEpisodes(DubStatus.Subbed, softsubeps)
            this.seasonNames = names.map { (name, int) -> SeasonData(int, name) }
            plot = info.selectFirst(".synopsis > .shorting > .content")?.text()
            this.posterUrl = poster
            rating = ratingElement.attr("data-score").toFloat().times(1000f).toInt()
            this.backgroundPosterUrl = backposter
            this.tags = genres
            this.recommendations = recss
            this.showStatus = status
            this.type = typetwo
            addDuration(duration)
        }
    }

    data class Result(
        @JsonProperty("url")
        val url: String? = null
    )

    data class Links(
        @JsonProperty("result")
        val result: Result? = null
    )

    /*private suspend fun getEpisodeLinks(id: String): Links? {
        return app.get("$mainUrl/ajax/server/$id?vrf=encodeVrf(id, cipherKey)}").parsedSafe()
    }*/

    /*   private suspend fun getStream(
           streamLink: String,
           name: String,
           referer: String,
           callback: (ExtractorLink) -> Unit
       )  {
           return generateM3u8(
               name,
               streamLink,
               referer
           ).map {
               callback(
                   newExtractorLink(
                       ""
                   )
               )
           }
       } */


    /*  private suspend fun getM3U8(epurl: String, lang: String, callback: (ExtractorLink) -> Unit):Boolean{
          val isdub = lang == "dub"
          val vidstream = app.get(epurl, interceptor = JsInterceptor("41", lang), timeout = 45)
          val mcloud = app.get(epurl, interceptor = JsInterceptor("28", lang), timeout = 45)
          val vidurl = vidstream.url
          val murl = mcloud.url
          val ll = listOf(vidurl, murl)
          ll.forEach {link ->
              val vv = link.contains("mcloud")
              val name1 = if (vv) "Mcloud" else "Vidstream"
              val ref = if (vv) "https://mcloud.to/" else ""
              val name2 = if (isdub) "$name1 Dubbed" else "$name1 Subbed"
              getStream(link, name2, ref ,callback)
          }
          return true
      } */



    data class NineConsumet (
        @JsonProperty("headers"  ) var headers  : ServerHeaders?           = ServerHeaders(),
        @JsonProperty("sources"  ) var sources  : ArrayList<NineConsuSources>? = arrayListOf(),
        @JsonProperty("embedURL" ) var embedURL : String?            = null,

        )
    data class NineConsuSources (
        @JsonProperty("url"    ) var url    : String?  = null,
        @JsonProperty("isM3U8" ) var isM3U8 : Boolean? = null
    )
    data class ServerHeaders (

        @JsonProperty("Referer"    ) var referer    : String? = null,
        @JsonProperty("User-Agent" ) var userAgent : String? = null

    )
    data class SubDubInfo (
        @JsonProperty("ID"   ) val ID   : String,
        @JsonProperty("type" ) val type : String
    )

    private fun serverName(serverID: String?): String? {
        val sss =
            when (serverID) {
                "41" -> "vidplay"
                "44" -> "filemoon"
                "40" -> "streamtape"
                "35" -> "mp4upload"
                else -> null
            }
        return sss
    }


    data class ConsumetVrfHelper (
        @JsonProperty("url"      ) var url      : String,
        @JsonProperty("vrfQuery" ) var vrfQuery : String
    )
    private suspend fun consumetVrf(input: String): ConsumetVrfHelper{
        return app.get("https://9anime.eltik.net/vrf?query=$input&apikey=lagrapps").parsed<ConsumetVrfHelper>()
    }
    private suspend fun decUrlConsu(serverID: String):String {
        val sa = consumetVrf(serverID)
        val encID = sa.url
        val videncrr = app.get("$mainUrl/ajax/server/$serverID?${sa.vrfQuery}=${encode(encID)}").parsed<Links>()
        val encUrl = videncrr.result?.url
        val ses = app.get("https://9anime.eltik.net/decrypt?query=$encUrl&apikey=lagrapps").text
        return ses.substringAfter("url\":\"").substringBefore("\"")
    }






    data class AniwaveMediaInfo (

        @JsonProperty("result" ) val result : AniwaveResult? = AniwaveResult()

    )


    data class AniwaveResult (

        @JsonProperty("sources" ) var sources : ArrayList<AniwaveTracks> = arrayListOf(),
        @JsonProperty("tracks"  ) var tracks  : ArrayList<AniwaveTracks>  = arrayListOf()

    )
    data class AniwaveTracks (
        @JsonProperty("file"    ) var file    : String?  = null,
        @JsonProperty("label"   ) var label   : String?  = null,
    )





    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parseData = AppUtils.parseJson<SubDubInfo>(data)
        val sa = consumetVrf(parseData.ID)
        val datavrf = sa.url
        val one = app.get("$mainUrl/ajax/server/list/${parseData.ID}?${sa.vrfQuery}=${encode(datavrf)}").parsed<Response>()
        val two = Jsoup.parse(one.html ?: return false)
        val aas = two.select("div.servers .type[data-type=${parseData.type}] li").mapNotNull {
            val datalinkId = it.attr("data-link-id")
            val serverID = it.attr("data-sv-id").toString()
            val newSname = serverName(serverID)
            Pair(newSname, datalinkId)
        }
        aas.apmap { (sName, sId) ->
            val nName = if (sName == null) "mycloud" else sName
            val vids = nName == "vidplay"
            val mclo = nName == "mycloud"
            if (vids || mclo) {
                val sae = consumetVrf(sId)
                val encID = sae.url
                val videncrr = app.get("$mainUrl/ajax/server/$sId?${sae.vrfQuery}=${encode(encID)}").parsed<Links>()
                val encUrl = videncrr.result?.url
                if (encUrl != null) {
                    val asss = decUrlConsu(sId)
                    val regex = Regex("(.+?/)e(?:mbed)?/([a-zA-Z0-9]+)")
                    val group = regex.find(asss)!!.groupValues
                    val comps = asss.split("/");
                    val vizId = comps[comps.size - 1];
                    val action = if (vids) "rawVizcloud" else "rawMcloud"
                    val futoken = app.get("https://vidstream.pro/futoken").text
                    val encodedFutoken = URLEncoder.encode(futoken, "UTF-8")
                    val map = mapOf("query" to vizId, "futoken" to futoken)
                    val jsonBody = JSONObject(map).toString()
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val ssaeUrl = app.post("https://9anime.eltik.net/$action?apikey=lagrapps", mapOf("Content-Type" to "application/x-www-form-urlencoded"), requestBody = RequestBody.Companion.create(mediaType, jsonBody)).text.substringAfter("rawURL\"").substringAfter("\"").substringBefore("\"");

                    val ref = if (vids) "https://vidstream.pro/" else "https://mcloud.to/"

                    //val ssae = app.get(ssaeUrl, headers = mapOf("Referer" to ref)).text

                    val resultJson = app.get(ssaeUrl, headers = mapOf("Referer" to ref)).parsedSafe<AniwaveMediaInfo>()
                    val name = if (vids) "Vidplay" else "MyCloud"
                    resultJson?.result?.sources?.apmap {
                       val source = it.file ?: ""
                       generateM3u8(
                            name,
                           source,
                           ref
                       ).forEach(callback)
                    }
                    resultJson?.result?.tracks?.apmap {
                        val subtitle = it.file ?: ""
                        val lang = it.label ?: ""
                        subtitleCallback.invoke(
                            SubtitleFile(lang, subtitle)
                        )
                    }
                }
            }
            if (!sName.isNullOrEmpty() && !vids || !mclo) {
                val bbbs = decUrlConsu(sId)
                loadExtractor(bbbs, subtitleCallback, callback)
            }
        }
        return true
    }
}