# setlist.fm to Spotify
A simple bot that converts any given setlist.fm setlist into a Spotify playlist!

![Preview](https://i.imgur.com/gcoiWea.png)
You can find it here: https://setlistfm.selbi.club/

I hope some people other than myself can find some utility in this app. Any feedback is welcome! You can leave it as [an issue on GitHub](https://github.com/Selbi182/SetlistFmToSpotifyPlaylist/issues) or on [the official setlist.fm forum](https://www.setlist.fm/forum/music/other-music-stuff/setlistfm-to-spotify-playlist-converter-7bd72e68).

## How it works
I'm pretty sure I'm not the first one with this idea, but I couldn't find anything that _just works_ without having to do a bunch of preparation first.

It's as straight forward as possible as a result. Just paste any setlist.fm URL into the input field, hit the _Create Playlist_ button, and the bot will create a playlist on its Spotify account within seconds! You can then either choose to simply follow that playlist or copy-paste the songs in there to make your own.

Behind the scene, the bot simply uses the setlist.fm API to get all song names in the given setlist and then searches for them one-by-one on Spotify (as though you were to type in the names yourself). It will try to find _exact_ name matches of the artist and song name first, but will fall back to whatever it finds otherwise. This also means that not all playlists will be 100% accurate if the artist in question isn't fully represented on Spotify; not much I can do about that, unfortunately.

## Example
Let's try to convert this setlist into a playlist:

![Preview setlist.fm](https://i.imgur.com/cI48ZjX.png)

https://www.setlist.fm/setlist/leprous/2023/gruenspan-hamburg-germany-3bbd0044.html

After the conversion, the page will show the playlist in an embed:

![Preview Leprous](https://i.imgur.com/B2nlfrt.png)

I can't embed it here, since GitHub doesn't allow iframes. So, have the link instead: https://open.spotify.com/playlist/6q0uT1EY9aTQxwIIFFOwAH
