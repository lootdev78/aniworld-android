package io.github.lootdev78.aniworld.aniskip

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aniworld_prefs")

object AniskipPrefs {
    val ANISKIP_ENABLED = booleanPreferencesKey("aniskip_enabled")
    val ANISKIP_BASE_URL = stringPreferencesKey("aniskip_base_url")
    val ANISKIP_ALWAYS_SKIP_INTRO = booleanPreferencesKey("aniskip_always_skip_intro")
    val ANISKIP_ALWAYS_SKIP_OUTRO = booleanPreferencesKey("aniskip_always_skip_outro")

    fun isEnabledFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[ANISKIP_ENABLED] ?: true }

    fun baseUrlFlow(context: Context): Flow<String> =
        context.dataStore.data.map { it[ANISKIP_BASE_URL] ?: "https://api.aniskip.com" }

    fun alwaysSkipIntroFlow(context: Context) =
        context.dataStore.data.map { it[ANISKIP_ALWAYS_SKIP_INTRO] ?: false }

    fun alwaysSkipOutroFlow(context: Context) =
        context.dataStore.data.map { it[ANISKIP_ALWAYS_SKIP_OUTRO] ?: false }

    suspend fun setEnabled(context: Context, value: Boolean) = context.dataStore.edit { it[ANISKIP_ENABLED] = value }
    suspend fun setBaseUrl(context: Context, value: String) = context.dataStore.edit { it[ANISKIP_BASE_URL] = value }
    suspend fun setAlwaysSkipIntro(context: Context, value: Boolean) = context.dataStore.edit { it[ANISKIP_ALWAYS_SKIP_INTRO] = value }
    suspend fun setAlwaysSkipOutro(context: Context, value: Boolean) = context.dataStore.edit { it[ANISKIP_ALWAYS_SKIP_OUTRO] = value }
}
