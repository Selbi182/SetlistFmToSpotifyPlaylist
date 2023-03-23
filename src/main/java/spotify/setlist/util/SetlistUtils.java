package spotify.setlist.util;

import java.net.MalformedURLException;
import java.net.URL;

import spotify.setlist.data.Setlist;

public class SetlistUtils {
  private static final int MAX_PLAYLIST_NAME_LENGTH = 100;

  /**
   * Get the setlist ID from a setlist.fm URL.
   *
   * @param url the setlist.fm URL
   * @return the setlist ID
   * @throws MalformedURLException on a bad URL
   */
  public static String getIdFromSetlistFmUrl(String url) throws MalformedURLException {
    URL asUrl = new URL(url);
    String path = asUrl.getPath();
    String[] segments = path.split("-");
    String lastSegment = segments[segments.length - 1];
    return lastSegment.split("\\.")[0];
  }

  /**
   * Assemble the name for the setlist playlist in the following format:
   * <pre>Artist Name [Setlist] // Tour Name (Year)</pre>
   * If there is no tour name, the "Venue, Country" will be used as fallback.
   * If there is a tour name, but it doesn't contain "Tour", it will be appended.
   * Names over 100 characters will be cut off.
   *
   * @param setlist the setlist as base
   * @return the playlist string
   */
  public static String assemblePlaylistSetlistName(Setlist setlist) {
    String tourOrVenue = setlist.getVenue() + ", " + setlist.getCity();

    String tourName = setlist.getTourName().strip();
    if (!tourName.isBlank()) {
      tourOrVenue = tourName;
      if (!tourName.toLowerCase().contains("tour")) {
        tourOrVenue += " Tour";
      }
    }

    String setlistName = String.format("%s [Setlist] // %s (%d)", setlist.getArtistName(), tourOrVenue, setlist.getEventDate().getYear());
    return setlistName.substring(0, Math.min(setlistName.length(), MAX_PLAYLIST_NAME_LENGTH));
  }
}
