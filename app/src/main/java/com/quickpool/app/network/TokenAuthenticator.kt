package com.quickpool.app.network

import com.quickpool.app.data.TokenHolder
import com.quickpool.app.data.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(private val tokenStore: TokenStore) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite retry loops if refresh itself keeps failing
        if (responseCount(response) >= 2) return null

        val refreshToken = runBlocking { tokenStore.getRefreshToken() } ?: return null

        return runBlocking {
            try {
                val refreshResponse = ApiClient.authApi.refreshToken(RefreshTokenDto(refreshToken))
                if (refreshResponse.isSuccessful) {
                    val body = refreshResponse.body()!!
                    tokenStore.saveTokens(body.userId, body.accessToken, body.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${body.accessToken}")
                        .build()
                } else {
                    tokenStore.clear()
                    TokenHolder.accessToken = null
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}