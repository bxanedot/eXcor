package com.excor

import io.ktor.http.URLBuilder

internal fun setQueryParameter(url: String, name: String, value: String): String =
    URLBuilder(url).apply {
        parameters.remove(name)
        parameters.append(name, value)
    }.buildString()
