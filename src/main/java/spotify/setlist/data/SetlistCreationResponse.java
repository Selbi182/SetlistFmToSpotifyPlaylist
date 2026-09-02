package spotify.setlist.data;

import java.util.List;

public record SetlistCreationResponse(Setlist setlist, SetlistCreationOptions usedOptions, String playlistId, String playlistUrl, List<TrackSearchResult> searchResults, long timeTaken, boolean reused) {
}
