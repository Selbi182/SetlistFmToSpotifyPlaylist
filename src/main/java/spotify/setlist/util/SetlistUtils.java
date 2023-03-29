package spotify.setlist.util;

import java.net.MalformedURLException;
import java.net.URL;

import spotify.setlist.data.Setlist;

public class SetlistUtils {
  private static final String SETLIST_DESCRIPTION = "Generated with: https://setlistfm.selbi.club";

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
   * @param setlist the setlist object
   * @return the name
   */
  public static String assemblePlaylistName(Setlist setlist) {
    String year = Integer.toString(setlist.getEventDate().getYear());

    final String tourOrVenue;
    if (setlist.hasTour()) {
      String formattedTour = setlist.getTourName().replace(year, "").replaceAll("\\s+", " ").strip();
      tourOrVenue = formattedTour + (formattedTour.toLowerCase().contains("tour") ? "" : " Tour");
    } else {
      tourOrVenue = setlist.venueAndCity();
    }

    String setlistName = String.format("%s [Setlist] // %s (%s)", setlist.getArtistName(), tourOrVenue, year);
    return setlistName.substring(0, Math.min(setlistName.length(), MAX_PLAYLIST_NAME_LENGTH));
  }

  /**
   * Assemble the description for the setlist playlist. If the setlist has a tour assigned,
   * the description will contain the venue, date, and branding. Otherwise, only the branding.
   *
   * @param setlist the setlist object
   * @return the description
   */
  public static String assembleDescription(Setlist setlist) {
    if (setlist.hasTour()) {
      return String.format("%s (%s) // %s", setlist.venueAndCity(), setlist.getEventDate(), SETLIST_DESCRIPTION);
    }
    return SETLIST_DESCRIPTION;
  }
}
