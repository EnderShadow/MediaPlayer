package matt.media.player;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class FileTransferDisplay
{
	private Stage window;
	
	public FileTransferDisplay(String title, ReadOnlyDoubleProperty progressProperty, ReadOnlyStringProperty progressAsText)
	{
		window = new Stage();
		window.initModality(Modality.APPLICATION_MODAL);
		window.setTitle(title);
		window.setMinWidth(300);
		window.setMinHeight(100);
		
		HBox hbox = new HBox(10.0D);
		hbox.setAlignment(Pos.CENTER);

		ProgressBar progressBar = new ProgressBar();
		
		if(progressProperty != null)
		{
			progressBar.progressProperty().bind(progressProperty);
			hbox.getChildren().add(progressBar);
		}
		
		if(progressAsText != null)
		{
			Label label = new Label();
			label.textProperty().bind(progressAsText);
			hbox.getChildren().add(label);
		}
		
		if(hbox.getChildren().isEmpty())
			hbox.getChildren().add(progressBar);
		
		VBox layout = new VBox(10.0D);
		layout.getChildren().addAll(hbox);
		layout.setAlignment(Pos.CENTER);
		
		Scene scene = new Scene(layout);
		
		window.setScene(scene);
	}
	
	public void display()
	{
		window.show();
	}
	
	public void hide()
	{
		window.hide();
	}
}