package matt.media.player

import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.MapChangeListener
import javafx.scene.image.Image
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.io.File
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
    
    val titleProperty = SimpleStringProperty(if(isFile(location)) File(location).nameWithoutExtension else "Unknown")
    val artistProperty = SimpleStringProperty("Unknown")
    val albumProperty = SimpleStringProperty("Unknown")
    val genreProperty = SimpleStringProperty("")
    val albumArtistProperty = SimpleStringProperty("")
    val imageProperty = SimpleObjectProperty(defaultImage)
    val trackCountProperty = SimpleIntegerProperty(0)
    val trackNumberProperty = SimpleIntegerProperty(0)
    val yearProperty = SimpleIntegerProperty(0)
    val durationProperty = SimpleObjectProperty<Duration>(Duration.ZERO)
    
    init
    {
        mediaSource.durationProperty().addListener(InvalidationListener {durationProperty.value = mediaSource.duration})
    
        mediaSource.metadata.addListener {change: MapChangeListener.Change<out String, out Any> ->
            if(change.valueAdded is String && (change.valueAdded as String).isBlank())
                return@addListener
            when(change.key.toLowerCase())
            {
                "title" -> titleProperty.set(change.valueAdded as String)
                "artist" -> artistProperty.set(change.valueAdded as String)
                "album" -> albumProperty.set(change.valueAdded as String)
                "genre" -> genreProperty.set(change.valueAdded as String)
                "album artist" -> albumArtistProperty.set(change.valueAdded as String)
                "image" -> imageProperty.set(squareAndCache(change.valueAdded as Image))
                "track count" -> trackCountProperty.set(change.valueAdded as Int)
                "track number" -> trackNumberProperty.set(change.valueAdded as Int)
                "year" -> yearProperty.set(change.valueAdded as Int)
            }
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
        return location.toString()
    }
}