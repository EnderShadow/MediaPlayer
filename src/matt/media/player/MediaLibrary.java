package matt.media.player;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class MediaLibrary
{
	private static final List<AudioSource> needToCache = new ArrayList<AudioSource>();
	private static final Object albumListLock = new Object();
	
	public static final ObservableList<AudioSource> songs = FXCollections.observableList(Collections.synchronizedList(new ArrayList<>(10000)));
	public static Map<URI, AudioSource> urlSongMap = new ConcurrentHashMap<URI, AudioSource>(10000);
	
	public static final ObservableList<UniqueSongCollection> albums = FXCollections.observableList(Collections.synchronizedList(new ArrayList<>(1000)));
	
	public static void addSong(AudioSource as)
	{
		songs.add(as);
		urlSongMap.putIfAbsent(as.getURI(), as);
		boolean contains;
		synchronized(albumListLock)
		{
			contains = albums.stream().map(album -> album.nameProperty().get()).noneMatch(albumName -> albumName.equals(as.albumProperty().get()));
		}
		if(contains)
		{
			UniqueSongCollection album = new UniqueSongCollection(as.albumProperty().get(), songs, null, (as1, as2) -> Integer.compare(as1.trackNumberProperty().get(), as2.trackNumberProperty().get()));
			album.setBelongs(as2 -> as2.albumProperty().get().equals(album.nameProperty().get()));
			Platform.runLater(() -> {
				synchronized(albumListLock)
				{
					albums.add(album);
				}
			});
		}
		if(!as.wasSeriallyLoaded())
		{
			needToCache.add(as);
		}
	}
	
	public static void removeSong(AudioSource as, boolean deleteSong)
	{
		System.out.println("Removing song " + as);
		Player.queueProperty.get().removeSong(as);
		songs.remove(as);
		urlSongMap.remove(as.getURI());
		as.getMediaPlayer().dispose();
		Cache.remove(as);
		if(deleteSong)
		{
			System.out.println("Deleting song " + as);
			File f = new File(as.getURI());
			if(!f.delete())
				f.deleteOnExit();
		}
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
}