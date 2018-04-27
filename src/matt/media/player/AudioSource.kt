package matt.media.player

import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import java.net.URI

class AudioSource(val location: URI)
{
    constructor(location: String): this(URI(location))
    
    val mediaSource = Media(location.toString())
    val mediaPlayer = MediaPlayer(mediaSource)
    
    override fun toString(): String
    {
        return location.path
    }
}