package spotify.setlist.data;

public class SetlistCreationRequest {
  private final String setlistFmId;
  private final String targetPlaylist;
  private final String password;
  private final boolean dry;

  public SetlistCreationRequest(String setlistFmId, String targetPlaylist, String password, boolean dry) {
    this.setlistFmId = setlistFmId;
    this.targetPlaylist = targetPlaylist;
    this.password = password;
    this.dry = dry;
  }

  public String getSetlistFmId() {
    return setlistFmId;
  }

  public String getTargetPlaylist() {
    return targetPlaylist;
  }

  public String getPassword() {
    return password;
  }

  public boolean isDry() {
    return dry;
  }
}