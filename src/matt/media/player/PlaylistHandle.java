package matt.media.player;

public class PlaylistHandle extends MediaHandle
{
	private Playlist playlistSource;
	private int currentSong;
	
	public PlaylistHandle(Playlist audioSource, MediaHandle prev, MediaHandle next)
	{
		super(prev, next);
		this.playlistSource = audioSource;
		currentSong = 0;
	}
	
	public PlaylistHandle(Playlist audioSource, MediaHandle prev)
	{
		this(audioSource, prev, null);
	}
	
	public AudioSource getCurrentAudioSource()
	{
		return playlistSource.isEmpty() ? null : playlistSource.getSong(currentSong).getCurrentAudioSource();
	}

	@Override
	public AudioSource getAudioSource(int index)
	{
		return playlistSource.getSong(index).getCurrentAudioSource();
	}

	@Override
	public Playlist getPlaylist()
	{
		return playlistSource;
	}
	
	public boolean hasMoreSongs()
	{
		return currentSong < playlistSource.getSize();
	}
	
	public boolean isAtBeginning()
	{
		return currentSong == 0;
	}
	
	public void nextSong()
	{
		currentSong++;
	}
	
	public void previousSong()
	{
		currentSong--;
	}
	
	public void resetIndex()
	{
		currentSong = 0;
	}
	
	public void setIndex(int index)
	{
		int size = playlistSource.getSize();
		if(index <= -size || index >= size)
			throw new IllegalArgumentException("index cannot be less than " + (1 - size) + " or larger than " + (size - 1) + ", it was " + index);
		if(index < 0)
			currentSong = index + size;
		else
			currentSong = index;
	}
}