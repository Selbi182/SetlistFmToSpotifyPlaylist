package spotify.setlist.data;

import java.util.List;

@SuppressWarnings("unused")
public class SetlistCreationResponse {
  private final Setlist setlist;
  private final String playlistId;
  private final List<TrackSearchResult> searchResults;
  private final long timeTaken;
  private final boolean reused;

  public SetlistCreationResponse(Setlist setlist, String playlistId, List<TrackSearchResult> searchResults, long timeTaken, boolean reused) {
    this.setlist = setlist;
    this.playlistId = playlistId;
    this.searchResults = searchResults;
    this.timeTaken = timeTaken;
    this.reused = reused;
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

  public List<TrackSearchResult> getSearchResults() {
    return searchResults;
  }

  public long getTimeTaken() {
    return timeTaken;
  }

  public boolean isReused() {
    return reused;
  }
}
