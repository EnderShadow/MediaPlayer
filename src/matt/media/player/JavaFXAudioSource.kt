package matt.media.player

import javafx.beans.InvalidationListener
import javafx.collections.MapChangeListener
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.net.URI
import java.nio.file.Files
import java.util.*
import javax.imageio.ImageIO

class JavaFXAudioSource(location: URI, uuid: UUID, title: String, artist: String, album: String, genre: String, albumArtist: String, trackCount: Int, trackNumber: Int, year: String, duration: Duration): AudioSource(location, uuid, title, artist, album, genre, albumArtist, trackCount, trackNumber, year, duration)
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
                        "image" -> {
                            val fxImage = change.valueAdded as Image
                            val imageCache = cachePath.resolve("artwork/$uuid.png")
                            if(Files.notExists(imageCache)) {
                                Files.createDirectories(imageCache.parent)
                                ioThreadPool.submit {
                                    ImageIO.write(SwingFXUtils.fromFXImage(fxImage, null), "png", imageCache.toFile())
                                }
                            }
                            imageProperty.set(squareAndCache(fxImage))
                        }
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