package com.excor.music

import com.excor.catalog.ExcorCatalogProvider
import com.excor.catalog.ExcorCatalogTokenProvider
import kotlin.test.Test
import kotlin.test.assertFalse

class ExcorMusicConfigTest {
    @Test
    fun configStringDoesNotRenderInjectedCredentialProviders() {
        val secret = "synthetic-user-token"
        val config = ExcorMusicConfig(
            tokens = LeakyCatalogTokenProvider(secret),
            appleMusicUserToken = LeakyMusicUserTokenProvider(secret),
        )

        val rendered = config.toString()
        assertFalse(secret in rendered, "ExcorMusicConfig must not call provider toString implementations")
        assertFalse("provider-secret" in rendered)
    }

    private class LeakyCatalogTokenProvider(private val secret: String) : ExcorCatalogTokenProvider {
        override suspend fun accessToken(provider: ExcorCatalogProvider): String = secret
        override fun toString(): String = "provider-secret:$secret"
    }

    private class LeakyMusicUserTokenProvider(private val secret: String) : ExcorMusicUserTokenProvider {
        override suspend fun musicUserToken(): String = secret
        override fun toString(): String = "provider-secret:$secret"
    }
}
