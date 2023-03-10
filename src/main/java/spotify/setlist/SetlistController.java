package spotify.setlist;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.Track;
import spotify.api.SpotifyCall;
import spotify.setlist.data.Setlist;
import spotify.setlist.data.SetlistCreationRequest;
import spotify.util.SpotifyLogger;

@RestController
public class SetlistController {
  private static final Pattern REGEX_INTRO = Pattern.compile("\\bintro\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern REGEX_SOLO = Pattern.compile("\\bsolo\\b", Pattern.CASE_INSENSITIVE);
  private static final DateTimeFormatter PARSE_LFM_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US);
  private static final DateTimeFormatter VERBOSE_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
  private static final Pattern NUMBER_REGEX = Pattern.compile("\\d+");

  private final SpotifyApi spotifyApi;
  private final SpotifyLogger logger;

  SetlistController(SpotifyApi spotifyApi, SpotifyLogger spotifyLogger) {
    this.spotifyApi = spotifyApi;
    this.logger = spotifyLogger;
  }

  @CrossOrigin
  @PostMapping("/")
  public ResponseEntity<Setlist> createSpotifySetlistFromSetlistFm(@RequestBody SetlistCreationRequest setlistCreationRequest) throws NotFoundException {
    Setlist setlist = getSetlistFromSetlistFm(setlistCreationRequest.getSetlistFmId(), setlistCreationRequest.getSetlistFmApiToken());
    String targetPlaylistParam = setlistCreationRequest.getTargetPlaylist();
    Playlist targetPlaylist = null;
    if (targetPlaylistParam != null && !targetPlaylistParam.isBlank()) {
      // Find existing playlist
      if (setlistCreationRequest.isCreate()) {
        targetPlaylist = SpotifyCall.execute(spotifyApi.getPlaylist(targetPlaylistParam));
      }
      logger.info("Append tracks to existing setlist playlist: " + targetPlaylistParam);
    } else {
      // Create new playlist
      String setlistName = String.format("%s [Setlist]", setlist.getArtistName());
      String eventDate = formatDate(setlist.getEventDate());
      String setlistDescription = String.format("%s // %s, %s", eventDate, setlist.getVenue(), setlist.getCity());
      if (!setlist.getTourName().isBlank()) {
        setlistDescription = String.format("%s (%s)", setlist.getTourName(), setlistDescription);
      }
      if (setlistCreationRequest.isCreate()) {
        targetPlaylist = SpotifyCall.execute(spotifyApi.createPlaylist(setlistCreationRequest.getUserId(), setlistName).description(setlistDescription).public_(false));
      }
      logger.info("New setlist created: " + setlistName + ": " + setlistDescription);
    }

    // Add the songs
    if (setlistCreationRequest.isCreate() && targetPlaylist != null) {
      addSetlistSongsToPlaylist(targetPlaylist, setlist);
    }

    return ResponseEntity.ok(setlist);
  }

  private String formatDate(LocalDate eventDate) {
    String formattedDate = eventDate.format(VERBOSE_DATE_FORMATTER);
    Matcher matcher = NUMBER_REGEX.matcher(formattedDate);
    boolean dateFound = matcher.find();
    if (dateFound) {
      int day = Integer.parseInt(matcher.group());
      String suffix = "th";
      if (day < 11 || day > 13) {
        switch (day % 10) {
          case 1:
            suffix = "st";
            break;
          case 2:
            suffix = "nd";
            break;
          case 3:
            suffix = "rd";
            break;
        }
        formattedDate = matcher.replaceFirst(day + suffix);
      }
    }
    return formattedDate;
  }

  private Setlist getSetlistFromSetlistFm(String setlistFmId, String setlistFmApiToken) throws NotFoundException {
    try {
      String url = UriComponentsBuilder.newInstance()
          .scheme("https")
          .host("api.setlist.fm")
          .path("/rest/1.0/setlist/" + setlistFmId).build().toUriString();
      String rawJson = Jsoup.connect(url)
          .header("Accept", "application/json")
          .header("x-api-key", setlistFmApiToken).ignoreContentType(true).execute().body();
      JsonObject json = JsonParser.parseString(rawJson).getAsJsonObject();

      String artistName = json.get("artist").getAsJsonObject().get("name").getAsString();
      LocalDate eventDate = LocalDate.parse(json.get("eventDate").getAsString(), PARSE_LFM_DATE_FORMATTER);
      JsonObject venueJson = json.get("venue").getAsJsonObject();
      JsonElement cityJson = venueJson.get("city");
      String country = cityJson.getAsJsonObject().get("country").getAsJsonObject().get("name").getAsString();
      String city = cityJson.getAsJsonObject().get("name").getAsString() + ", " + country;
      String venue = venueJson.get("name").getAsString();
      String tourName = json.has("tour") ? json.get("tour").getAsJsonObject().get("name").getAsString() : "";

      List<String> songNames = new ArrayList<>();
      JsonArray asJsonArray = json.get("sets").getAsJsonObject().get("set").getAsJsonArray();
      for (JsonElement sets : asJsonArray) {
        JsonArray songs = sets.getAsJsonObject().get("song").getAsJsonArray();
        for (JsonElement song : songs) {
          JsonObject songInfo = song.getAsJsonObject();
          String songName = songInfo.get("name").getAsString();
          boolean isRedundantTape = songInfo.has("tape") && songInfo.get("tape").getAsBoolean() && (songInfo.has("cover") || songInfo.has("info"));
          boolean isRedundantSong = songName.isBlank()
              || REGEX_INTRO.matcher(songName).find()
              || REGEX_SOLO.matcher(songName).find();
          if (!isRedundantTape && !isRedundantSong) {
            String[] medleyParts = songName.split(" / ");
            songNames.addAll(Arrays.asList(medleyParts));
          }
        }
      }

      return new Setlist(artistName, eventDate, city, venue, tourName, songNames);

    } catch (IOException e) {
      throw new NotFoundException("Error during API call to setlist.fm");
    }
  }

  private void addSetlistSongsToPlaylist(Playlist targetPlaylist, Setlist setlist) throws NotFoundException {
    // Find every song on Spotify
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
}
