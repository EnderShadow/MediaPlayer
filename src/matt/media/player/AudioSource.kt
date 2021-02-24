package matt.media.player

import javafx.application.Platform
import javafx.beans.property.*
import javafx.beans.value.ChangeListener
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.*
import java.net.URI
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO
import kotlin.concurrent.thread

abstract class AudioSource(val location: URI, val uuid: UUID, val dateAdded: LocalDateTime, title: String, artist: String, album: String, genre: String, albumArtist: String, trackCount: Int, trackNumber: Int, year: String, duration: Duration)
{
    companion object
    {
        private val activeSources = LinkedList<AudioSource>()
        private val imageLoadQueue = ConcurrentLinkedQueue<Pair<AudioSource, SimpleObjectProperty<Image>>>()
        
        init {
            thread(start = true, isDaemon = true, name = "Image Loading Thread") {
                while(true) {
                    val imageToLoad = imageLoadQueue.poll()
                    if(imageToLoad != null) {
                        val (audioSource, imageProperty) = imageToLoad
                        val imageCache = cachePath.resolve("artwork/${audioSource.uuid}.png")
                        if(Files.exists(imageCache)) {
                            val image = squareAndCache(SwingFXUtils.toFXImage(ImageIO.read(imageCache.toFile()), null))
                            Platform.runLater {imageProperty.value = image}
                        }
                        else {
                            Files.createDirectories(imageCache.parent)
                            try {
                                val artworkData = AudioFileIO.read(File(audioSource.location)).tagOrCreateAndSetDefault.artworkList.firstOrNull {it != null}?.binaryData
                                if(artworkData != null) {
                                    val rawImage = ImageIO.read(ByteArrayInputStream(artworkData))
                                    ioThreadPool.submit {
                                        ImageIO.write(rawImage, "png", imageCache.toFile())
                                    }
                                    val image = SwingFXUtils.toFXImage(rawImage, null)
                                    if(image.exception == null) {
                                        val squaredImage = squareAndCache(image)
                                        Platform.runLater {imageProperty.value = squaredImage}
                                    } else {
                                        Platform.runLater(audioSource::init)
                                    }
                                }
                            } catch(e: Exception) {
                                Platform.runLater(audioSource::init)
                            }
                        }
                    }
                    else {
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
        
        private fun loadImage(audioSource: AudioSource, imageProperty: SimpleObjectProperty<Image>)
        {
            imageLoadQueue.add(Pair(audioSource, imageProperty))
        }
    }
    
    val titleProperty = SimpleStringProperty(title)
    val artistProperty = SimpleStringProperty(artist)
    val albumProperty = SimpleStringProperty(album)
    val genreProperty = SimpleStringProperty(genre)
    val albumArtistProperty = SimpleStringProperty(albumArtist)
    val imageProperty = SimpleObjectProperty(defaultImage)
    val trackCountProperty = SimpleIntegerProperty(trackCount)
    val trackNumberProperty = SimpleIntegerProperty(trackNumber)
    val yearProperty = SimpleStringProperty(year)
    val durationProperty = SimpleObjectProperty(duration)
    val currentTimeProperty = SimpleObjectProperty(Duration.ZERO)
    val statusProperty = SimpleObjectProperty(MediaPlayer.Status.UNKNOWN)
    
    abstract var volume: Double
    
    var onEndOfMedia: () -> Unit = {}
    
    var loadImage: () -> Unit = {}
        protected set
        get() = if(!initializing) field else ({})
    
    init
    {
        val metadataChangeListener = ChangeListener<Any> {_, old, new ->
            if(old != new && new != null)
                MediaLibrary.markDirty()
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
        
        @Suppress("LeakingThis")
        if(this !is NOPAudioSource)
        {
            loadImage = {
                loadImage = {}
                loadImage(this, imageProperty)
            }
        }
    }
    
    fun readMetadataFromSource() {
        if(isFile(location))
        {
            val audioFile = AudioFileIO.read(File(location))
        
            val header = audioFile.audioHeader
            durationProperty.value = Duration.seconds(header.preciseTrackLength)
            val tag = audioFile.tagOrCreateAndSetDefault
        
            tag.getFirst(FieldKey.TITLE).let {
                if(it.isNotBlank())
                    titleProperty.value = it
            }
            tag.getFirst(FieldKey.ARTIST).let {if(it.isNotBlank()) artistProperty.value = it}
            tag.getFirst(FieldKey.ALBUM).let {if(it.isNotBlank()) albumProperty.value = it}
            tag.getFirst(FieldKey.GENRE).let {if(it.isNotBlank()) genreProperty.value = it}
            tag.getFirst(FieldKey.ALBUM_ARTIST).let {if(it.isNotBlank()) albumArtistProperty.value = it}
            tag.getFirst(FieldKey.TRACK_TOTAL).let {if(it.isNotBlank()) trackCountProperty.value = it.toInt()}
            tag.getFirst(FieldKey.TRACK).let {if(it.isNotBlank()) trackNumberProperty.value = it.toInt()}
            tag.getFirst(FieldKey.YEAR).let {if(it.isNotBlank()) yearProperty.value = it}
            
            MediaLibrary.markDirty()
        }
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