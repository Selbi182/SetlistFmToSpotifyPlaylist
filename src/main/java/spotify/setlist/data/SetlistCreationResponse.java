package spotify.setlist.data;

public class SetlistCreationResponse {
  private final Setlist setlist;
  private final String playlistUrl;
  private final String playlistId;

  public SetlistCreationResponse(Setlist setlist, String playlistUrl, String playlistId) {
    this.setlist = setlist;
    this.playlistUrl = playlistUrl;
    this.playlistId = playlistId;
  }

  public Setlist getSetlist() {
    return setlist;
  }

  public String getPlaylistUrl() {
    return playlistUrl;
  }

  public String getPlaylistId() {
    return playlistId;
  }
}
