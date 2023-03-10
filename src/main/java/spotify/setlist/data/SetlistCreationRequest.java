package spotify.setlist.data;

public class SetlistCreationRequest {
  private final String setlistFmId;
  private final String setlistFmApiToken;
  private final String targetPlaylist;
  private final String userId;
  private final boolean create;

  public SetlistCreationRequest(String setlistFmId, String setlistFmApiToken, String targetPlaylist, String userId, boolean create) {
    this.setlistFmId = setlistFmId;
    this.setlistFmApiToken = setlistFmApiToken;
    this.targetPlaylist = targetPlaylist;
    this.userId = userId;
    this.create = create;
  }

  public String getSetlistFmId() {
    return setlistFmId;
  }

  public String getSetlistFmApiToken() {
    return setlistFmApiToken;
  }

  public String getTargetPlaylist() {
    return targetPlaylist;
  }

  public String getUserId() {
    return userId;
  }

  public boolean isCreate() {
    return create;
  }
}