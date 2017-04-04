package matt.media.player;

public class SongHandle extends MediaHandle
{
	private AudioSource audioSource;
	
	public SongHandle(AudioSource audioSource, MediaHandle prev, MediaHandle next)
	{
		super(prev, next);
		this.audioSource = audioSource;
	}
	
	public SongHandle(AudioSource audioSource, MediaHandle prev)
	{
		this(audioSource, prev, null);
	}
	
	public AudioSource getCurrentAudioSource()
	{
		return audioSource;
	}

	@Override
	public AudioSource getAudioSource(int index)
	{
		return audioSource;
	}

	@Override
	public Playlist getPlaylist()
	{
		throw new UnsupportedOperationException("Cannot get playlist from song handle");
	}
}