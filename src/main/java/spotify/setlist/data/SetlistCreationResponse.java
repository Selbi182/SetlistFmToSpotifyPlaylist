package spotify.setlist.data;

import java.util.List;

public class SetlistCreationResponse {
  private final Setlist setlist;
  private final String playlistId;
  private final List<Setlist.SongWithIndex> missedSongs;

  public SetlistCreationResponse(Setlist setlist, String playlistId, List<Setlist.SongWithIndex> missedSongs) {
    this.setlist = setlist;
    this.playlistId = playlistId;
    this.missedSongs = missedSongs;
  }

  public Setlist getSetlist() {
    return setlist;
  }

  public String getPlaylistId() {
    return playlistId;
  }

  public String getPlaylistUrl() {
    return "https://open.spotify.com/playlist/" + getPlaylistId();
  }

  public List<Setlist.SongWithIndex> getMissedSongs() {
    return missedSongs;
  }
}
