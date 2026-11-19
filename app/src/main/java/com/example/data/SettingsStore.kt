package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.network.PcHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.airTouchDataStore by preferencesDataStore(name = "airtouch_prefs")

data class RemotePreferences(
    val pointerSensitivity: Float = 1.4f,
    val scrollSensitivity: Float = 1.2f,
    val naturalScrolling: Boolean = true,
    val tapToClick: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val voiceAutoCommands: Boolean = true,
)

data class StoredSettings(
    val preferences: RemotePreferences = RemotePreferences(),
    val recentHosts: List<PcHost> = emptyList(),
)

class SettingsStore(private val context: Context) {

    val settingsFlow: Flow<StoredSettings> = context.airTouchDataStore.data.map { prefs ->
        val remotePrefs = RemotePreferences(
            pointerSensitivity = prefs[KEY_POINTER_SENS] ?: 1.4f,
            scrollSensitivity = prefs[KEY_SCROLL_SENS] ?: 1.2f,
            naturalScrolling = prefs[KEY_NATURAL_SCROLL] ?: true,
            tapToClick = prefs[KEY_TAP_TO_CLICK] ?: true,
            hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
            voiceAutoCommands = prefs[KEY_VOICE_COMMANDS] ?: true,
        )
        val hostsJson = prefs[KEY_RECENT_HOSTS].orEmpty()
        val recentHosts = parseHosts(hostsJson)
        StoredSettings(preferences = remotePrefs, recentHosts = recentHosts)
    }

    suspend fun updatePreferences(transform: (RemotePreferences) -> RemotePreferences) {
        context.airTouchDataStore.edit { prefs ->
            val current = RemotePreferences(
                pointerSensitivity = prefs[KEY_POINTER_SENS] ?: 1.4f,
                scrollSensitivity = prefs[KEY_SCROLL_SENS] ?: 1.2f,
                naturalScrolling = prefs[KEY_NATURAL_SCROLL] ?: true,
                tapToClick = prefs[KEY_TAP_TO_CLICK] ?: true,
                hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
                voiceAutoCommands = prefs[KEY_VOICE_COMMANDS] ?: true,
            )
            val updated = transform(current)
            prefs[KEY_POINTER_SENS] = updated.pointerSensitivity
            prefs[KEY_SCROLL_SENS] = updated.scrollSensitivity
            prefs[KEY_NATURAL_SCROLL] = updated.naturalScrolling
            prefs[KEY_TAP_TO_CLICK] = updated.tapToClick
            prefs[KEY_HAPTICS] = updated.hapticsEnabled
            prefs[KEY_VOICE_COMMANDS] = updated.voiceAutoCommands
        }
    }

    suspend fun saveRecentHost(host: PcHost) {
        context.airTouchDataStore.edit { prefs ->
            val current = parseHosts(prefs[KEY_RECENT_HOSTS].orEmpty()).toMutableList()
            current.removeAll { it.ipAddress == host.ipAddress }
            current.add(0, host.copy(lastConnectedMs = System.currentTimeMillis()))
            val trimmed = current.take(5)
            val arr = JSONArray()
            for (item in trimmed) {
                arr.put(
                    JSONObject().apply {
                        put("name", item.name)
                        put("ip", item.ipAddress)
                        put("tcp", item.tcpPort)
                        put("udp", item.udpPort)
                        put("ts", item.lastConnectedMs)
                    },
                )
            }
            prefs[KEY_RECENT_HOSTS] = arr.toString()
        }
    }

    private fun parseHosts(raw: String): List<PcHost> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val ip = obj.optString("ip")
                    if (ip.isBlank()) continue
                    add(
                        PcHost(
                            name = obj.optString("name", ip),
                            ipAddress = ip,
                            tcpPort = obj.optInt("tcp", PcHost.DEFAULT_TCP_PORT),
                            udpPort = obj.optInt("udp", PcHost.DEFAULT_UDP_PORT),
                            lastConnectedMs = obj.optLong("ts", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private val KEY_POINTER_SENS = floatPreferencesKey("pointer_sensitivity")
        private val KEY_SCROLL_SENS = floatPreferencesKey("scroll_sensitivity")
        private val KEY_NATURAL_SCROLL = booleanPreferencesKey("natural_scrolling")
        private val KEY_TAP_TO_CLICK = booleanPreferencesKey("tap_to_click")
        private val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
        private val KEY_VOICE_COMMANDS = booleanPreferencesKey("voice_auto_commands")
        private val KEY_RECENT_HOSTS = stringPreferencesKey("recent_hosts_json")
    }
}
