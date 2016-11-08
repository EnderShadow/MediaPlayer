package matt.media.player;

import java.io.IOException;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;

public class UniqueSongCollectionViewer extends AnchorPane
{
	@FXML
	private List<ImageView> images;
	@FXML
	private Label title, numSongs, duration;
	@FXML
	private TableView<AudioSource> songListTableView;
	
	public UniqueSongCollectionViewer()
	{
		FXMLLoader loader = new FXMLLoader(getClass().getResource("UniqueSongCollectionViewer.fxml"));
		loader.setRoot(this);
		loader.setController(this);
		try
		{
			loader.load();
		}
		catch(IOException ioe)
		{
			throw new RuntimeException(ioe);
		}
		
		MenuItem add2queue = new MenuItem("Add to queue");
		add2queue.setOnAction(evt -> {
			songListTableView.getSelectionModel().getSelectedItems().forEach(Player::addToQueue);
		});
		
		MenuItem deleteSongs = new MenuItem("Delete");
		deleteSongs.setOnAction(evt -> {
			songListTableView.getSelectionModel().getSelectedItems().forEach(as -> MediaLibrary.removeSong(as, false));
		});
		
		MenuItem add2playlist = new MenuItem("Add to playlist");
		add2playlist.setOnAction(evt -> {
			// TODO
		});
		
		ContextMenu contextMenu = new ContextMenu(add2queue, deleteSongs, add2playlist);
		
		songListTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		for(TableColumn<AudioSource, ?> col : songListTableView.getColumns())
		{
			@SuppressWarnings("rawtypes")
			Callback temp = col.getCellFactory();
			col.setCellFactory(callback -> {
				@SuppressWarnings("rawtypes")
				TableCell cell = (TableCell) temp.call(callback);
				cell.setContextMenu(contextMenu);
				cell.setOnMouseClicked(evt -> {
					if(evt.getButton().equals(MouseButton.PRIMARY) && evt.getClickCount() >= 2 && evt.getPickResult().getIntersectedNode() != null)
					{
						Player.clearQueue();
						Player.addToQueue(songListTableView.getSelectionModel().getSelectedItem());
						Player.play();
					}
					else if(evt.getButton().equals(MouseButton.SECONDARY))
					{
						cell.getContextMenu().show(Player.controller.window);
					}
				});
				return cell;
			});
		}
		
		List<TableColumn<AudioSource, ?>> columns = songListTableView.getColumns();
		TableColumn<AudioSource, ImageView> imageColumn = (TableColumn<AudioSource, ImageView>) columns.get(0);
		TableColumn<AudioSource, String> titleColumn = (TableColumn<AudioSource, String>) columns.get(1);
		TableColumn<AudioSource, String> durationColumn = (TableColumn<AudioSource, String>) columns.get(2);
		TableColumn<AudioSource, String> artistColumn = (TableColumn<AudioSource, String>) columns.get(3);
		TableColumn<AudioSource, String> albumColumn = (TableColumn<AudioSource, String>) columns.get(4);
		TableColumn<AudioSource, Number> playCountColumn = (TableColumn<AudioSource, Number>) columns.get(5);
		TableColumn<AudioSource, Number> ratingColumn = (TableColumn<AudioSource, Number>) columns.get(6);
		titleColumn.setSortable(true);
		durationColumn.setSortable(true);
		artistColumn.setSortable(true);
		albumColumn.setSortable(true);
		playCountColumn.setSortable(true);
		ratingColumn.setSortable(true);
		
		imageColumn.setCellValueFactory(as -> {
			ImageView iv = new ImageView();
			iv.imageProperty().bind(as.getValue().imageProperty());
			iv.fitWidthProperty().bind(as.getTableColumn().widthProperty());
			iv.setSmooth(true);
			iv.setCache(true);
			iv.setPreserveRatio(true);
			return new SimpleObjectProperty<>(iv);
		});
		titleColumn.setCellValueFactory(as -> as.getValue().titleProperty());
		durationColumn.setCellValueFactory(as -> Bindings.createStringBinding(() -> Util.formatDuration(as.getValue().durationProperty().get()), as.getValue()));
		artistColumn.setCellValueFactory(as -> as.getValue().artistProperty());
		albumColumn.setCellValueFactory(as -> as.getValue().albumProperty());
		playCountColumn.setCellValueFactory(as -> as.getValue().playCountProperty());
		ratingColumn.setCellValueFactory(as -> as.getValue().ratingProperty());
	}
	
	public ImageView getImageView(int index)
	{
		return images.get(index);
	}
	
	public Label getTitle()
	{
		return title;
	}
	
	public Label getNumSongs()
	{
		return numSongs;
	}
	
	public Label getDuration()
	{
		return duration;
	}
	
	public TableView<AudioSource> getSongListTableView()
	{
		return songListTableView;
	}
	
	public void popViewer()
	{
		((StackPane) this.getParent()).getChildren().remove(this);
	}
}