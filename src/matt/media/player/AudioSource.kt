package matt.media.player

import javafx.application.Platform
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
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

abstract class AudioSource(val location: URI, val uuid: UUID)
{
    companion object
    {
        private val activeSources = LinkedList<AudioSource>()
        private val imageLoadQueue = ConcurrentLinkedQueue<Pair<AudioSource, SimpleObjectProperty<Image>>>()
        
        init
        {
            thread(start = true, isDaemon = true, name = "Image Loading Thread") {
                while(true)
                {
                    val imageToLoad = imageLoadQueue.poll()
                    if(imageToLoad != null)
                    {
                        val (audioSource, imageProperty) = imageToLoad
                        try
                        {
                            val artworkData = AudioFileIO.read(File(audioSource.location)).tagOrCreateAndSetDefault.artworkList.firstOrNull {it != null}?.binaryData
                            if(artworkData != null)
                            {
                                val image = Image(ByteArrayInputStream(artworkData))
                                if(image.exception == null)
                                {
                                    val squaredImage = squareAndCache(image)
                                    Platform.runLater {imageProperty.value = squaredImage}
                                }
                                else
                                {
                                    Platform.runLater(audioSource::init)
                                }
                            }
                        }
                        catch(e: Exception)
                        {
                            Platform.runLater(audioSource::init)
                        }
                    }
                    else
                    {
                        Thread.yield()
                    }
                }
            }
        }
        
        // must only be called when synchronized on AudioSource::class
        fun markActive(audioSource: AudioSource)
        {
            activeSources.remove(audioSource)
            activeSources.add(audioSource)
            while(activeSources.size > Config[ConfigKey.MAX_LOADED_SOURCES] as Int)
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
        fun create(uri: URI, uuid: UUID? = null): AudioSource
        {
            @Suppress("NAME_SHADOWING")
            val uuid = uuid ?: UUID.randomUUID()
            
            if(VLCAudioSource.isSupported(uri))
            {
                try
                {
                    val vlcas = VLCAudioSource(uri, uuid) as AudioSource
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
                    val jfxas = JavaFXAudioSource(uri, uuid) as AudioSource
                    if(!jfxas.loaded)
                        jfxas.init()
                    return jfxas
                }
                catch(me: MediaException) {}
            }
            throw IllegalArgumentException("Unsupported audio format: $uri")
        }
        
        private fun loadImage(audioSource: AudioSource, imageProperty: SimpleObjectProperty<Image>)
        {
            imageLoadQueue.add(Pair(audioSource, imageProperty))
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
    
    private val metadataFile = mediaDirectory.resolve("$uuid.metadata")
    
    init
    {
        if(Files.exists(metadataFile)) try
        {
            DataInputStream(Files.newInputStream(metadataFile, StandardOpenOption.READ).buffered()).use {
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
        }
        catch(ioe: IOException)
        {
            System.err.println("Failed to read song metadata from file")
        }
        
        @Suppress("LeakingThis")
        if(this !is NOPAudioSource)
        {
            val metadataChangeListener = ChangeListener<Any> {_, old, new ->
                if(old != new && new != null) try
                {
                    // Prevents write attempts from overlapping
                    synchronized(location) {
                        DataOutputStream(Files.newOutputStream(metadataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).buffered()).use {
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
    
            if(!loaded && isFile(location))
            {
                val audioFile = AudioFileIO.read(File(location))
        
                val header = audioFile.audioHeader
                durationProperty.value = Duration.seconds(header.preciseTrackLength)
                val tag = audioFile.tagOrCreateAndSetDefault
        
                tag.getFirst(FieldKey.TITLE).let {
                    if(it.isNotBlank())
                    {
                        titleProperty.value = it
                    }
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
        
                loaded = true
            }
    
            loadImage = {
                loadImage = {}
                loadImage(this, imageProperty)
            }
        }
    }
    
    fun deleteMetadata()
    {
        Files.deleteIfExists(metadataFile)
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