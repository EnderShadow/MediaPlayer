package matt.media.player;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import org.controlsfx.control.GridView;

import com.sun.javafx.scene.control.skin.TableViewSkin;
import com.sun.javafx.scene.control.skin.VirtualFlow;

import impl.org.controlsfx.skin.GridViewSkin;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.util.Duration;

public class Util
{
	private static String[] supportedAudioFormats = {".aif", ".aiff", ".m3u8", ".mp3", ".m4a", ".wav"};
	private static Image defaultImage;
	
	final static int[] illegalChars = {34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47};
	
	static
	{
		Arrays.sort(illegalChars);
	}
	
	public static String formatDuration(Duration duration)
	{
		if(duration == null)
			duration = Duration.ZERO;
		if(duration.greaterThanOrEqualTo(Duration.hours(100.0D)))
			duration = Duration.seconds(99 * 3600 + 59 * 60 + 59);
	    long seconds = (long) duration.toSeconds();
	    return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
	}
	
	public static boolean doesAudioSourceMatch(AudioSource as, StringProperty filterText)
	{
		if(as.titleProperty().get().toLowerCase().contains(filterText.get().toLowerCase()))
			return true;
		if(as.artistProperty().get().toLowerCase().contains(filterText.get().toLowerCase()))
			return true;
		if(as.albumProperty().get().toLowerCase().contains(filterText.get().toLowerCase()))
			return true;
		if(as.genreProperty().get().toLowerCase().contains(filterText.get().toLowerCase()))
			return true;
		return false;
	}
	
	public static boolean isUnsupportedAudio(File file)
	{
		String name = file.getName().toLowerCase();
		for(String extension : supportedAudioFormats)
			if(name.endsWith(extension))
				return false;
		return true;
	}
	
	public static String cleanFileName(String fileName)
	{
		StringBuilder cleanName = new StringBuilder();
		int len = fileName.codePointCount(0, fileName.length());
		for(int i = 0; i < len; i++)
		{
			int c = fileName.codePointAt(i);
			if(Arrays.binarySearch(illegalChars, c) < 0)
			{
				cleanName.appendCodePoint(c);
			}
			else
			{
				cleanName.append('_');
			}
		}
		return cleanName.toString().trim();
	}
	
	public static int countFiles(File dir)
	{
		LinkedList<File> toSearch = new LinkedList<>();
		toSearch.add(dir);
		int count = 0;
		while(toSearch.size() > 0)
		{
			dir = toSearch.remove();
			for(File file : dir.listFiles())
				if(file.isDirectory())
					toSearch.add(file);
				else
					count++;
		}
		return count;
	}
	
	public static Image getDefaultImage()
	{
		if(defaultImage == null)
			return defaultImage = new Image(Util.class.getClassLoader().getResourceAsStream("res/default.jpg"));
		return defaultImage;
	}
	
	public static <A> void concurrentForEach(List<A> list, Consumer<A> consumer)
	{
		for(int i = 0; i < list.size(); i++)
		{
			try
			{
				consumer.accept(list.get(i));
			}
			catch(ArrayIndexOutOfBoundsException aioobe)
			{
				// ignore exception
			}
		}
	}
	
	public static BufferedImage squareImage(BufferedImage bi)
	{
		if(bi.getHeight() == bi.getWidth() && (Config.maxImageSize <= 0 && Config.maxImageSize >= bi.getWidth()))
			return bi;
		
		int newSize = Math.max(bi.getHeight(), bi.getWidth());
		if(Config.maxImageSize > 0 && Config.maxImageSize < newSize)
		{
			double scale = (double) Config.maxImageSize / newSize;
			BufferedImage temp = new BufferedImage(Config.maxImageSize, Config.maxImageSize, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = temp.createGraphics();
			g.drawImage(bi.getScaledInstance((int) (bi.getWidth() * scale), (int) (bi.getHeight() * scale), BufferedImage.SCALE_SMOOTH), 0, 0, null);
			g.dispose();
			newSize = Config.maxImageSize;
			bi = temp;
		}
		
		BufferedImage newImage = new BufferedImage(newSize, newSize, BufferedImage.TYPE_INT_ARGB);
		int xOffset = (newSize - bi.getWidth()) / 2;
		int yOffset = (newSize - bi.getHeight()) / 2;
		Graphics2D g = newImage.createGraphics();
		g.drawImage(bi, xOffset, yOffset, null);
		g.dispose();
		return newImage;
	}
	
	public static BufferedImage prepForCache(BufferedImage bi)
	{
		if(bi.getWidth() <= Config.cacheImageSize)
			return bi;
		
		BufferedImage temp = new BufferedImage(Config.cacheImageSize, Config.cacheImageSize, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = temp.createGraphics();
		g.drawImage(bi.getScaledInstance(Config.cacheImageSize, Config.cacheImageSize, BufferedImage.SCALE_SMOOTH), 0, 0, null);
		g.dispose();
		return temp;
	}
	
	public static byte[] toByteArray(BufferedImage bi)
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try
		{
			ImageIO.write(bi, "jpg", baos);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return baos.toByteArray();
	}
	
	public static BufferedImage fromByteArray(byte[] ba)
	{
		try
		{
			return ImageIO.read(new ByteArrayInputStream(ba));
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}
	
	public static <T> List<T> getVisible(GridView<T> gridView)
	{
		List<T> retList = new ArrayList<>();
		GridViewSkin<?> skin = (GridViewSkin<?>) gridView.getSkin();
		VirtualFlow<?> flow = (VirtualFlow<?>) skin.getChildren().stream().filter(n -> n instanceof VirtualFlow).findFirst().get();
		
		if(flow.getFirstVisibleCell() == null)
			return retList;
		int firstIndex = flow.getFirstVisibleCell().getIndex();
		int lastIndex = flow.getLastVisibleCell().getIndex();
		int cellsInRow = skin.computeMaxCellsInRow();
		
		retList.addAll(gridView.getItems().subList(firstIndex * cellsInRow, Math.min((lastIndex + 1) * cellsInRow, gridView.getItems().size())));
		return retList;
	}
	
	public static <T> List<T> getVisible(TableView<T> tableView)
	{
		List<T> retList = new ArrayList<>();
		TableViewSkin<?> skin = (TableViewSkin<?>) tableView.getSkin();
		VirtualFlow<?> flow = (VirtualFlow<?>) skin.getChildren().stream().filter(n -> n instanceof VirtualFlow).findFirst().get();
		
		if(flow.getFirstVisibleCell() == null)
			return retList;
		int firstIndex = flow.getFirstVisibleCell().getIndex();
		int lastIndex = flow.getLastVisibleCell().getIndex();
		
		retList.addAll(tableView.getItems().subList(firstIndex, lastIndex + 1));
		return retList;
	}
}