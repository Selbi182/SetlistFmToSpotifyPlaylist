# setlist.fm to Spotify
A simple bot that converts any given setlist.fm setlist into a Spotify playlist!

![Preview](https://i.imgur.com/Lo5VVEf.png)
You can find it here: https://setlistfm.selbi.club/

I hope some people other than myself can find some utility in this app. Any feedback is welcome! You can leave it as [an issue on GitHub](https://github.com/Selbi182/SetlistFmToSpotifyPlaylist/issues) or on [the official setlist.fm forum](https://www.setlist.fm/forum/music/other-music-stuff/setlistfm-to-spotify-playlist-converter-7bd72e68).

## Options
Five options exist to tackle some edge cases that come down to preference:
* **Main Tapes**: \
  Whether to include songs tagged as @Tape (the grayed-out songs in setlists) composed by the performer of the show
* **Foreign Tapes**: \
  Whether to include songs tagged as @Tape composed by OTHER artists. These find frequent use as openers, such as Metallica beginning all of their shows with 'The Ecstasy Of Gold' by Ennio Morricone.
* **Cover Originals**: \
  Some artists release official studio versions of covers; those will always be included if possible. But if an official cover by the band couldn't be found, the original song by the original artist will be used instead of simply leaving a blank in the playlist. If you don't want this fallback, disable this option.
* **Medleys**: \
  If enabled, songs that are separated by ' / ' (space-slash-space) will be split and all added as individual songs to the playlist. Otherwise, the entire medley will be excluded.
* **Image**: \
  Search for an image of the artist on Spotify and use that as playlist thumbnail. Do note that this can take quite a bit of time!

## How it works
I'm pretty sure I'm not the first one with this idea, but I couldn't find anything that _just works_ without having to do a bunch of preparation first.

It's as straight forward as possible as a result. Just paste any setlist.fm URL into the input field, hit the _Create Playlist_ button, and the bot will create a playlist on its Spotify account within seconds! You can then either choose to simply follow that playlist or copy-paste the songs in there to make your own.

Behind the scene, the bot simply uses the setlist.fm API to get all song names in the given setlist and then searches for them one-by-one on Spotify (as though you were to type in the names yourself). It will try to find _exact_ name matches of the artist and song name first, but will fall back to whatever it finds otherwise. This also means that not all playlists will be 100% accurate if the artist in question isn't fully represented on Spotify; not much I can do about that, unfortunately.

## Example
Let's try to convert [this setlist](https://www.setlist.fm/setlist/rammstein/2024/veltins-arena-gelsenkirchen-germany-63ab8613.html) into a playlist:

![Preview setlist.fm](https://i.imgur.com/wGouBsA.png)

In this case, Foreign Tapes have been disabled. After the conversion is done, the page will show the playlist in an embed, along with a summary of the search results (notice how the song *Ramm 4* wasn't found, as that song is nowhere to be found on Spotify):

![Preview Leprous](https://i.imgur.com/miY5aAi.png)

Click the title of the playlist to get redirected to the Spotify page of the playlist. In this example that would be: https://open.spotify.com/playlist/3rQw1AO4NVKnZws487pnG8
