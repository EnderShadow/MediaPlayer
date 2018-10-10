# MediaPlayer
A Media Player I'm creating for fun and as a potential business venture

Requires Java 8 with the following JVM argument  
`-DVLCJ_INITX=no`  
This is due to a bug in linux (or at least Ubuntu 18.04.1) where the JVM crashes when using vlcj and other native libraries when opening a file/folder chooser.

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