package com.excor.app

import com.excor.MusicCatalogItem

// The catalogs' own image hosts: Spotify (i.scdn.co, mosaic.scdn.co) and Deezer (cdn-images.dzcdn.net).
private val ARTWORK_HOSTS = listOf(".scdn.co", ".dzcdn.net")

/** The first image of [item] the app may fetch: HTTPS on a catalog's own image host; null for anything else. */
fun catalogArtworkUrl(item: MusicCatalogItem): String? = item.images.map { it.url }.firstOrNull { url ->
    val host = url.removePrefix("https://").takeIf { it != url }?.substringBefore('/')?.lowercase()
    host != null && '@' !in host && ':' !in host && ARTWORK_HOSTS.any { host.endsWith(it) }
}
