package spotify.setlist.data;

public class SetlistCreationResponse {
  private final Setlist setlist;
  private final String playlistId;

  public SetlistCreationResponse(Setlist setlist, String playlistId) {
    this.setlist = setlist;
    this.playlistId = playlistId;
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
}
