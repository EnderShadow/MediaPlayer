package matt.media.player;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MediaLibrary
{
	private static final List<AudioSource> needToCache = new ArrayList<AudioSource>();
	private static final Object songListLock = new Object();
	
	public static final ObservableList<AudioSource> songs = FXCollections.observableList(Collections.synchronizedList(new ArrayList<>(10000)));
	public static Map<URI, AudioSource> urlSongMap = new ConcurrentHashMap<URI, AudioSource>(10000);
	
	public static final ObservableList<Playlist> playlists = FXCollections.observableList(Collections.synchronizedList(new ArrayList<>(100)));
	
	public static final ObservableList<UniqueSongCollection> albums = FXCollections.observableList(Collections.synchronizedList(new ArrayList<>(1000)));
	
	// TODO these should both contain albums
	public static final ObservableList<UniqueSongCollection> artists = FXCollections.observableList(Collections.synchronizedList(new ArrayList<>(1000)));
	public static final ObservableList<UniqueSongCollection> genres = FXCollections.observableList(Collections.synchronizedList(new ArrayList<>(1000)));
	
	public static void addSong(AudioSource as)
	{
		Platform.runLater(() -> {
			synchronized(songListLock)
			{
				songs.add(as);
				urlSongMap.putIfAbsent(as.getURI(), as);
				if(albums.stream().map(album -> album.nameProperty().get()).noneMatch(albumName -> albumName.equals(as.albumProperty().get())))
				{
					UniqueSongCollection album = new UniqueSongCollection(as.albumProperty().get(), as.albumArtistProperty().get(), songs, null, (as1, as2) -> Integer.compare(as1.trackNumberProperty().get(), as2.trackNumberProperty().get()));
					album.setBelongs(as2 -> as2.albumProperty().get().equals(album.nameProperty().get()) && as2.albumArtistProperty().get().equals(album.secondaryTextProperty().get()));
					albums.add(album);
				}
				if(artists.stream().map(artist -> artist.nameProperty().get()).noneMatch(artistName -> artistName.equals(as.artistProperty().get())))
				{
					UniqueSongCollection artist = new UniqueSongCollection(as.artistProperty().get(), null, songs, null, (as1, as2) -> as1.titleProperty().get().compareToIgnoreCase(as2.titleProperty().get()));
					artist.setBelongs(as2 -> as2.artistProperty().get().equals(artist.nameProperty().get()));
					artist.displayStyleProperty().set(DisplayStyle.SINGLE);
					artists.add(artist);
				}
				if(genres.stream().map(genre -> genre.nameProperty().get()).noneMatch(genreName -> genreName.equals(as.genreProperty().get())))
				{
					UniqueSongCollection genre = new UniqueSongCollection(as.genreProperty().get(), null, songs, null, (as1, as2) -> as1.titleProperty().get().compareToIgnoreCase(as2.titleProperty().get()));
					genre.setBelongs(as2 -> as2.genreProperty().get().equals(genre.nameProperty().get()));
					genre.secondaryTextProperty().bind(Bindings.createStringBinding(() -> genre.getUnmodifiableSongList().size() + " songs", genre.getChildrenUnmodifiable()));
					genre.displayStyleProperty().set(DisplayStyle.ALBUM);
					genres.add(genre);
				}
			}
		});
		if(!as.wasSeriallyLoaded())
		{
			needToCache.add(as);
		}
	}
	
	public static void removeSong(AudioSource as, boolean deleteSong)
	{
		System.out.println("Removing song " + as);
		Player.queueProperty.get().removeSong(as);
		synchronized(songListLock)
		{
			songs.remove(as);
		}
		urlSongMap.remove(as.getURI());
		as.dispose();
		Cache.remove(as);
		if(deleteSong)
		{
			System.out.println("Deleting song " + as);
			File f = new File(as.getURI());
			if(!f.delete())
				f.deleteOnExit();
		}
	}
	
	public static void loadPlaylists()
	{
		File playlistFolder = new File(Config.mediaDirectory, "Playlists");
		if(!playlistFolder.exists())
			playlistFolder.mkdirs();
		
		for(File playlist : playlistFolder.listFiles((file, name) -> name.toLowerCase().endsWith(Playlist.EXTENSION)))
		{
			if(!isPlaylistLoaded(playlist.getName().substring(0, playlist.getName().length() - Playlist.EXTENSION.length())))
			{
				playlists.add(new Playlist(playlist));
			}
		}
	}
	
	public static boolean isPlaylistLoaded(String name)
	{
		return playlists.stream().map(pl -> pl.getName()).anyMatch(plName -> plName.equalsIgnoreCase(name));
	}
	
	public static Playlist getPlaylist(String name)
	{
		return playlists.stream().filter(pl -> pl.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}
	
	public static void cacheAll()
	{
		if(!needToCache.isEmpty())
		{
			List<AudioSource> sub = needToCache.subList(0, needToCache.size());
			List<AudioSource> temp = new ArrayList<>(sub.size());
			temp.addAll(sub);
			sub.clear();
			new Thread(() -> {
				Cache.cacheAll(temp);
			}).start();
		}
	}
	
	public static void forgetToCache()
	{
		needToCache.clear();
	}
}