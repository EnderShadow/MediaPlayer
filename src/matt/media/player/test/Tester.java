package matt.media.player.test;

import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.stage.Stage;
import matt.media.player.AudioSource;
import matt.media.player.Player;

public class Tester extends Application
{
	public static void main(String[] args) throws Exception
	{
		//AudioSource af = new AudioSource(new File("D:/Users/Matthew/Desktop/Anime Songs to process/(01)SAVIOR OF SONG(feat. MY FIRST STORY).mp3").toURI().toString());
		AudioSource af = new AudioSource("http://dl.forunesia.com/mp3/07/%5BForunesia%5D%20Paradisus-Paradoxum.mp3");
		Player.addToQueue(af);
		Scanner scanner = new Scanner(System.in);
		String[] cmd;
		while(!(cmd = scanner.nextLine().split(" "))[0].equals("end"))
		{
			switch(cmd[0])
			{
			case "play":
				Player.play();
				break;
			case "stop":
				Player.stop();
				break;
			case "pause":
				Player.pause();
				break;
			case "addToQueue":
				Player.addToQueue(new AudioSource(new File(Arrays.stream(Arrays.copyOfRange(cmd, 1, cmd.length)).collect(Collectors.joining(" "))).toURI().toString()));
				break;
			case "next":
				Player.next();
				break;
			case "previous":
				Player.previous();
				break;
			}
		}
		Player.stop();
		System.exit(0);
	}

	@Override
	public void start(Stage arg0) throws Exception
	{
	}
}