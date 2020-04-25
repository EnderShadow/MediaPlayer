package matt.media.player

import org.json.JSONObject
import java.nio.file.*

data class ConfigKey(val keyString: String) {
    companion object {
        val DATA_DIRECTORY = ConfigKey("dataDirectory")
        val VLC_DIRECTORY = ConfigKey("vlcDirectory")
        val MAX_IMAGE_SIZE = ConfigKey("maxImageSize")
        val MAX_LOADED_SOURCES = ConfigKey("maxLoadedSources")
        val SUPPRESS_VLC_MESSAGE = ConfigKey("suppressVLCMessage")
        val DEFAULT_PLAYLIST_ADD_MODE = ConfigKey("defaultPlaylistAddMode")
    }
}

object Config {
    private val configPath = Paths.get("${System.getProperty("user.home")}/.config/Media Player/config.json")
    private const val VERSION = "1.0.0"
    
    private val configuration = JSONObject()
    
    init {
        configuration["version"] = VERSION
        configuration[ConfigKey.DATA_DIRECTORY.keyString] = "${System.getProperty("user.home")}/Media Player"
        configuration[ConfigKey.VLC_DIRECTORY.keyString] = ""
        configuration[ConfigKey.MAX_IMAGE_SIZE.keyString] = 100
        configuration[ConfigKey.MAX_LOADED_SOURCES.keyString] = 10
        configuration[ConfigKey.SUPPRESS_VLC_MESSAGE.keyString] = false
        configuration[ConfigKey.DEFAULT_PLAYLIST_ADD_MODE.keyString] = "REFERENCE"
    }
    
    operator fun set(key: ConfigKey, value: Any) {
        configuration[key.keyString] = value
    }
    
    operator fun set(key: ConfigKey, value: Path) {
        this[key] = value.toString()
    }
    
    operator fun get(key: ConfigKey): Any {
        return configuration[key.keyString]
    }
    
    fun getPath(key: ConfigKey): Path = Paths.get(getString(key))
    fun getInt(key: ConfigKey) = this[key] as Int
    fun getBoolean(key: ConfigKey) = this[key] as Boolean
    fun getString(key: ConfigKey) = this[key] as String
    
    fun load() {
        // if it doesn't exist then defaults will be used until a change to them is made
        if(Files.notExists(configPath))
            return
        
        // load the config and replace default values with the saved values. Unrecognized values are preserved
        val readConfig = JSONObject(Files.readAllLines(configPath).joinToString("\n"))
        readConfig.keys().forEach {
            configuration[it] = readConfig[it]
        }
    }
    
    fun save() {
        if(Files.notExists(configPath.parent))
            Files.createDirectories(configPath.parent)
        
        // atomic move to ensure that either the old or new data is written
        val tempConfigPath = configPath.resolveSibling("${configPath.fileName}.new")
        Files.newOutputStream(tempConfigPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC).use {
            it.write(configuration.toString(4).toByteArray())
            it.flush()
        }
        Files.move(tempConfigPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}