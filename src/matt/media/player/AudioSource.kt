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
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.util.*

class AudioSource(val location: URI)
{
    companion object
    {
        private val activeSources = LinkedList<AudioSource>()
        
        // must only be called when synchronized on location
        fun markActive(audioSource: AudioSource)
        {
            activeSources.remove(audioSource)
            activeSources.add(audioSource)
            if(activeSources.size > 10)
            {
                val oldSource = if(Player.currentlyPlaying.value is SongHandle && Player.currentlyPlaying.value?.getCurrentAudioSource() == activeSources[0])
                {
                    activeSources.removeAt(1)
                }
                else
                {
                    activeSources.removeFirst()
                }
                oldSource.dispose()
            }
        }
    }
    
    constructor(location: String): this(URI(location))
    
    private var _mediaSource: Media? = null
    private val mediaSource: Media
        get()
        {
            if(_mediaSource === null)
            {
                _mediaSource = Media(location.toString())
        
                _mediaSource!!.durationProperty().addListener(InvalidationListener {durationProperty.value = _mediaSource?.duration})
        
                _mediaSource!!.metadata.addListener {change: MapChangeListener.Change<out String, out Any> ->
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
            }
    
            return _mediaSource!!
        }
    private var _mediaPlayer: MediaPlayer? = null
    val mediaPlayer: MediaPlayer
        get()
        {
            synchronized(location)
            {
                init()
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
    val yearProperty = SimpleStringProperty("")
    val durationProperty = SimpleObjectProperty<Duration>(Duration.ZERO)
    
    lateinit var loadImage: () -> Unit
        private set
    
    init
    {
        var loaded = false
        if(isFile(location)) try
        {
            val audioFile = AudioFileIO.read(File(location))
        
            val header = audioFile.audioHeader
            durationProperty.value = Duration.seconds(header.preciseTrackLength)
        
            val tag = audioFile.tag
            if(tag != null)
            {
                tag.getFirst(FieldKey.TITLE).let {if(it.isNotBlank()) titleProperty.value = it}
                tag.getFirst(FieldKey.ARTIST).let {if(it.isNotBlank()) artistProperty.value = it}
                tag.getFirst(FieldKey.ALBUM).let {if(it.isNotBlank()) albumProperty.value = it}
                tag.getFirst(FieldKey.GENRE).let {if(it.isNotBlank()) genreProperty.value = it}
                tag.getFirst(FieldKey.ALBUM_ARTIST).let {if(it.isNotBlank()) albumArtistProperty.value = it}
                tag.getFirst(FieldKey.TRACK_TOTAL).let {if(it.isNotBlank()) trackCountProperty.value = it.toInt()}
                tag.getFirst(FieldKey.TRACK).let {if(it.isNotBlank()) trackNumberProperty.value = it.toInt()}
                tag.getFirst(FieldKey.YEAR).let {if(it.isNotBlank()) yearProperty.value = it}
                
                val artworkData = tag.firstArtwork?.binaryData
                loadImage = {
                    if(artworkData != null)
                    {
                        val image = Image(ByteArrayInputStream(artworkData))
                        if(image.exception == null)
                        {
                            imageProperty.value = squareAndCache(image)
                        }
                        else
                        {
                            init()
                        }
                    }
                    loadImage = {}
                }
                loaded = true
            }
        }
        catch(e: Throwable)
        {
            System.err.println("Failed to read metadata from audio source. Fully loading audio source instead.")
        }
        
        if(!loaded)
        {
            init()
        }
    }
    
    private fun init()
    {
        synchronized(location)
        {
            if(_mediaPlayer === null)
                _mediaPlayer = MediaPlayer(mediaSource)
            markActive(this)
        }
        loadImage = {}
    }
    
    // should only be called in the companion object
    private fun dispose()
    {
        synchronized(location)
        {
            _mediaPlayer?.dispose()
            _mediaPlayer = null
            _mediaSource = null
        }
    }
    
    override fun toString(): String
    {
        return location.toString()
    }
}