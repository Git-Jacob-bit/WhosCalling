package io.github.gitjacobbit.whoscalling.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.gitjacobbit.whoscalling.domain.LookupSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Settings(
    val hasApiKey: Boolean,
    val model: String,
    val autoBlock: Boolean,
    val autoBlockThreshold: Int,
    val cacheDays: Int,
    val onboardingDone: Boolean,
)

class SettingsRepository(private val context: Context) : LookupSettings {
    private object Keys {
        val apiKey = stringPreferencesKey("api_key_enc")
        val model = stringPreferencesKey("model")
        val autoBlock = booleanPreferencesKey("auto_block")
        val threshold = intPreferencesKey("auto_block_threshold")
        val cacheDays = intPreferencesKey("cache_days")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            hasApiKey = p[Keys.apiKey] != null,
            model = p[Keys.model] ?: DEFAULT_MODEL,
            autoBlock = p[Keys.autoBlock] ?: false,
            autoBlockThreshold = p[Keys.threshold] ?: 85,
            cacheDays = p[Keys.cacheDays] ?: 30,
            onboardingDone = p[Keys.onboardingDone] ?: false,
        )
    }

    override suspend fun current(): Settings = settings.first()

    override suspend fun apiKey(): String? =
        context.dataStore.data.first()[Keys.apiKey]?.let(SecretStore::decrypt)

    suspend fun setApiKey(key: String?) = context.dataStore.edit { p ->
        if (key.isNullOrBlank()) p.remove(Keys.apiKey) else p[Keys.apiKey] = SecretStore.encrypt(key.trim())
    }

    suspend fun setModel(model: String) = context.dataStore.edit { it[Keys.model] = model.trim() }
    suspend fun setAutoBlock(enabled: Boolean) = context.dataStore.edit { it[Keys.autoBlock] = enabled }
    suspend fun setAutoBlockThreshold(value: Int) = context.dataStore.edit { it[Keys.threshold] = value }
    suspend fun setCacheDays(days: Int) = context.dataStore.edit { it[Keys.cacheDays] = days }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboardingDone] = true }

    companion object {
        const val DEFAULT_MODEL = "gpt-5-mini"
    }
}
