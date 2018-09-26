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

class JavaFXAudioSource(location: URI): AudioSource(location)
{
    companion object
    {
        val supportedAudioFormats = listOf(".aif", ".aiff", ".fxm", ".flv", ".mp3", ".mp4", ".m4a", ".m4v", ".wav")
        fun isSupported(uri: URI) = supportedAudioFormats.any {uri.toString().endsWith(it, true)}
    }
    
    private var _mediaPlayer: MediaPlayer? = null
    private val mediaPlayer: MediaPlayer
        get()
        {
            synchronized(location)
            {
                init()
                return _mediaPlayer!!
            }
        }
    
    override val titleProperty = SimpleStringProperty(if(isFile(location)) File(location).nameWithoutExtension else "Unknown")
    override val artistProperty = SimpleStringProperty("Unknown")
    override val albumProperty = SimpleStringProperty("Unknown")
    override val genreProperty = SimpleStringProperty("")
    override val albumArtistProperty = SimpleStringProperty("")
    override val imageProperty = SimpleObjectProperty(defaultImage)
    override val trackCountProperty = SimpleIntegerProperty(0)
    override val trackNumberProperty = SimpleIntegerProperty(0)
    override val yearProperty = SimpleStringProperty("")
    override val durationProperty = SimpleObjectProperty(Duration.ZERO)
    override val currentTimeProperty = SimpleObjectProperty(Duration.ZERO)
    override val statusProperty = SimpleObjectProperty(MediaPlayer.Status.UNKNOWN)
    
    override var volume
        get() = mediaPlayer.volume
        set(value)
        {
            mediaPlayer.volume = value
        }
    
    override fun init()
    {
        synchronized(location)
        {
            if(_mediaPlayer === null)
            {
                val mediaSource = Media(location.toString())
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
                        "year" -> yearProperty.set(change.valueAdded.toString())
                    }
                }
                _mediaPlayer = MediaPlayer(mediaSource)
                _mediaPlayer!!.onEndOfMedia = Runnable {onEndOfMedia()}
            }
            _mediaPlayer!!.currentTimeProperty().addListener(InvalidationListener {currentTimeProperty.value = _mediaPlayer!!.currentTime})
            _mediaPlayer!!.statusProperty().addListener(InvalidationListener {statusProperty.value = _mediaPlayer!!.status})
            synchronized(AudioSource::class)
            {
                markActive(this)
            }
        }
        loadImage = {}
    }
    
    override fun play() = mediaPlayer.play()
    
    override fun pause() = mediaPlayer.pause()
    
    override fun stop() = mediaPlayer.stop()
    
    override fun seek(position: Duration) = mediaPlayer.seek(position)
    
    // should only be called in the companion object
    override fun dispose()
    {
        synchronized(location)
        {
            _mediaPlayer?.dispose()
            _mediaPlayer = null
        }
    }
}