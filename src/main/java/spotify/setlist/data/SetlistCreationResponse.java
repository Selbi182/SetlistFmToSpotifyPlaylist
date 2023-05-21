package spotify.setlist.data;

public class SetlistCreationResponse {
  private final Setlist setlist;
  private final String playlistId;
  private final int missedSongs;

  public SetlistCreationResponse(Setlist setlist, String playlistId, int missedSongs) {
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

  public int getMissedSongs() {
    return missedSongs;
  }
}
