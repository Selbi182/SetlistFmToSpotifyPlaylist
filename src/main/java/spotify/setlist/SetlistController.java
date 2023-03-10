package spotify.setlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.User;
import spotify.api.SpotifyCall;
import spotify.services.UserService;
import spotify.setlist.data.Setlist;
import spotify.setlist.data.SetlistCreationRequest;
import spotify.setlist.setlistfm.SetlistFmApi;
import spotify.setlist.util.SetlistUtils;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyUtils;

@RestController
public class SetlistController {

  private static final String SETLIST_FM_API_TOKEN_ENV = "setlist_fm_api_token";
  private final String setlistFmApiToken;

  private static final String PASSWORD_ENV = "password";
  private final String password;

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

    String password = System.getenv(PASSWORD_ENV);
    if (password != null && !password.isBlank()) {
      this.password = password;
    } else {
      throw new IllegalStateException("Password environment variable is missing!");
    }
  }

  @CrossOrigin
  @PostMapping("/")
  public ResponseEntity<Setlist> createSpotifySetlistFromSetlistFm(@RequestBody SetlistCreationRequest setlistCreationRequest) throws NotFoundException {
    if (Objects.equals(this.password, setlistCreationRequest.getPassword())) {
      Setlist setlist = SetlistFmApi.getSetlist(setlistCreationRequest.getSetlistFmId(), setlistFmApiToken);
      String targetPlaylistParam = setlistCreationRequest.getTargetPlaylist();

      Playlist targetPlaylist;
      if (targetPlaylistParam == null || targetPlaylistParam.isBlank()) {
        targetPlaylist = createNewSetlistPlaylist(setlistCreationRequest, setlist);
      } else {
        targetPlaylist = appendToExistingSetlistPlaylist(targetPlaylistParam);
      }

      if (!setlistCreationRequest.isDry() && targetPlaylist != null) {
        addSetlistSongsToPlaylist(targetPlaylist, setlist);
      }
      return ResponseEntity.ok(setlist);
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  private Playlist createNewSetlistPlaylist(SetlistCreationRequest setlistCreationRequest, Setlist setlist) {
    String setlistName = String.format("%s [Setlist]", setlist.getArtistName());
    String eventDate = SetlistUtils.formatDate(setlist.getEventDate());
    String setlistDescription = String.format("%s // %s, %s", eventDate, setlist.getVenue(), setlist.getCity());
    if (!setlist.getTourName().isBlank()) {
      setlistDescription = String.format("%s (%s)", setlist.getTourName(), setlistDescription);
    }
    Playlist targetPlaylist = null;
    if (!setlistCreationRequest.isDry()) {
      targetPlaylist = SpotifyCall.execute(spotifyApi.createPlaylist(getUserId(), setlistName).description(setlistDescription).public_(false));

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
    logger.info("New setlist created: " + setlistName + ": " + setlistDescription);
    return targetPlaylist;
  }

  private Playlist appendToExistingSetlistPlaylist(String targetPlaylistParam) {
    String targetPlaylistId = SetlistUtils.getIdFromSpotifyUrl(targetPlaylistParam);
    Playlist targetPlaylist = SpotifyCall.execute(spotifyApi.getPlaylist(targetPlaylistId));
    logger.info("Append tracks to existing setlist playlist: " + targetPlaylist.getName());
    return targetPlaylist;
  }

  private void addSetlistSongsToPlaylist(Playlist targetPlaylist, Setlist setlist) throws NotFoundException {
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
}
