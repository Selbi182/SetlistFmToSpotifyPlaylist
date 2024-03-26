package spotify.setlist.data;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import spotify.setlist.util.SetlistUtils;

@SuppressWarnings("unused")
public class Setlist {
  private final String artistName;
  private final LocalDate eventDate;
  private final String city;
  private final String venue;
  private final String tourName;
  private final List<Song> songs;

  public Setlist(String artistName, LocalDate eventDate, String city, String venue, String tourName, List<Song> songs) {
    this.artistName = artistName;
    this.eventDate = eventDate;
    this.city = city;
    this.venue = venue;
    this.tourName = tourName;
    this.songs = songs;
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

  public List<Song> getSongs() {
    return songs;
  }

  @JsonIgnore
  public boolean hasTour() {
    return getTourName() != null && !getTourName().isBlank();
  }

  @JsonIgnore
  public String venueAndCity() {
    return getVenue() + ", " + getCity();
  }

  @Override
  public String toString() {
    return SetlistUtils.assemblePlaylistName(this);
  }

  public static class Song {
    private final int index;
    private final String songName;
    private final String artistName;
    private final String originalArtistName;
    private final boolean tape;
    private final boolean cover;
    private final boolean medleyPart;

    public Song(int index, String songName, String artistName, String originalArtistName, boolean tape, boolean cover, boolean medleyPart) {
      this.index = index;
      this.songName = songName;
      this.artistName = artistName;
      this.originalArtistName = originalArtistName;
      this.tape = tape;
      this.cover = cover;
      this.medleyPart = medleyPart;
    }

    public Song(Song song) {
      this(song.getIndex(), song.getSongName(), song.getArtistName(), song.getOriginalArtistName(), song.isTape(), song.isCover(), song.isMedleyPart());
    }

    public int getIndex() {
      return index;
    }

    public String getSongName() {
      return songName;
    }

    public String getArtistName() {
      return artistName;
    }

    public String getOriginalArtistName() {
      return originalArtistName;
    }

    public boolean isTape() {
      return tape;
    }

    public boolean isCover() {
      return cover;
    }

    public boolean isMedleyPart() {
      return medleyPart;
    }
  }
}
