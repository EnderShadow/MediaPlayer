package matt.media.player

import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.ArrayList

object Config
{
    private val configFile = File(System.getProperty("user.home") + File.separator + "Media Player" + File.separator + "config.cfg")
    private const val VERSION = "1.0.0"
    
    var mediaDirectory = File(System.getProperty("user.home") + File.separator + "Media Player" + File.separator + "Media")
    var maxImageSize = 300
    var unloadInvisibleSongs = true

    fun load()
    {
        if(!configFile.exists())
        {
            configFile.parentFile.mkdirs()
            updateConfig()
        }
        else
        {
            try
            {
                val configData = Files.readAllLines(configFile.toPath())

                configData.removeIf {str -> str.trim().run {startsWith("#") || isEmpty()}}
                val version = configData.removeAt(0).split(':')[1].trim()

                // do anything with the version here

                for(str in configData)
                {
                    val line = str.split(':', limit=2)
                    when(line[0].trim())
                    {
                        "mediaDirectory" -> mediaDirectory = File(line[1].trim())
                        "maxImageSize" -> maxImageSize = line[1].trim().toInt()
                        "unloadInvisibleSongs" -> unloadInvisibleSongs = line[1].trim().toBoolean()
                        else -> println("Unknown setting in config\t$str")
                    }
                }
            }
            catch(ioe: IOException)
            {
                System.err.println("Unable to read config. Using default config.")
            }
        }

        if(!mediaDirectory.exists())
            mediaDirectory.mkdirs()
    }

    fun updateConfig()
    {
        val configData = ArrayList<String>()
        configData.add("version: $VERSION")
        configData.add("mediaDirectory: ${mediaDirectory.path}")
        configData.add("maxImageSize: $maxImageSize")
        configData.add("unloadInvisibleSongs: $unloadInvisibleSongs")
        
        println("Config updated")
        configData.forEach {println(it)}
        println()
        
        try
        {
            Files.write(configFile.toPath(), configData, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        }
        catch(ioe: IOException)
        {
            System.err.println("Unable to create or update config.")
        }
    }
}
