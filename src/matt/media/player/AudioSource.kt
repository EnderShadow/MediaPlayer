package matt.media.player

import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import java.net.URI

class AudioSource(val location: URI)
{
    constructor(location: String): this(URI(location))
    
    val mediaSource = Media(location.toString())
    private var _mediaPlayer: MediaPlayer? = null
    val mediaPlayer: MediaPlayer
        get()
        {
            synchronized(mediaSource)
            {
                if(_mediaPlayer === null)
                    _mediaPlayer = MediaPlayer(mediaSource)
                return _mediaPlayer!!
            }
        }
    
    fun init()
    {
        synchronized(mediaSource)
        {
            if(_mediaPlayer === null)
                _mediaPlayer = MediaPlayer(mediaSource)
        }
    }
    
    fun dispose()
    {
        synchronized(mediaSource)
        {
            _mediaPlayer?.dispose()
            _mediaPlayer = null
        }
    }
    
    override fun toString(): String
    {
        return location.path
    }
}