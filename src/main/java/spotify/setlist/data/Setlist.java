package spotify.setlist.data;

import java.util.Date;
import java.util.List;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import spotify.setlist.util.SetlistUtils;

public record Setlist(String artistName, Date eventDate, String city, String venue, String tourName, List<Song> songs) {

  @JsonIgnore
  public boolean hasTour() {
    return tourName() != null && !tourName().isBlank();
  }

  @JsonIgnore
  public String venueAndCity() {
    return venue() + ", " + city();
  }

  @NonNull
  @Override
  public String toString() {
    return SetlistUtils.assemblePlaylistName(this);
  }

  public record Song(int index, String songName, String artistName, String originalArtistName, @NonNull String info, boolean tape, boolean cover, boolean medleyPart) {
  }
}
