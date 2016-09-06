package matt.media.player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

public class SettingsController
{
	public Window window;
	
	private DirectoryChooser directoryChooser = new DirectoryChooser();
	
	@FXML
	private TextField musicDirectory;
	@FXML
	private TextField maxImageSize;
	@FXML
	private CheckBox disableCache;
	@FXML
	private CheckBox imagesInCache;
	@FXML
	private Button rebuildCacheButton;
	@FXML
	private TextField cacheImageSize;
	
	public void initialize()
	{
		musicDirectory.setText(Config.mediaDirectory.getAbsolutePath());
		maxImageSize.setText(Integer.toString(Config.maxImageSize));
		maxImageSize.setOnAction(evt -> {
			try
			{
				Config.maxImageSize = Integer.parseInt(maxImageSize.getText());
			}
			catch(NumberFormatException nfe)
			{
				Config.maxImageSize = 1;
			}
			Config.updateConfig();
		});
		disableCache.setSelected(Config.cacheDisable);
		disableCache.setOnAction(evt -> {
			Config.cacheDisable = !Config.cacheDisable;
			Config.updateConfig();
		});
		imagesInCache.setSelected(Config.imagesInCache);
		imagesInCache.setOnAction(evt -> {
			Config.imagesInCache = !Config.imagesInCache;
			Config.updateConfig();
		});
		cacheImageSize.setText(Integer.toString(Config.cacheImageSize));
		cacheImageSize.setOnAction(evt -> {
			try
			{
				Config.cacheImageSize = Integer.parseInt(cacheImageSize.getText());
			}
			catch(NumberFormatException nfe)
			{
				Config.cacheImageSize = 0;
			}
			Config.updateConfig();
		});
		
		rebuildCacheButton.disableProperty().bind(disableCache.selectedProperty());
		imagesInCache.disableProperty().bind(disableCache.selectedProperty());
		cacheImageSize.disableProperty().bind(disableCache.selectedProperty().or(imagesInCache.selectedProperty().not()));
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
	
	public void rebuildCache()
	{
		Cache.reset();
	}
	
	public void deleteCache()
	{
		Cache.delete();
	}
}