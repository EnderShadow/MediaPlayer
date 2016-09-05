package matt.media.player;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.util.Pair;

public class Cache
{
	private static List<Pair<URI, Long>> uriOffsetList = new ArrayList<>(10000);
	public static boolean needsRebuilding = false;
	
	static
	{
		File cacheFile = new File(Config.mediaDirectory, "media.cache");
		try
		{
			if(!cacheFile.exists())
				cacheFile.createNewFile();
		}
		catch(IOException ioe)
		{
			ioe.printStackTrace();
		}
	}
	
	public static synchronized void cache(AudioSource as)
	{
		if(Config.cacheDisable)
			return;
		if(uriOffsetList.stream().map(Pair::getKey).anyMatch(uri -> uri.equals(as.getURI())))
			remove(as);
		File cacheFile = new File(Config.mediaDirectory, "media.cache");
		long offset = cacheFile.length();
		uriOffsetList.add(new Pair<>(as.getURI(), offset));
		try(RandomAccessFile raf = new RandomAccessFile(cacheFile, "rw"))
		{
			as.writeToFile(raf);
		}
		catch(IOException ioe)
		{
			ioe.printStackTrace();
		}
	}
	
	/**
	 * This should only be run when adding new files to the player. Do not run in any other situation
	 * @param as
	 */
	public static synchronized void cacheAll(List<AudioSource> asl)
	{
		if(Config.cacheDisable)
			return;
		File cacheFile = new File(Config.mediaDirectory, "media.cache");
		try(RandomAccessFile raf = new RandomAccessFile(cacheFile, "rw"))
		{
			long offset = raf.length();
			for(AudioSource as : asl)
			{
				uriOffsetList.add(new Pair<>(as.getURI(), offset));
				as.writeToFile(raf);
				offset = raf.length();
			}
		}
		catch(IOException ioe)
		{
			ioe.printStackTrace();
		}
	}
	
	public static synchronized void remove(AudioSource as)
	{
		if(Config.cacheDisable)
			return;
		if(uriOffsetList.stream().map(Pair::getKey).noneMatch(uri -> uri.equals(as.getURI())))
			return;
		Pair<URI, Long> uriOffsetPair = uriOffsetList.stream().filter(p -> p.getKey().equals(as.getURI())).findAny().get();
		int index = indexOf(uriOffsetPair);
		if(index == uriOffsetList.size() - 1)
		{
			try
			{
				RandomAccessFile raf = new RandomAccessFile(new File(Config.mediaDirectory, "media.cache"), "rw");
				raf.setLength(uriOffsetPair.getValue());
				raf.close();
			}
			catch(IOException ioe)
			{
				ioe.printStackTrace();
			}
		}
		else
		{
			long size = uriOffsetList.get(index + 1).getValue() - uriOffsetPair.getValue();
			for(int i = index + 1; i < uriOffsetList.size(); i++)
			{
				Pair<URI, Long> val = uriOffsetList.get(i);
				val = new Pair<URI, Long>(val.getKey(), val.getValue() - size);
				uriOffsetList.set(i - 1, val);
			}
			uriOffsetList.remove(uriOffsetList.size() - 1);
			try
			{
				RandomAccessFile raf = new RandomAccessFile(new File(Config.mediaDirectory, "media.cache"), "rw");
				long writePos = uriOffsetPair.getValue();
				long readPos = writePos + size;
				raf.seek(readPos);
				byte[] temp = new byte[8192];
				int amt;
				while((amt = raf.read(temp)) != -1)
				{
					readPos = raf.getFilePointer();
					raf.seek(writePos);
					raf.write(temp, 0, amt);
					writePos += amt;
					raf.seek(readPos);
				}
				raf.setLength(raf.length() - size);
				raf.close();
			}
			catch(IOException ioe)
			{
				ioe.printStackTrace();
			}
		}
	}
	
	public static synchronized int retrieveAll(int numFilesRead, int numFilesTotal, BiConsumer<Integer, Integer> updateProgress, Consumer<String> updateMessage)
	{
		if(Config.cacheDisable)
			return 0;
		List<AudioSource> asl = new ArrayList<AudioSource>();
		File cacheFile = new File(Config.mediaDirectory, "media.cache");
		try(RandomAccessFile raf = new RandomAccessFile(cacheFile, "r"))
		{
			long offset = 0;
			while(offset < raf.length())
			{
				AudioSource as = AudioSource.readFromFile(raf);
				MediaLibrary.addSong(as);
				asl.add(as);
				uriOffsetList.add(new Pair<>(as.getURI(), offset));
				offset = raf.getFilePointer();
				numFilesRead++;
				updateProgress.accept(numFilesRead, numFilesTotal);
				updateMessage.accept(numFilesRead + "/" + numFilesTotal);
			}
		}
		catch(EOFException eofe)
		{
			// useful for notifying the developer that he forgot to reset the cache after changing the cache format
			System.err.println("The cache is corrupt. Marking for rebuild.");
			eofe.printStackTrace();
			needsRebuilding = true;
		}
		catch(IOException ioe)
		{
			ioe.printStackTrace();
		}
		
		return numFilesRead;
	}
	
	public static synchronized AudioSource retrieve(URI uri)
	{
		if(Config.cacheDisable)
			return null;
		Pair<URI, Long> uriOffsetPair = uriOffsetList.stream().filter(p -> p.getKey().equals(uri)).findAny().get();
		File cacheFile = new File(Config.mediaDirectory, "media.cache");
		try(RandomAccessFile raf = new RandomAccessFile(cacheFile, "r"))
		{
			raf.seek(uriOffsetPair.getValue());
			return AudioSource.readFromFile(raf);
		}
		catch(IOException ioe)
		{
			ioe.printStackTrace();
		}
		return null;
	}
	
	private static int indexOf(Pair<URI, Long> uriOffsetPair)
	{
		return indexOf(uriOffsetPair, 0, uriOffsetList.size());
	}
	
	private static int indexOf(Pair<URI, Long> uriOffsetPair, int low, int high)
	{
		while(low < high)
		{
			int mid = (high - low) / 2;
			Pair<URI, Long> potential = uriOffsetList.get(mid);
			if(potential.equals(uriOffsetPair))
				return mid;
			else if(uriOffsetPair.getValue() < potential.getValue())
				high = mid;
			else if(uriOffsetPair.getValue() > potential.getValue())
				low = mid + 1;
		}
		return -1;
	}
	
	public static void reset()
	{
		delete();
		if(Config.cacheDisable)
			return;
		new Thread(() -> cacheAll(MediaLibrary.songs.subList(0, MediaLibrary.songs.size()))).start();
	}
	
	public static void delete()
	{
		File cacheFile = new File(Config.mediaDirectory, "media.cache");
		try
		{
			RandomAccessFile raf = new RandomAccessFile(cacheFile, "rw");
			raf.setLength(0);
			raf.close();
		}
		catch(IOException ioe)
		{
			ioe.printStackTrace();
		}
		uriOffsetList.clear();
	}
}