package matt.media.player;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class AlertBox
{
	private Object retVal;
	private Stage window;
	
	public AlertBox(String title, String message, String[] options, Object... returnValues)
	{
		window = new Stage();
		window.initModality(Modality.APPLICATION_MODAL);
		window.setTitle(title);
		window.setMinWidth(250);
		
		Label label = new Label(message);
		List<Button> buttons = new ArrayList<Button>();
		for(int i = 0; i < options.length; i++)
		{
			int j = i;
			Button button = new Button(options[i]);
			button.setOnAction(evt -> {
				retVal = returnValues[j];
				window.close();
			});
			buttons.add(button);
		}
		HBox hbox = new HBox(10.0D);
		hbox.getChildren().addAll(buttons);
		VBox layout = new VBox(10.0D);
		layout.getChildren().addAll(label, hbox);
		layout.setAlignment(Pos.CENTER);
		
		Scene scene = new Scene(layout);
		
		window.setScene(scene);
	}
	
	public void display()
	{
		window.showAndWait();
	}
	
	public void hide()
	{
		window.hide();
	}
	
	public Object getReturnValue()
	{
		return retVal;
	}
}