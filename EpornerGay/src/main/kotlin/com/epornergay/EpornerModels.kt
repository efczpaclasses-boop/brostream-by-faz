package com.epornergay

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpornerSearchResponse(
    @JsonProperty("total_pages") val totalPages: Int = 0,
    @JsonProperty("current_page") val currentPage: Int = 0,
    val videos: List<EpornerVideo> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpornerVideo(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val views: Long = 0,
    val rate: Double = 0.0,
    @JsonProperty("length_sec") val lengthSeconds: Int = 0,
    @JsonProperty("default_thumb") val defaultThumb: EpornerThumb? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EpornerThumb(
    val src: String? = null,
)
