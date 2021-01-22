package matt.media.player

import javafx.util.Duration
import java.net.URI
import java.util.*

class NOPAudioSource(location: URI, uuid: UUID, title: String, artist: String, album: String, genre: String, albumArtist: String, trackCount: Int, trackNumber: Int, year: String, duration: Duration): AudioSource(location, uuid, title, artist, album, genre, albumArtist, trackCount, trackNumber, year, duration)
{
    constructor(location: URI, uuid: UUID): this(location, uuid, "", "", "", "", "", 0, 0, "", Duration.ZERO)
    
    override var volume: Double
        get() = Player.volume
        set(_) {}
    
    override fun init() {}
    
    override fun dispose() {}
    override fun play() {}
    override fun pause() {}
    override fun stop() {}
    override fun seek(position: Duration) {}
}