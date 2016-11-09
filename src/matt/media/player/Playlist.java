package matt.media.player;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Playlist implements Iterable<AudioSource>
{
	public static final String EXTENSION = ".rmppl";//".m3u8";
	
	private String name;
	public ObservableList<SongHandle> songs = FXCollections.observableList(new LinkedList<>());
	
	public Playlist(File playlistFile)
	{
		name = playlistFile.getName();
		name = name.substring(0, name.lastIndexOf("."));
		
		try
		{
			Files.lines(playlistFile.toPath(), Charset.forName("UTF-8")).map(str -> {
				try
				{
					return new AudioSource(str);
				}
				catch(URISyntaxException urise)
				{
					return null;
				}
			}).filter(as -> as != null).forEach(this::addSong);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			// TODO tell user playlist couldn't be opened
		}
	}
	
	public Playlist(String name)
	{
		this.name = name;
	}
	
	public int getSize()
	{
		return songs.size();
	}
	
	public boolean isEmpty()
	{
		return songs.isEmpty();
	}
	
	public SongHandle getSong(int index)
	{
		if(index == -1 || index == songs.size())
			return null;
		return songs.get(index);
	}
	
	//public Duration getDuration()
	//{
	//	songs.stream().map(SongHandle::getAudioFile).map(AudioFile::)
	//}
	
	public void addSong(int index, AudioSource playable)
	{
		if(index > songs.size())
			throw new IllegalArgumentException("Cannot add song past end of playlist");
		
		if(songs.size() == 0)
		{
			songs.add(new SongHandle(playable, null));
		}
		else if(songs.stream().map(SongHandle::getAudioSource).noneMatch(song -> song.equals(playable)))
		{
			songs.add(index, new SongHandle(playable, getSong(index - 1), getSong(index)));
		}
		else if(!name.isEmpty())
		{
			// TODO warn about duplicate song
		}
	}
	
	public void addSong(AudioSource playable)
	{
		addSong(songs.size(), playable);
	}
	
	public void addPlaylist(int index, Playlist playlist)
	{
		for(AudioSource as : playlist.songs.stream().map(SongHandle::getAudioSource).collect(Collectors.toList()))
			addSong(index++, as);
	}
	
	public void addPlaylist(Playlist playlist)
	{
		addPlaylist(songs.size(), playlist);
	}
	
	public boolean removeSong(SongHandle song)
	{
		return removeSong0(song, false);
	}
	
	private boolean removeSong0(SongHandle song, boolean exists)
	{
		if(!exists && !songs.contains(song))
			return false;
		SongHandle prev = song.getPrev();
		SongHandle next = song.getNext();
		if(prev != null)
			prev.setNext(next);
		else if(next != null)
			next.setPrev(prev);
		return songs.remove(song);
	}
	
	public void removeSong(AudioSource as)
	{
		for(int i = 0; i < songs.size(); i++)
		{
			SongHandle sh = songs.get(i);
			if(sh.getAudioSource().equals(as))
			{
				if(Player.currentlyPlayingProperty.get().equals(sh))
					Player.next();
				removeSong0(sh, true);
				i--;
			}
		}
	}
	
	public void moveSong(SongHandle song, SongHandle prev, SongHandle next)
	{
		if(!songs.contains(song) || (prev != null && !songs.contains(prev)) || (next != null && !songs.contains(next)))
		{
			// TODO this shouldn't happen. Warn the user I guess?
		}
		removeSong(song);
		song.setPrev(prev);
		song.setNext(next);
		
		// will put the song in slot 0 if the previous song is null or in the correct spot if it isn't null
		songs.add(songs.indexOf(prev) + 1, song);
	}
	
	public int indexOf(SongHandle sh)
	{
		return songs.indexOf(sh);
	}
	
	public int indexOf(AudioSource as)
	{
		for(int i = 0; i < songs.size(); i++)
			if(songs.get(i).getAudioSource().equals(as))
				return i;
		return -1;
	}
	
	public void save(File saveDir)
	{
		if(!saveDir.exists())
			saveDir.mkdirs();
		
		File file = new File(saveDir, name + EXTENSION);
		byte[] data = songs.stream().map(SongHandle::getAudioSource).map(AudioSource::toString).collect(Collectors.joining("\n")).getBytes(Charset.forName("UTF-8"));
		try
		{
			Files.write(file.toPath(), data, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			// TODO tell the user the playlist failed to save
		}
	}
	
	public void clearPlaylist()
	{
		songs.clear();
	}
	
	public void shuffle(boolean shuffle)
	{
		// TODO
	}
	
	@Override
	public Iterator<AudioSource> iterator()
	{
		return new PlaylistIterator();
	}
	
	private class PlaylistIterator implements Iterator<AudioSource>
	{
		private Iterator<SongHandle> iterator = songs.iterator();
		
		@Override
		public boolean hasNext()
		{
			return iterator.hasNext();
		}
		
		@Override
		public AudioSource next()
		{
			return iterator.next().getAudioSource();
		}
	}
}