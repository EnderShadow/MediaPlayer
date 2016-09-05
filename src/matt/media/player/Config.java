package matt.media.player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class Config
{
	private static final File configLoc = new File(System.getProperty("user.home") + File.separator + "MediaPlayer" + File.separator + "config.cfg");
	private static final String VERSION = "1.0.0";
	
	public static File mediaDirectory = new File(System.getProperty("user.home") + File.separator + "MediaPlayer" + File.separator + "Media");
	public static boolean cacheDisable = false;
	public static boolean imagesInCache = true;
	public static int cacheImageSize = 100;
	
	public static void load()
	{
		if(!configLoc.exists())
		{
			configLoc.getParentFile().mkdirs();
			updateConfig();
		}
		else
		{
			try
			{
				List<String> configData = Files.readAllLines(configLoc.toPath(), Charset.forName("UTF-8"));
				
				configData.removeIf(str -> str.trim().startsWith("#") || str.trim().isEmpty());
				String version = configData.remove(0).split(":")[1].trim();
				
				// do anything with the version here
				
				for(String str : configData)
				{
					String[] line = str.split(":", 2);
					switch(line[0].trim())
					{
					case "mediaDirectory":
						mediaDirectory = new File(line[1].trim());
						break;
					case "cacheDisable":
						cacheDisable = Boolean.parseBoolean(line[1].trim());
						break;
					case "imagesInCache":
						imagesInCache = Boolean.parseBoolean(line[1].trim());
						break;
					case "cacheImageSize":
						cacheImageSize = Integer.parseInt(line[1].trim());
						break;
					default:
						System.out.println("Unknown setting in config\t" + str);
					}
				}
			}
			catch(IOException ioe)
			{
				System.err.println("Unable to read config. Using default config.");
			}
		}
		
		if(!mediaDirectory.exists())
			mediaDirectory.mkdirs();
	}
	
	public static void updateConfig()
	{
		List<String> configData = new ArrayList<String>();
		configData.add("version: " + VERSION);
		configData.add("mediaDirectory: " + mediaDirectory.getAbsolutePath());
		configData.add("cacheDisable: " + cacheDisable);
		configData.add("imagesInCache: " + imagesInCache);
		configData.add("cacheImageSize: " + cacheImageSize);
		
		try
		{
			Files.write(configLoc.toPath(), configData, Charset.forName("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch(IOException ioe)
		{
			System.err.println("Unable to create or update config.");
		}
	}
}
