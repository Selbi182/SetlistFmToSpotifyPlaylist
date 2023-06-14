# setlist.fm to Spotify
A simple bot that converts any given setlist.fm setlist into a Spotify playlist!

![Preview](https://i.imgur.com/XTXqsUj.png)
You can find it here: https://setlistfm.selbi.club/

I hope some people other than myself can find some utility in this app. Any feedback is welcome! You can leave it as [an issue on GitHub](https://github.com/Selbi182/SetlistFmToSpotifyPlaylist/issues) or on [the official setlist.fm forum](https://www.setlist.fm/forum/music/other-music-stuff/setlistfm-to-spotify-playlist-converter-7bd72e68).

## Options
So far three options exist to tackle some edge cases that come down to preference:
* **Include songs played from tape**: \
Whether to include songs tagged as `@Tape` or not, including songs played from tape that are by other artists. These are the grayed-out songs in setlists.
* **Use original songs for covers**: \
If an official cover by the band couldn't be found, the original song by the original artist will be used instead of simply leaving a blank in the playlist.
* **Include medleys**: \
Songs that are separated by ` / ` (space-slash-space) will be split and added one after another. Otherwise, the entire medley will be left out.
* **Strict search**: \
Dictates which search logic should be used internally. If enabled, it's `track:TRACKNAME artist:ARTISTNAME` instead of simply `TRACKNAME ARTISTNAME` without special syntax. This prevents accidental additions by unrelated artists, but it struggles with songs that tend to have a lot of special characters. Consider disabling only if you get too many song misses.

## How it works
I'm pretty sure I'm not the first one with this idea, but I couldn't find anything that _just works_ without having to do a bunch of preparation first.

It's as straight forward as possible as a result. Just paste any setlist.fm URL into the input field, hit the _Create Playlist_ button, and the bot will create a playlist on its Spotify account within seconds! You can then either choose to simply follow that playlist or copy-paste the songs in there to make your own.

Behind the scene, the bot simply uses the setlist.fm API to get all song names in the given setlist and then searches for them one-by-one on Spotify (as though you were to type in the names yourself). It will try to find _exact_ name matches of the artist and song name first, but will fall back to whatever it finds otherwise. This also means that not all playlists will be 100% accurate if the artist in question isn't fully represented on Spotify; not much I can do about that, unfortunately.

## Example
Let's try to convert [this setlist](https://www.setlist.fm/setlist/rammstein/2023/olympiastadion-helsinki-finland-53b907ed.html) into a playlist:

![Preview setlist.fm](https://i.imgur.com/zNUHG0Q.png)

In this case, songs played from tape has been unchecked. After the conversion, the page will show the playlist in an embed:

![Preview Leprous](https://i.imgur.com/5Njecg9.png)

Click the title of the playlist to get redirected to the Spotify page of the playlist. In this example that would be: https://open.spotify.com/playlist/2OGv90ONVvtSEOxXPhrA4r
