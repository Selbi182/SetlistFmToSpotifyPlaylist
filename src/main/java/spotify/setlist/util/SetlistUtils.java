package spotify.setlist.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SetlistUtils {
  private static final DateTimeFormatter VERBOSE_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
  private static final Pattern NUMBER_REGEX = Pattern.compile("\\d+");

  public static String getIdFromSpotifyUrl(String spotifyUrl) {
    try {
      URL url = new URL(spotifyUrl);
      String path = url.getPath();
      return path.substring(path.lastIndexOf('/') + 1);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String formatDate(LocalDate eventDate) {
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
}
