package matt.media.player

import javafx.util.Duration
import java.net.URI
import java.util.*

class NOPAudioSource(location: URI, uuid: UUID): AudioSource(location, uuid)
{
    override var volume: Double
        get() = Player.volume
        set(_) {}
    
    override fun init()
    {
        loaded = true
    }
    
    override fun dispose() {}
    override fun play() {}
    override fun pause() {}
    override fun stop() {}
    override fun seek(position: Duration) {}
}