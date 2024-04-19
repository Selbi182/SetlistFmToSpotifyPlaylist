package spotify.setlist.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import se.michaelthelin.spotify.enums.AlbumType;
import se.michaelthelin.spotify.model_objects.specification.Track;
import spotify.setlist.data.Setlist;

public class SetlistUtils {
  private static final String SETLIST_DESCRIPTION = "Generated with: https://setlistfm.selbi.club";
  private static final int MAX_PLAYLIST_NAME_LENGTH = 100;
  private static final Pattern STRING_PURIFICATION_REGEX = Pattern.compile("[^\\p{L}\\p{N}]");

  private static final List<String> ALTERNATE_VERSION_WORDS = List.of(
    "instrumental",
    "orchestral",
    "symphonic",
    "live",
    "classic",
    "demo",
    "session");
  private static final Pattern ALTERNATE_VERSION_REGEX = Pattern.compile("[-(].*(" + String.join("|", ALTERNATE_VERSION_WORDS) + ")", Pattern.CASE_INSENSITIVE);

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
   * If there is a tour name, that will be used (plus the year if not within the tour name itself).
   * If there is no tour name, "Venue, Country" will be used as fallback.
   * Names over 100 characters will be cut off.
   *
   * @param setlist the setlist object
   * @return the name
   */
  public static String assemblePlaylistName(Setlist setlist) {
    String year = Integer.toString(setlist.getEventDate().getYear());

    final String tourOrVenue;
    if (setlist.hasTour()) {
      tourOrVenue = setlist.getTourName().contains(year)
        ? setlist.getTourName().replaceAll("\\s+", " ").strip()
        : String.format("%s (%s)", setlist.getTourName(), year);
    } else {
      tourOrVenue = String.format("%s (%s)", setlist.venueAndCity(), year);
    }

    String setlistName = String.format("%s [Setlist] // %s", setlist.getArtistName(), tourOrVenue);
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

  /**
   * Replaces any and all special characters in the given string with spaces.
   *
   * @param input the input string
   * @return the purified string
   */
  public static String purifyString(String input) {
    return STRING_PURIFICATION_REGEX.matcher(input).replaceAll(" ").replaceAll("\\s+", " ").trim();
  }

  /**
   * Returns true if the containedString is part of the baseString. Case is ignored.
   *
   * @param baseString the base string
   * @param containedString the string to check if it's contained
   * @return true if it's contained
   */
  public static boolean containsIgnoreCase(String baseString, String containedString) {
    return baseString.toLowerCase().contains(containedString.toLowerCase());
  }

  /**
   * Does a containsIgnoreCase check on the given input Strings.
   * If that fails, it runs {@link SetlistUtils#purifyString} on both input strings
   * before doing another containsIgnoreCase comparison.
   * This method is required for really, really weird edge cases.
   *
   * @param baseString the base string
   * @param containedString the string to check if it's contained
   * @return true if it's contained
   */
  public static boolean containsIgnoreCaseNormalized(String baseString, String containedString) {
    if (containsIgnoreCase(baseString, containedString)) {
      // In 99% of all cases, this is already enough. String purification is only necessary on some very weird edge cases.
      return true;
    }

    String string1Normalized = purifyString(baseString);
    String string2Normalized = purifyString(containedString);
    return containsIgnoreCase(string1Normalized, string2Normalized);
  }

  /**
   * Returns true if the shorter of the two given strings is contained at the start of the other string.
   * Case is ignored and any non-white special characters are ignored.
   *
   * @param a the first string
   * @param b the second string
   * @return true if yes
   */
  public static boolean isStartContained(String a, String b) {
    if (a.equals(b)) {
      // Shortcut for most cases
      return true;
    }

    a = purifyString(a);
    b = purifyString(b);
    if (a.length() >= b.length()) {
      return StringUtils.startsWithIgnoreCase(a, b);
    }
    return StringUtils.startsWithIgnoreCase(b, a);
  }

  /**
   * A very shallow method to test for whether the given track name from Spotify contains a word indicating
   * it's not the original studio version, such as "live" anywhere past the first hyphen or bracket, case-insensitive.
   *
   * @param setlistName the name as it was provided by setlist.fm
   * @param spotifyName the name as it was returned by Spotify
   * @return true if it's a live song
   */
  public static boolean containsAlternateVersionWord(String setlistName, String spotifyName) {
    return ALTERNATE_VERSION_REGEX.matcher(spotifyName).find()
      && ALTERNATE_VERSION_WORDS.stream().noneMatch(word -> containsIgnoreCase(setlistName, word));
  }

  /**
   * Returns true if the given track is contained is on an album.
   *
   * @param track the track
   * @return true if it's on an album
   */
  public static boolean isInAlbum(Track track) {
    return AlbumType.ALBUM.equals(track.getAlbum().getAlbumType());
  }
}
