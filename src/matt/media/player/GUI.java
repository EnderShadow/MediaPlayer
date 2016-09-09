package matt.media.player;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GUI extends Application
{
	public static void main(String[] args)
	{
		launch(args);
	}
	
	@Override
	public void start(Stage primaryStage) throws Exception
	{
		Config.load();
		FXMLLoader loader = new FXMLLoader(getClass().getResource("GUI.fxml"));
		Parent root = loader.load();
		primaryStage.setTitle("Resonant Music Player");
		Scene scene = new Scene(root);
		scene.getStylesheets().add("matt/media/player/GUI.css");
		primaryStage.setScene(scene);
		Controller controller = loader.getController();
		Task<Void> task = controller.loadMusic();
		new Thread(task).start();
		primaryStage.show();
		controller.window = primaryStage;
		controller.settingsWindow = loadSettingsWindow();
	}
	
	private Stage loadSettingsWindow() throws Exception
	{
		Stage stage = new Stage();
		stage.initModality(Modality.APPLICATION_MODAL);
		FXMLLoader loader = new FXMLLoader(getClass().getResource("Settings.fxml"));
		Parent root = loader.load();
		stage.setTitle("Settings");
		stage.setScene(new Scene(root));
		SettingsController controller = loader.getController();
		controller.window = stage;
		
		return stage;
	}
}
