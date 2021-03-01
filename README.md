# MediaPlayer
A Media Player I'm creating for fun and my own personal use

Currently requires you to use JRE/JDK 8

### Notable features

- Nested Playlists
  - This feature allows you to add playlists to other playlists such that when a playlist is updated any playlist containing it will also be updated

## TODO

### High Priority
- Add support for adding songs from URIs in the GUI
- Add support for copying/moving media to the media player directory
- Add support for automatically adding songs in the media player directory to the library
- Add support for exporting/importing media library/playlists
- Add detection of playlists with song UUIDs that are not in the library
- Finish UI for locating missing songs

### Medium Priority
- Add Genre tab
- Add Artist tab

### Low Priority
- Song art deduplication
- Optimize load order of song art
- Better automatic sizing of the UI

### Enhancements
- Add support for plugins
  - Custom tabs
  - Additional context menu items
  - Additional audio sources
- Add support for re-ordering tabs
- Add support for hiding tabs
- Custom theme support
  - Better light theme
  - Dark theme
- JavaFX 9+ support
- Add support for websites such as Spotify
- Multiplatform support
  - Android
  - iOS (maybe?)
  - WebApp (maybe?)
