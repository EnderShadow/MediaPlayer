package matt.media.player;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

public class Playlist
{
	public static final String EXTENSION = ".rmppl";//".m3u8";
	
	private String name;
	public ObservableList<MediaHandle> media = FXCollections.observableList(new ArrayList<>());
	private int numSongs = 0;
	private List<Playlist> playlists = new ArrayList<Playlist>();
	private boolean dirty = false;
	
	public Playlist(File playlistFile)
	{
		name = playlistFile.getName();
		name = name.substring(0, name.lastIndexOf("."));
		
		try
		{
			Files.lines(playlistFile.toPath(), Charset.forName("UTF-8")).forEach(str -> {
				if(str.startsWith("s:"))
				{
					String song = str.substring(2);
					if(MediaLibrary.urlSongMap.containsKey(song))
					{
						addSong(MediaLibrary.urlSongMap.get(song));
					}
					else
					{
						try
						{
							addSong(new AudioSource(song));
						}
						catch(URISyntaxException urise)
						{
							urise.printStackTrace();
							// TODO tell user the song couldn't be loaded
						}
					}
				}
				else if(str.startsWith("p:"))
				{
					String playlist = str.substring(2);
					if(MediaLibrary.isPlaylistLoaded(playlist))
					{
						addPlaylist(MediaLibrary.getPlaylist(playlist));
					}
					else
					{
						Playlist temp;
						MediaLibrary.playlists.add(temp = new Playlist(new File(playlistFile.getParentFile(), playlist + EXTENSION)));
						addPlaylist(temp);
					}
				}
			});
		}
		catch(Exception e)
		{
			e.printStackTrace();
			// TODO tell user playlist couldn't be opened
		}
		dirty = false;
	}
	
	public Playlist(String name)
	{
		this.name = name;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String name)
	{
		if(!this.name.equals(name))
		{
			String oldName = this.name;
			this.name = name;
			File saveDir = new File(Config.mediaDirectory, "Playlists");
			save(saveDir);
			new File(saveDir, oldName + EXTENSION).delete();
			
			for(Playlist pl : MediaLibrary.playlists)
			{
				if(pl.media.parallelStream().filter(mh -> mh instanceof PlaylistHandle).anyMatch(mh -> mh.getPlaylist().getName().equals(name)))
					pl.setDirty(true);
			}
		}
	}
	
	public boolean isDirty()
	{
		return dirty;
	}
	
	public void setDirty(boolean dirty)
	{
		this.dirty = dirty;
	}
	
	public int getSize()
	{
		return numSongs + playlists.stream().mapToInt(Playlist::getSize).sum();
	}
	
	public boolean isEmpty()
	{
		return media.isEmpty();
	}
	
	public MediaHandle getSong(int index)
	{
		final int origIndex = index; // used for error message

		if(index < 0 || index >= getSize())
			return null;
		for(MediaHandle mh : media)
		{
			if(mh instanceof PlaylistHandle)
			{
				Playlist pl = mh.getPlaylist();
				if(pl.getSize() > index)
					return pl.getSong(index);
				else
					index -= pl.getSize();
			}
			else if(index == 0)
			{
				return mh;
			}
			else
			{
				index--;
			}
		}
		
		StringBuilder sb = new StringBuilder(100);
		sb.append("Somehow you managed to not find a song at index ");
		sb.append(origIndex);
		sb.append(" in playlist \"");
		sb.append(name);
		sb.append('"');
		throw new IllegalStateException(sb.toString());
	}
	
	private MediaHandle getMedia(int index)
	{
		if(index < 0 || index >= media.size())
			return null;
		
		return media.get(index);
	}
	
	public Duration getDuration()
	{
		Duration d = media.parallelStream().filter(mh -> mh instanceof SongHandle).map(mh -> mh.getCurrentAudioSource().durationProperty().get()).reduce((d1, d2) -> d1.add(d2)).orElse(Duration.ZERO);
		return d.add(media.parallelStream().filter(mh -> mh instanceof PlaylistHandle).map(mh -> mh.getPlaylist().getDuration()).reduce((d1, d2) -> d1.add(d2)).orElse(Duration.ZERO));
	}
	
	public void addSong(int index, AudioSource playable)
	{
		if(index < 0 || index > media.size())
			throw new IllegalArgumentException("Cannot add song below index 0 or past end of playlist");
		
		if(media.size() == 0)
		{
			media.add(new SongHandle(playable, null));
			numSongs++;
			dirty = true;
		}
		else if(!containsSong(playable))
		{
			media.add(index, new SongHandle(playable, getMedia(index - 1), getMedia(index)));
			numSongs++;
			dirty = true;
		}
		else if(!name.isEmpty())
		{
			// TODO warn about duplicate song
			media.add(index, new SongHandle(playable, getMedia(index - 1), getMedia(index)));
			numSongs++;
			dirty = true;
		}
	}
	
	public void addSong(AudioSource playable)
	{
		addSong(media.size(), playable);
	}
	
	/**
	 * 
	 * @param index to insert playlist at
	 * @param playlist to add to this playlist
	 * @param byReference whether or not the playlist should by added by reference or content
	 * @param flatten whether or not the contents of the playlist being added should flattened to a list of songs (only used if byReference is false)
	 */
	public void addPlaylist(int index, Playlist playlist, boolean byReference, boolean flatten)
	{
		if(index < 0 || index > media.size())
			throw new IllegalArgumentException("Cannot add playlist below index 0 or past end of playlist");
		
		if(byReference)
		{
			media.add(index, new PlaylistHandle(playlist, media.get(index - 1), media.get(index)));
			playlists.add(playlist);
		}
		else if(flatten)
		{
			for(AudioSource as : playlist.flatten())
				addSong(index++, as);
			numSongs += playlist.getSize();
		}
		else
		{
			for(MediaHandle mh : playlist.media)
			{
				if(mh instanceof SongHandle)
					addSong(index++, mh.getCurrentAudioSource());
				else
					addPlaylist(index++, mh.getPlaylist(), true, false);
			}
			numSongs += playlist.numSongs;
			playlists.addAll(playlist.playlists);
		}
		dirty = true;
	}
	
	public void addPlaylist(Playlist playlist)
	{
		addPlaylist(media.size(), playlist, true, false);
	}
	
	public boolean removeMedia(MediaHandle media)
	{
		return removeMedia0(media, false);
	}
	
	private boolean removeMedia0(MediaHandle mediaHandle, boolean exists)
	{
		if(!exists && !media.contains(mediaHandle))
			return false;
		MediaHandle prev = mediaHandle.getPrev();
		MediaHandle next = mediaHandle.getNext();
		if(prev != null)
			prev.setNext(next);
		else if(next != null)
			next.setPrev(prev);
		if(mediaHandle instanceof SongHandle)
			numSongs--;
		else
			playlists.remove(mediaHandle.getPlaylist());
		dirty = true;
		return media.remove(mediaHandle);
	}
	
	public void removeSong(AudioSource as)
	{
		for(int i = 0; i < media.size(); i++)
		{
			MediaHandle mh = media.get(i);
			if(mh instanceof SongHandle && mh.getCurrentAudioSource().equals(as))
			{
				if(Player.currentlyPlayingProperty.get().equals(mh))
					Player.next();
				removeMedia0(mh, true);
				i--;
			}
		}
	}
	
	public void moveMedia(MediaHandle mediaHandle, MediaHandle prev, MediaHandle next)
	{
		if(!media.contains(mediaHandle) || (prev != null && !media.contains(prev)) || (next != null && !media.contains(next)))
		{
			// TODO this shouldn't happen. Warn the user I guess?
		}
		removeMedia(mediaHandle);
		mediaHandle.setPrev(prev);
		mediaHandle.setNext(next);
		
		// will put the song in slot 0 if the previous song is null or in the correct spot if it isn't null
		media.add(media.indexOf(prev) + 1, mediaHandle);
		if(mediaHandle instanceof SongHandle)
			numSongs++;
		else
			playlists.add(mediaHandle.getPlaylist());
		dirty = true;
	}
	
	public int indexOf(MediaHandle mh)
	{
		return media.indexOf(mh);
	}
	
	public int indexOf(AudioSource as)
	{
		int index = 0;
		for(MediaHandle mh : media)
		{
			if(mh instanceof SongHandle && mh.getCurrentAudioSource().equals(as))
				return index;
			index++;
		}
		return -1;
	}
	
	/**
	 * checks if this playlist contains a given song in itself or any of its sub-playlists
	 * @return
	 */
	public boolean containsSong(AudioSource as)
	{
		for(MediaHandle mh : media)
			if(mh instanceof SongHandle && mh.getCurrentAudioSource().equals(as))
				return true;
			else if(mh instanceof PlaylistHandle && mh.getPlaylist().containsSong(as))
				return true;
		return false;
	}
	
	public void save(File saveDir)
	{
		if(!saveDir.exists())
			saveDir.mkdirs();
		
		File file = new File(saveDir, name + EXTENSION);
		StringBuilder sb = new StringBuilder();
		for(MediaHandle mh : media)
		{
			if(mh instanceof SongHandle)
			{
				sb.append("s:");
				sb.append(mh.getCurrentAudioSource().toString());
			}
			else
			{
				sb.append("p:");
				sb.append(mh.getPlaylist().name);
			}
			sb.append('\n');
		}
		byte[] data = sb.toString().getBytes(Charset.forName("UTF-8"));
		try
		{
			Files.write(file.toPath(), data);
			dirty = false;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			// TODO tell the user the playlist failed to save
		}
	}
	
	public void clearPlaylist()
	{
		media.clear();
		dirty = true;
	}
	
	public void shuffle(boolean shuffle)
	{
		// TODO
	}
	
	private List<AudioSource> flatten()
	{
		ArrayList<AudioSource> songs = new ArrayList<>(getSize());
		for(MediaHandle mh : media)
		{
			if(mh instanceof SongHandle)
				songs.add(mh.getCurrentAudioSource());
			else
				songs.addAll(mh.getPlaylist().flatten());
		}
		return songs;
	}
}