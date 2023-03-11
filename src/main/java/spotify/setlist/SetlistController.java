package spotify.setlist;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.User;
import spotify.api.SpotifyCall;
import spotify.services.UserService;
import spotify.setlist.data.Setlist;
import spotify.setlist.setlistfm.SetlistFmApi;
import spotify.setlist.util.SetlistUtils;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyUtils;

@RestController
public class SetlistController {
  private static final String SETLIST_DESCRIPTION = "Created with: https://github.com/Selbi182/SetlistFmToSpotifyPlaylist";

  private static final String SETLIST_FM_API_TOKEN_ENV = "setlist_fm_api_token";
  public static final int MAX_PLAYLIST_NAME_LENGTH = 100;
  private final String setlistFmApiToken;

  private final SpotifyApi spotifyApi;
  private final UserService userService;
  private final SpotifyLogger logger;

  private User user;

  SetlistController(SpotifyApi spotifyApi, UserService userService, SpotifyLogger spotifyLogger) {
    this.spotifyApi = spotifyApi;
    this.userService = userService;
    this.logger = spotifyLogger;

    String setlistFmApiToken = System.getenv(SETLIST_FM_API_TOKEN_ENV);
    if (setlistFmApiToken == null || setlistFmApiToken.isBlank()) {
      throw new IllegalStateException(SETLIST_FM_API_TOKEN_ENV + " environment variable is missing!");
    }
    this.setlistFmApiToken = setlistFmApiToken;
  }

  @RequestMapping("/")
  public ModelAndView creationModel() {
    return new ModelAndView("create.html");
  }

  @CrossOrigin
  @RequestMapping("/create")
  public ResponseEntity<String> createSpotifySetlistFromSetlistFmByParam(@RequestParam("url") String url) throws MalformedURLException, NotFoundException, IndexOutOfBoundsException {
    URL asUrl = new URL(url);
    String path = asUrl.getPath();
    String[] segments = path.split("-");
    String lastSegment = segments[segments.length - 1];
    String setlistFmId = lastSegment.split("\\.")[0];
    return createSpotifySetlistFromSetlistFmById(setlistFmId);
  }

  @CrossOrigin
  @RequestMapping("/create/{setlistFmId}")
  public ResponseEntity<String> createSpotifySetlistFromSetlistFmById(@PathVariable("setlistFmId") String setlistFmId) throws NotFoundException {
    Setlist setlist = SetlistFmApi.getSetlist(setlistFmId, setlistFmApiToken);
    List<Track> songsFromSpotify = findSongsOnSpotify(setlist);

    Playlist targetPlaylist = createNewSetlistPlaylist(setlist);
    attachArtistImage(setlist, targetPlaylist);
    addSetlistSongsToPlaylist(targetPlaylist, songsFromSpotify);

    String playlistUrl = "https://open.spotify.com/playlist/" + targetPlaylist.getId();
    logger.info("New setlist created: " + targetPlaylist.getName() + " - " + playlistUrl);
    return ResponseEntity.ok(playlistUrl);
  }

  private List<Track> findSongsOnSpotify(Setlist setlist) throws NotFoundException {
    List<Track> tracksFromSpotify = new ArrayList<>();
    for (String songName : setlist.getSongNames()) {
      Track[] searchResults = SpotifyCall.execute(spotifyApi.searchTracks(setlist.getArtistName() + " " + songName).limit(10)).getItems();
      if (searchResults.length > 0) {
        Track exactNameMatch = null;
        for (Track t : searchResults) {
          if (songName.equalsIgnoreCase(t.getName())) {
            exactNameMatch = t;
            break;
          }
        }
        tracksFromSpotify.add(exactNameMatch != null ? exactNameMatch : searchResults[0]);
      } else {
        throw new NotFoundException("Couldn't find song on Spotify: " + songName);
      }
    }
    return tracksFromSpotify;
  }

  private Playlist createNewSetlistPlaylist(Setlist setlist) {
    String tourOrVenue = setlist.getTourName().isBlank() ? setlist.getVenue() + ", " + setlist.getCity() : setlist.getTourName();
    String setlistName = String.format("%s [Setlist] // %s (%d)", setlist.getArtistName(), tourOrVenue, setlist.getEventDate().getYear());
    setlistName = setlistName.substring(0, Math.min(setlistName.length(), MAX_PLAYLIST_NAME_LENGTH));
    return SpotifyCall.execute(spotifyApi.createPlaylist(getUserId(), setlistName).description(SETLIST_DESCRIPTION).public_(true));
  }

  private void attachArtistImage(Setlist setlist, Playlist targetPlaylist) {
    Artist[] artists = SpotifyCall.execute(spotifyApi.searchArtists(setlist.getArtistName())).getItems();
    if (artists.length > 0) {
      Artist artist = artists[0];
      String image = SpotifyUtils.findLargestImage(artist.getImages());
      if (image != null) {
        String base64image = SetlistUtils.toBase64Image(image);
        if (base64image != null) {
          SpotifyCall.execute(spotifyApi.uploadCustomPlaylistCoverImage(targetPlaylist.getId()).image_data(base64image));
        }
      }
    }
  }

  private void addSetlistSongsToPlaylist(Playlist targetPlaylist, List<Track> tracksFromSpotify) {
    String[] trackUris = tracksFromSpotify.stream()
      .map(Track::getUri)
      .toArray(String[]::new);
    SpotifyCall.execute(spotifyApi.addItemsToPlaylist(targetPlaylist.getId(), trackUris));
  }

  private String getUserId() {
    if (this.user == null) {
      this.user = userService.getCurrentUser();
    }
    return this.user.getId();
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<String> handleNotFoundException(NotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
  }

  @ExceptionHandler(IOException.class)
  public ResponseEntity<String> handleIOException(IOException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleGenericException(Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
  }
}
