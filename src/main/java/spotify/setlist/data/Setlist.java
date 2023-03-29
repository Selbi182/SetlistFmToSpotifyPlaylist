package spotify.setlist.data;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Setlist {
  private final String artistName;
  private final LocalDate eventDate;
  private final String city;
  private final String venue;
  private final String tourName;
  private final List<String> songNames;

  public Setlist(String artistName, LocalDate eventDate, String city, String venue, String tourName, List<String> songNames) {
    this.artistName = artistName;
    this.eventDate = eventDate;
    this.city = city;
    this.venue = venue;
    this.tourName = tourName;
    this.songNames = songNames;
  }

  public String getArtistName() {
    return artistName;
  }

  public LocalDate getEventDate() {
    return eventDate;
  }

  public String getCity() {
    return city;
  }

  public String getVenue() {
    return venue;
  }

  public String getTourName() {
    return tourName;
  }

  public List<String> getSongNames() {
    return songNames;
  }

  @JsonIgnore
  public boolean hasTour() {
    return getTourName() != null && !getTourName().isBlank();
  }

  @JsonIgnore
  public String venueAndCity() {
    return getVenue() + ", " + getCity();
  }
}
