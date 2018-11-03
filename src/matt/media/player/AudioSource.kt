package matt.media.player

import javafx.beans.property.*
import javafx.beans.value.ChangeListener
import javafx.scene.image.Image
import javafx.scene.media.MediaException
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.*
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
            if(VLCAudioSource.isSupported(uri))
            {
                try
                {
                    val vlcas = VLCAudioSource(uri) as AudioSource
                    if(!vlcas.loaded)
                        vlcas.init()
                    return vlcas
                }
                catch(e: Exception) {}
            }
            if(JavaFXAudioSource.isSupported(uri))
            {
                try
                {
                    val jfxas = JavaFXAudioSource(uri) as AudioSource
                    if(!jfxas.loaded)
                        jfxas.init()
                    return jfxas
                }
                catch(me: MediaException) {}
            }
            throw IllegalArgumentException("Unsupported audio format: $uri")
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
    val durationProperty = SimpleObjectProperty(Duration.ZERO)
    val currentTimeProperty = SimpleObjectProperty(Duration.ZERO)
    val statusProperty = SimpleObjectProperty(MediaPlayer.Status.UNKNOWN)
    
    abstract var volume: Double
    
    var onEndOfMedia: () -> Unit = {}
    
    var loadImage: () -> Unit = {}
        protected set
        get() = if(!initializing) field else ({})
    
    protected var loaded = false
    
    private val metadataFile = File(Config.mediaDirectory, "${hexString(location.hashCode())}.metadata")
    
    init
    {
        if(metadataFile.exists()) try
        {
            DataInputStream(metadataFile.inputStream().buffered()).use {
                if(location.toString() != it.readUTF())
                    throw IOException("Hash collision for URI detected")
                titleProperty.value = it.readUTF()
                artistProperty.value = it.readUTF()
                albumProperty.value = it.readUTF()
                genreProperty.value = it.readUTF()
                albumArtistProperty.value = it.readUTF()
                trackCountProperty.value = it.readInt()
                trackNumberProperty.value = it.readInt()
                yearProperty.value = it.readUTF()
                durationProperty.value = Duration.millis(it.readDouble())
            }
            loaded = true
            loadImage = {
                try
                {
                    val artworkData = AudioFileIO.read(File(location)).tagOrCreateAndSetDefault.firstArtwork?.binaryData
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
                }
                catch(e: Exception)
                {
                    init()
                }
                loadImage = {}
            }
        }
        catch(ioe: IOException)
        {
            System.err.println("Failed to read song metadata from file")
        }
    
        val metadataChangeListener = ChangeListener<Any> {_, old, new ->
            if(old != new && new != null) try
            {
                // Prevents write attempts from overlapping
                synchronized(location) {
                    DataOutputStream(metadataFile.outputStream().buffered()).use {
                        it.writeUTF(location.toString())
                        it.writeUTF(titleProperty.value)
                        it.writeUTF(artistProperty.value)
                        it.writeUTF(albumProperty.value)
                        it.writeUTF(genreProperty.value)
                        it.writeUTF(albumArtistProperty.value)
                        it.writeInt(trackCountProperty.value)
                        it.writeInt(trackNumberProperty.value)
                        it.writeUTF(yearProperty.value)
                        it.writeDouble(durationProperty.value.toMillis())
                    }
                }
            }
            catch(ioe: IOException)
            {
                System.err.println("Failed to write song metadata to file")
            }
        }
        
        titleProperty.addListener(metadataChangeListener)
        artistProperty.addListener(metadataChangeListener)
        albumProperty.addListener(metadataChangeListener)
        genreProperty.addListener(metadataChangeListener)
        albumArtistProperty.addListener(metadataChangeListener)
        trackCountProperty.addListener(metadataChangeListener)
        trackNumberProperty.addListener(metadataChangeListener)
        yearProperty.addListener(metadataChangeListener)
        durationProperty.addListener(metadataChangeListener)
        
        if(!loaded && isFile(location)) try
        {
            val audioFile = AudioFileIO.read(File(location))
            
            val header = audioFile.audioHeader
            durationProperty.value = Duration.seconds(header.preciseTrackLength)
            val tag = audioFile.tagOrCreateAndSetDefault
            if(tag != null)
            {
                tag.getFirst(FieldKey.TITLE).let {
                    if(it.isNotBlank())
                        titleProperty.value = it
                    else
                    {
                        tag.setField(FieldKey.TITLE, titleProperty.value)
                        audioFile.commit()
                    }
                }
                tag.getFirst(FieldKey.ARTIST).let {if(it.isNotBlank()) artistProperty.value = it}
                tag.getFirst(FieldKey.ALBUM).let {if(it.isNotBlank()) albumProperty.value = it}
                tag.getFirst(FieldKey.GENRE).let {if(it.isNotBlank()) genreProperty.value = it}
                tag.getFirst(FieldKey.ALBUM_ARTIST).let {if(it.isNotBlank()) albumArtistProperty.value = it}
                tag.getFirst(FieldKey.TRACK_TOTAL).let {if(it.isNotBlank()) trackCountProperty.value = it.toInt()}
                tag.getFirst(FieldKey.TRACK).let {if(it.isNotBlank()) trackNumberProperty.value = it.toInt()}
                tag.getFirst(FieldKey.YEAR).let {if(it.isNotBlank()) yearProperty.value = it}
                
                loadImage = {
                    val artworkData = tag.firstArtwork?.binaryData
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
    }
    
    fun deleteMetadata()
    {
        metadataFile.delete()
    }
    
    protected abstract fun init()
    protected abstract fun dispose()
    
    abstract fun play()
    abstract fun pause()
    abstract fun stop()
    abstract fun seek(position: Duration)
    
    override fun toString() = location.toString()
    override fun equals(other: Any?) = other is AudioSource && other.location == location
    override fun hashCode() = location.hashCode()
}