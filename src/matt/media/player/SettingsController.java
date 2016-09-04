package matt.media.player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

public class SettingsController
{
	public Window window;
	public TextField musicDirectory;
	public DirectoryChooser directoryChooser = new DirectoryChooser();
	
	public void initialize()
	{
		musicDirectory.setText(Config.mediaDirectory.getAbsolutePath());
	}
	
	public void changeMediaDir()
	{
		File dir;
		if((dir = directoryChooser.showDialog(window)) != null)
		{
			AlertBox ab = new AlertBox("Media Move Prompt", "What would you like to do with your old media?", new String[]{"Nothing",  "Copy it", "Move it"}, MediaMoveOptions.DO_NOTHING, MediaMoveOptions.COPY, MediaMoveOptions.MOVE);
			ab.display();
			MediaMoveOptions mmo = (MediaMoveOptions) ab.getReturnValue();
			try
			{
				switch(mmo)
				{
				case COPY:
					Files.copy(Config.mediaDirectory.toPath(), dir.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
					break;
				case MOVE:
					Files.move(Config.mediaDirectory.toPath(), dir.toPath(), StandardCopyOption.REPLACE_EXISTING);
					break;
				case DO_NOTHING:
					break;
				}
			}
			catch(IOException ioe)
			{
				System.err.println("Failed to copy or move old media to the new directory.");
			}
			
			Config.mediaDirectory = dir;
			Config.updateConfig();
			musicDirectory.setText(Config.mediaDirectory.getAbsolutePath());
		}
	}
	
	public void resetCache()
	{
		Cache.reset();
	}
}