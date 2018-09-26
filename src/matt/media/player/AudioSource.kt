package matt.media.player

import javafx.beans.property.*
import javafx.scene.image.Image
import javafx.scene.media.MediaException
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.IllegalArgumentException
import java.net.URI
import java.util.*

abstract class AudioSource(val location: URI)
{
    companion object
    {
        private val activeSources = LinkedList<AudioSource>()
        
        // must only be called when synchronized on AudioSource::class
        fun markActive(audioSource: AudioSource)
        {
            activeSources.remove(audioSource)
            activeSources.add(audioSource)
            while(activeSources.size > Config.maxLoadedSources)
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
    
        /**
         * Creates an instance of AudioSource based on the provided uri
         */
        fun create(uri: URI): AudioSource
        {
            if(JavaFXAudioSource.isSupported(uri))
            {
                try
                {
                    return JavaFXAudioSource(uri)
                }
                catch(me: MediaException) {}
            }
            throw IllegalArgumentException("Unsupported audio format: $uri")
        }
    }
    
    abstract val titleProperty: StringProperty
    abstract val artistProperty: StringProperty
    abstract val albumProperty: StringProperty
    abstract val genreProperty: StringProperty
    abstract val albumArtistProperty: StringProperty
    abstract val imageProperty: ObjectProperty<Image>
    abstract val trackCountProperty: IntegerProperty
    abstract val trackNumberProperty: IntegerProperty
    abstract val yearProperty: StringProperty
    abstract val durationProperty: ObjectProperty<Duration>
    abstract val currentTimeProperty: ObjectProperty<Duration>
    abstract val statusProperty: ObjectProperty<MediaPlayer.Status>
    
    abstract var volume: Double
    
    var onEndOfMedia: () -> Unit = {}
    
    var loadImage: () -> Unit = {}
        protected set
    
    init
    {
        var loaded = false
        if(isFile(location)) try
        {
            val audioFile = AudioFileIO.read(File(location))
            
            val header = audioFile.audioHeader
            @Suppress("LeakingThis")
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
            @Suppress("LeakingThis")
            init()
        }
    }
    
    protected abstract fun init()
    protected abstract fun dispose()
    
    abstract fun play()
    abstract fun pause()
    abstract fun stop()
    abstract fun seek(position: Duration)
    
    override fun toString() = location.toString()
}