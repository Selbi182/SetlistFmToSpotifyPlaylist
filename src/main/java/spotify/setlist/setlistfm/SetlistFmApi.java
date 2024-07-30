package spotify.setlist.setlistfm;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import spotify.setlist.data.Setlist;

public class SetlistFmApi {
  private static final SimpleDateFormat PARSE_LFM_DATE_FORMATTER = new SimpleDateFormat("dd-MM-yyyy", Locale.US);

  public static Setlist getSetlist(String setlistFmId, String setlistFmApiToken) throws NotFoundException {
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
      Date eventDate = PARSE_LFM_DATE_FORMATTER.parse(json.get("eventDate").getAsString());
      JsonObject venueJson = json.get("venue").getAsJsonObject();
      JsonElement cityJson = venueJson.get("city");
      String country = cityJson.getAsJsonObject().get("country").getAsJsonObject().get("name").getAsString();
      String city = cityJson.getAsJsonObject().get("name").getAsString() + ", " + country;
      String venue = venueJson.get("name").getAsString();
      String tourName = json.has("tour") ? json.get("tour").getAsJsonObject().get("name").getAsString() : "";

      List<Setlist.Song> setlistSongs = new ArrayList<>();
      JsonArray asJsonArray = json.get("sets").getAsJsonObject().get("set").getAsJsonArray();
      if (asJsonArray.isEmpty()) {
        throw new IllegalStateException("Setlist mustn't be empty");
      }

      int index = 0;
      for (JsonElement sets : asJsonArray) {
        JsonArray songs = sets.getAsJsonObject().get("song").getAsJsonArray();
        for (JsonElement song : songs) {
          index++;
          JsonObject songInfo = song.getAsJsonObject();
          String songName = songInfo.get("name").getAsString();
          boolean isTape = songInfo.has("tape") && songInfo.get("tape").getAsBoolean();
          boolean isCover = songInfo.has("cover");

          String[] medleyParts = songName.split(" / ");
          boolean isMedleyPart = medleyParts.length > 1;
          for (String medleyPartOrSingleSong : medleyParts) {
            String originalArtist = isCover
              ? songInfo.get("cover").getAsJsonObject().get("name").getAsString()
              : artistName;

            String info = songInfo.has("info") ? songInfo.get("info").getAsString() : null;

            Setlist.Song setlistSong = new Setlist.Song(index, medleyPartOrSingleSong, artistName, originalArtist, info, isTape, isCover, isMedleyPart);
            setlistSongs.add(setlistSong);
          }
        }
      }

      return new Setlist(artistName, eventDate, city, venue, tourName, setlistSongs);
    } catch (Exception e) {
      throw new NotFoundException("Setlist isn't valid");
    }
  }
}
