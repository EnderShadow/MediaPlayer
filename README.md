# MediaPlayer
A Media Player I'm creating for fun and as a potential business venture

Requires Java 9+ with the following JVM arguments  
`--add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED`  
This is due to ControlsFX and JAudioTagger requiring internal APIs. This will be fixed as soon as the libraries are fixed.

Currently working features  
-Music can be played  
-Music can be added to the queue  
-Volume Controls
-The time control slider  
-The music library  
-Persistent playlist references  
-The config  
-Song/Playlist reordering  
-Queue viewer  
-Shuffling

Planned features  
-The GUI  
-The artists tab  
-The genres tab  