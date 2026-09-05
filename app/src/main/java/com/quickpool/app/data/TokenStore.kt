package com.quickpool.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class TokenStore(private val context: Context) {

    private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    private val USER_ID_KEY = stringPreferencesKey("user_id")

    suspend fun saveTokens(userId: String, accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = userId
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
        TokenHolder.accessToken = accessToken
    }

    suspend fun getAccessToken(): String? =
        context.dataStore.data.first()[ACCESS_TOKEN_KEY]

    suspend fun getRefreshToken(): String? =
        context.dataStore.data.first()[REFRESH_TOKEN_KEY]

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun loadIntoMemory() {
        TokenHolder.accessToken = getAccessToken()
    }
}