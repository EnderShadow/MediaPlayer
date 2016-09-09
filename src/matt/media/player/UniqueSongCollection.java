package matt.media.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class UniqueSongCollection extends VBox
{
	private ObservableList<AudioSource> songs;
	
	private StringProperty name;
	private StringProperty secondaryText;
	private ObjectProperty<DisplayStyle> displayStyle = new SimpleObjectProperty<>(DisplayStyle.SINGLE);
	
	@SuppressWarnings("rawtypes")
	public UniqueSongCollection(String name, String secondaryText, ObservableList<AudioSource> songs, Predicate<AudioSource> belongs, Comparator<AudioSource> sorter)
	{
		while(songs instanceof SortedList)
			songs = ((SortedList) songs).getSource();
		if(belongs == null)
			belongs = as -> false;
		this.songs = songs.filtered(belongs).sorted(sorter);
		this.songs.addListener((InvalidationListener) obs -> setupDisplay());
		this.name = new SimpleStringProperty(name);
		this.secondaryText = new SimpleStringProperty(secondaryText);
		
		prefWidthProperty().bind(Player.controller.albumListView.cellWidthProperty());
		prefHeightProperty().bind(Player.controller.albumListView.cellHeightProperty());
		setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));
		setBorder(new Border(new BorderStroke(Color.GRAY, BorderStrokeStyle.SOLID, null, BorderWidths.DEFAULT, null)));
		setupDisplay();
	}
	
	public UniqueSongCollection(String name, String secondaryText, ObservableList<AudioSource> songs)
	{
		this(name, null, songs, null, null);
	}
	
	public StringProperty nameProperty()
	{
		return name;
	}
	
	public StringProperty secondaryTextProperty()
	{
		return secondaryText;
	}
	
	public ObservableList<AudioSource> getUnmodifiableSongList()
	{
		return FXCollections.unmodifiableObservableList(songs);
	}
	
	public ObjectProperty<DisplayStyle> displayStyleProperty()
	{
		return displayStyle;
	}
	
	public void setBelongs(Predicate<AudioSource> belongs)
	{
		((FilteredList<AudioSource>) ((SortedList<AudioSource>) songs).getSource()).setPredicate(belongs);
	}
	
	public void setComparator(Comparator<AudioSource> sorter)
	{
		((SortedList<AudioSource>) songs).setComparator(sorter);
	}
	
	public List<AudioSource> getVisibleSongs()
	{
		List<AudioSource> ret = new ArrayList<AudioSource>();
		switch(displayStyle.get())
		{
		case SINGLE:
			if(!songs.isEmpty())
				ret.add(songs.get(0));
			break;
		case ALBUM:
			ret.addAll(songs.subList(0, Math.min(songs.size(), 5)));
			break;
		case QUAD:
			ret.addAll(songs.subList(0, Math.min(songs.size(), 4)));
			break;
		}
		return ret;
	}
	
	private void setupDisplay()
	{
		Platform.runLater(() -> {
			switch(displayStyle.get())
			{
			case SINGLE:
				setupSingleDisplay();
				break;
			case ALBUM:
				setupAlbumDisplay();
				break;
			case QUAD:
				setupQuadDisplay();
				break;
			}
		});
	}
	
	private void setupSingleDisplay()
	{
		ImageView image = new ImageView();
		image.imageProperty().bind(getImage(0));
		image.fitWidthProperty().bind(prefWidthProperty());
		image.fitHeightProperty().bind(prefHeightProperty());
		image.setSmooth(true);
		image.setCache(true);
		image.setPreserveRatio(true);
		
		Label name = new Label();
		name.textProperty().bind(Bindings.createStringBinding(() -> this.name.get().trim(), this.name));
		name.setTextAlignment(TextAlignment.CENTER);
		name.setFont(Font.font(name.getFont().getFamily(), 18.0D));
		name.setWrapText(true);
		Label secondaryText = new Label();
		secondaryText.textProperty().bind(Bindings.createStringBinding(() -> this.secondaryText.get().trim(), this.secondaryText));
		secondaryText.visibleProperty().bind(secondaryText.textProperty().isNotEmpty());
		secondaryText.setTextAlignment(TextAlignment.CENTER);
		secondaryText.setFont(Font.font(name.getFont().getFamily(), 14.0D));
		secondaryText.setWrapText(true);
		
		VBox nameVBox = new VBox(name, secondaryText);
		nameVBox.setAlignment(Pos.CENTER);
		
		getChildren().clear();
		setAlignment(Pos.TOP_CENTER);
		getChildren().addAll(image, nameVBox);
	}
	
	private void setupAlbumDisplay()
	{
		// TODO add fifth image and configure image layout
		
		ImageView image1 = new ImageView();
		image1.imageProperty().bind(getImage(0));
		image1.fitWidthProperty().bind(prefWidthProperty().divide(2));
		image1.fitHeightProperty().bind(prefHeightProperty().divide(2));
		image1.setSmooth(true);
		image1.setCache(true);
		image1.setPreserveRatio(true);
		
		ImageView image2 = new ImageView();
		image2.imageProperty().bind(getImage(1));
		image2.fitWidthProperty().bind(prefWidthProperty().divide(2));
		image2.fitHeightProperty().bind(prefHeightProperty().divide(2));
		image2.setSmooth(true);
		image2.setCache(true);
		image2.setPreserveRatio(true);
		
		ImageView image3 = new ImageView();
		image3.imageProperty().bind(getImage(2));
		image3.fitWidthProperty().bind(prefWidthProperty().divide(2));
		image3.fitHeightProperty().bind(prefHeightProperty().divide(2));
		image3.setSmooth(true);
		image3.setCache(true);
		image3.setPreserveRatio(true);
		
		ImageView image4 = new ImageView();
		image4.imageProperty().bind(getImage(3));
		image4.fitWidthProperty().bind(prefWidthProperty().divide(2));
		image4.fitHeightProperty().bind(prefHeightProperty().divide(2));
		image4.setSmooth(true);
		image4.setCache(true);
		image4.setPreserveRatio(true);
		
		HBox imageHBox1 = new HBox(image1, image2);
		HBox imageHBox2 = new HBox(image3, image4);
		
		Label name = new Label();
		name.textProperty().bind(Bindings.createStringBinding(() -> this.name.get().trim(), this.name));
		name.setTextAlignment(TextAlignment.CENTER);
		name.setFont(Font.font(name.getFont().getFamily(), 18.0D));
		name.setWrapText(true);
		Label secondaryText = new Label();
		secondaryText.textProperty().bind(Bindings.createStringBinding(() -> this.secondaryText.get().trim(), this.secondaryText));
		secondaryText.visibleProperty().bind(secondaryText.textProperty().isNotEmpty());
		secondaryText.setTextAlignment(TextAlignment.CENTER);
		secondaryText.setFont(Font.font(name.getFont().getFamily(), 14.0D));
		secondaryText.setWrapText(true);
		
		VBox nameVBox = new VBox(name, secondaryText);
		nameVBox.setAlignment(Pos.CENTER);
		
		getChildren().clear();
		setAlignment(Pos.TOP_CENTER);
		getChildren().addAll(imageHBox1, imageHBox2, nameVBox);
	}
	
	private void setupQuadDisplay()
	{
		ImageView image1 = new ImageView();
		image1.imageProperty().bind(getImage(0));
		image1.fitWidthProperty().bind(prefWidthProperty().divide(2));
		image1.fitHeightProperty().bind(prefHeightProperty().divide(2));
		image1.setSmooth(true);
		image1.setCache(true);
		image1.setPreserveRatio(true);
		
		ImageView image2 = new ImageView();
		image2.imageProperty().bind(getImage(1));
		image2.fitWidthProperty().bind(prefWidthProperty().divide(2));
		image2.fitHeightProperty().bind(prefHeightProperty().divide(2));
		image2.setSmooth(true);
		image2.setCache(true);
		image2.setPreserveRatio(true);
		
		ImageView image3 = new ImageView();
		image3.imageProperty().bind(getImage(2));
		image3.fitWidthProperty().bind(prefWidthProperty().divide(2));
		image3.fitHeightProperty().bind(prefHeightProperty().divide(2));
		image3.setSmooth(true);
		image3.setCache(true);
		image3.setPreserveRatio(true);
		
		ImageView image4 = new ImageView();
		image4.imageProperty().bind(getImage(3));
		image4.fitWidthProperty().bind(prefWidthProperty().divide(2));
		image4.fitHeightProperty().bind(prefHeightProperty().divide(2));
		image4.setSmooth(true);
		image4.setCache(true);
		image4.setPreserveRatio(true);
		
		HBox imageHBox1 = new HBox(image1, image2);
		HBox imageHBox2 = new HBox(image3, image4);
		
		Label name = new Label();
		name.textProperty().bind(Bindings.createStringBinding(() -> this.name.get().trim(), this.name));
		name.setTextAlignment(TextAlignment.CENTER);
		name.setFont(Font.font(name.getFont().getFamily(), 18.0D));
		name.setWrapText(true);
		Label secondaryText = new Label();
		secondaryText.textProperty().bind(Bindings.createStringBinding(() -> this.secondaryText.get().trim(), this.secondaryText));
		secondaryText.visibleProperty().bind(secondaryText.textProperty().isNotEmpty());
		secondaryText.setTextAlignment(TextAlignment.CENTER);
		secondaryText.setFont(Font.font(name.getFont().getFamily(), 14.0D));
		secondaryText.setWrapText(true);
		
		VBox nameVBox = new VBox(name, secondaryText);
		nameVBox.setAlignment(Pos.CENTER);
		
		getChildren().clear();
		setAlignment(Pos.TOP_CENTER);
		getChildren().addAll(imageHBox1, imageHBox2, nameVBox);
	}
	
	private ObjectProperty<Image> getImage(int index)
	{
		// if this starts throwing null pointer exceptions again, wrap it in a while(true) try/catch loop
		return songs.stream().map(AudioSource::imageProperty)
				.filter(ip -> ip.get() != Util.getDefaultImage())
				.skip(index)
				.findFirst()
				.orElse(new SimpleObjectProperty<Image>(Util.getDefaultImage()));
	}
}