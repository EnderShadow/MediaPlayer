package matt.media.player

import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.ArrayList

object Config
{
    private val configFile = File(System.getProperty("user.home") + File.separator + "MediaPlayer" + File.separator + "config.cfg")
    private const val VERSION = "1.0.0"
    
    var mediaDirectory = File(System.getProperty("user.home") + File.separator + "MediaPlayer" + File.separator + "Media")
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
                val configData = Files.readAllLines(configFile.toPath(), Charset.forName("UTF-8"))

                configData.removeIf {str -> str.trim {it <= ' '}.startsWith("#") || str.trim {it <= ' '}.isEmpty()}
                val version = configData.removeAt(0).split(":".toRegex()).dropLastWhile {it.isEmpty()}.toTypedArray()[1].trim {it <= ' '}

                // do anything with the version here

                for(str in configData)
                {
                    val line = str.split(":".toRegex(), 2).toTypedArray()
                    when(line[0].trim {it <= ' '})
                    {
                        "mediaDirectory" -> mediaDirectory = File(line[1].trim {it <= ' '})
                        "maxImageSize" -> maxImageSize = Integer.parseInt(line[1].trim {it <= ' '})
                        "unloadInvisibleSongs" -> unloadInvisibleSongs = java.lang.Boolean.parseBoolean(line[1].trim {it <= ' '})
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
            Files.write(configFile.toPath(), configData, Charset.forName("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        }
        catch(ioe: IOException)
        {
            System.err.println("Unable to create or update config.")
        }
    }
}
