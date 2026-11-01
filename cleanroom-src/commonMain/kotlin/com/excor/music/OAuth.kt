package com.excor.music

import com.excor.catalog.ExcorCatalogError
import com.excor.catalog.ExcorCatalogException
import com.excor.catalog.ExcorCatalogProvider
import io.ktor.http.URLBuilder
import io.ktor.util.generateNonce
import kotlinx.serialization.json.JsonObject

/**
 * Spotify's Authorization Code flow with PKCE, which needs no client secret, so nothing secret ships in an app.
 * Open [authorizationUrl] in a browser, check the returned `state` equals [ExcorAuthorizationRequest.state], then
 * [exchange] the code. [refresher] plugs into [ExcorTokenManager].
 */
class ExcorSpotifyAuth internal constructor(private val http: MusicHttp, private val clientId: String, private val nowMs: () -> Long) {
    init {
        require(clientId.isNotBlank() && clientId.all { it.isLetterOrDigit() }) { "invalid Spotify client ID" }
    }

    fun authorizationUrl(redirectUri: String, scopes: Set<String>): ExcorAuthorizationRequest {
        val pkce = ExcorPkce.create()
        val state = base64Url(generateNonce(24))
        val url = URLBuilder("https://accounts.spotify.com/authorize").apply {
            parameters.append("client_id", clientId)
            parameters.append("response_type", "code")
            parameters.append("redirect_uri", redirectUri)
            parameters.append("code_challenge_method", "S256")
            parameters.append("code_challenge", pkce.challenge)
            parameters.append("state", state)
            if (scopes.isNotEmpty()) parameters.append("scope", scopes.joinToString(" "))
        }.buildString()
        return ExcorAuthorizationRequest(url, state, redirectUri, pkce)
    }

    @Throws(Throwable::class)
    suspend fun exchange(request: ExcorAuthorizationRequest, code: String, returnedState: String): ExcorOAuthToken {
        // A different state means the response belongs to another request (or a forged one).
        if (returnedState != request.state) throw ExcorCatalogException(ExcorCatalogProvider.SPOTIFY, ExcorCatalogError.INVALID_REQUEST)
        return tokenOf(post(mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to request.redirectUri,
            "client_id" to clientId,
            "code_verifier" to request.pkce.verifier,
        ), refreshing = false), previousRefresh = null)
    }

    val refresher: ExcorTokenRefresher = ExcorTokenRefresher { provider, token ->
        val refresh = token.refreshToken
        if (provider != ExcorCatalogProvider.SPOTIFY || refresh == null) throw ExcorCatalogException(provider, ExcorCatalogError.INVALID_CREDENTIAL)
        tokenOf(post(mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to clientId), refreshing = true), refresh)
    }

    private suspend fun post(form: Map<String, String>, refreshing: Boolean): JsonObject = try {
        http.postForm(ExcorCatalogProvider.SPOTIFY, "https://accounts.spotify.com/api/token", form).asObject(ExcorCatalogProvider.SPOTIFY)
    } catch (e: ExcorCatalogException) {
        // invalid_grant: the refresh token was revoked or expired, so the stored one must go.
        if (refreshing && e.statusCode == 400) throw ExcorCatalogException(ExcorCatalogProvider.SPOTIFY, ExcorCatalogError.INVALID_CREDENTIAL, 400)
        throw e
    }

    private fun tokenOf(o: JsonObject, previousRefresh: String?): ExcorOAuthToken {
        val access = o.str("access_token") ?: throw ExcorCatalogException(ExcorCatalogProvider.SPOTIFY, ExcorCatalogError.MALFORMED_RESPONSE)
        return ExcorOAuthToken(
            accessToken = access,
            refreshToken = o.str("refresh_token") ?: previousRefresh,
            expiresAtMs = o.long("expires_in")?.let { nowMs() + it * 1_000 },
            scopes = o.str("scope")?.split(' ')?.filter(String::isNotEmpty)?.toSet().orEmpty(),
        )
    }
}

/** One authorization attempt; keep it until the redirect returns. [toString] hides the verifier. */
data class ExcorAuthorizationRequest(val url: String, val state: String, val redirectUri: String, internal val pkce: ExcorPkce) {
    override fun toString(): String = "ExcorAuthorizationRequest(state=present, redirectUri=$redirectUri)"
}

/**
 * Deezer's OAuth consent URL. Exchanging the code needs the app secret, so that step belongs on the host's server;
 * pass the resulting access token through [ExcorMusicConfig.tokens].
 */
fun excorDeezerAuthorizationUrl(appId: String, redirectUri: String, permissions: Set<String> = setOf("basic_access", "offline_access")): String {
    require(appId.isNotBlank() && appId.all { it.isDigit() }) { "invalid Deezer app ID" }
    return URLBuilder("https://connect.deezer.com/oauth/auth.php").apply {
        parameters.append("app_id", appId)
        parameters.append("redirect_uri", redirectUri)
        parameters.append("perms", permissions.joinToString(","))
    }.buildString()
}
