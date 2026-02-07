package com.skylake.skytv.jgorunner.core.data

import android.content.Context
import com.google.gson.Gson
import com.skylake.skytv.jgorunner.data.SkySharedPref
import java.io.File

class JTVConfigurationManager private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: JTVConfigurationManager? = null

        // Singleton
        fun getInstance(context: Context): JTVConfigurationManager {
            return instance ?: synchronized(this) {
                instance
                    ?: JTVConfigurationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // Configuration
    private val preferenceManager = SkySharedPref.getInstance(context.applicationContext)
    private val filesDir = context.filesDir
    private val configDir = File(filesDir, "jiotv_go")
    private val configFileCandidates = listOf(
        "jiotv-config.json",
        "jtv_config.json",
        "jiotv-config.toml",
        "jiotv-config.yml",
        "jiotv_go.toml"
    )
    var jtvConfiguration = readFromJTVConfiguration()
        private set

    private fun resolveConfigLocation(): String {
        val current = preferenceManager.myPrefs.jtvConfigLocation?.takeIf { it.isNotBlank() }
        if (current != null) {
            val file = File(current)
            if (file.exists()) return file.absolutePath
        }

        val searchDirs = listOf(configDir, filesDir)
        for (dir in searchDirs) {
            for (name in configFileCandidates) {
                val candidate = File(dir, name)
                if (candidate.exists()) {
                    preferenceManager.myPrefs.jtvConfigLocation = candidate.absolutePath
                    preferenceManager.savePreferences()
                    return candidate.absolutePath
                }
            }
        }

        configDir.mkdirs()
        val fallback = File(configDir, "jiotv-config.json")
        preferenceManager.myPrefs.jtvConfigLocation = fallback.absolutePath
        preferenceManager.savePreferences()
        return fallback.absolutePath
    }

    private fun readFromJTVConfiguration(): JTVConfiguration {
        val jtvConfigLocation = resolveConfigLocation()
        val jtvConfigFile = File(jtvConfigLocation)
        if (!jtvConfigFile.exists())
            return JTVConfiguration()

        if (!jtvConfigFile.name.endsWith(".json", ignoreCase = true)) {
            return JTVConfiguration()
        }

        return try {
            Gson().fromJson(jtvConfigFile.readText(), JTVConfiguration::class.java)
        } catch (e: Exception){
            JTVConfiguration()
        }
    }

    fun saveJTVConfiguration() {
        var jtvConfigLocation = resolveConfigLocation()
        configDir.mkdirs()

        val currentFile = File(jtvConfigLocation)
        if (!currentFile.name.endsWith(".json", ignoreCase = true)) {
            val jsonFallback = File(configDir, "jiotv-config.json")
            jtvConfigLocation = jsonFallback.absolutePath
            preferenceManager.myPrefs.jtvConfigLocation = jtvConfigLocation
            preferenceManager.savePreferences()
        }

        if (jtvConfiguration.pathPrefix != filesDir.absolutePath) {
            handlePathPrefixMismatch()
        }

        val jtvConfigFile = File(jtvConfigLocation)
        if (!jtvConfigFile.exists()) {
            jtvConfigFile.parentFile?.mkdirs()
            jtvConfigFile.createNewFile()
        }

        jtvConfigFile.writeText(Gson().toJson(jtvConfiguration))
    }

    private fun handlePathPrefixMismatch() {
        jtvConfiguration.pathPrefix = filesDir.absolutePath
    }

    fun deleteJTVConfiguration() {
        val jtvConfigLocation = preferenceManager.myPrefs.jtvConfigLocation
        if (!jtvConfigLocation.isNullOrBlank()) {
            val jtvConfigFile = File(jtvConfigLocation)
            if (jtvConfigFile.exists()) jtvConfigFile.delete()
        }
        for (name in configFileCandidates) {
            val f = File(configDir, name)
            if (f.exists()) f.delete()
        }
        jtvConfiguration = JTVConfiguration()
    }
}
