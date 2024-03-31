package spotify.setlist.creator;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import spotify.api.SpotifyCall;
import spotify.services.PlaylistService;
import spotify.setlist.data.TrackSearchResult;
import spotify.util.SpotifyOptimizedExecutorService;

@EnableScheduling
@Component
public class CreationCache {
  private final SpotifyApi spotifyApi;
  private final PlaylistService playlistService;
  private final SpotifyOptimizedExecutorService executorService;

  private final DecimalFormat decimalFormat;

  /**
   * Maps setlist names to lists of playlist IDs
   * (in case one setlist name has different setlists, such as different setlists each night despite being on the same tour)
   */
  private final Map<String, List<String>> createdSetlists;

  CreationCache (SpotifyApi spotifyApi,
      PlaylistService playlistService,
      SpotifyOptimizedExecutorService spotifyOptimizedExecutorService) {
    this.spotifyApi = spotifyApi;
    this.playlistService = playlistService;
    this.executorService = spotifyOptimizedExecutorService;

    this.decimalFormat = new DecimalFormat("#,###");
    this.createdSetlists = new ConcurrentHashMap<>();
  }

  @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.DAYS)
  public void refreshCreatedSetlistsCounterAndRemoveDeadPlaylists() {
    // TODO more proper cleanup of already existing duplicated playlists (from debugging and stuff)

    List<PlaylistSimplified> allUserPlaylists = playlistService.getCurrentUsersPlaylists();

    createdSetlists.clear();
    for (PlaylistSimplified ps : allUserPlaylists) {
      String name = ps.getName();
      String id = ps.getId();
      addSetlistToCache(name, id);
    }

    List<Callable<String>> toRemove = allUserPlaylists.stream()
      .filter(pl -> (pl.getTracks().getTotal() == 0))
      .map(pl -> (Callable<String>) () -> SpotifyCall.execute(spotifyApi.unfollowPlaylist(pl.getId())))
      .collect(Collectors.toList());
    if (!toRemove.isEmpty()) {
      executorService.executeAndWait(toRemove);
    }
  }

  public int getSetlistCounter() {
    return createdSetlists.values().stream()
      .mapToInt(List::size)
      .sum();
  }

  public String getSetlistCounterFormatted() {
    return decimalFormat.format(getSetlistCounter());
  }

  protected Optional<Playlist> searchForExistingSetlistPlaylist(String setlistName, List<TrackSearchResult> setlistTracks) {
    List<String> playlistIdsForSetlistName = createdSetlists.get(setlistName);
    if (playlistIdsForSetlistName == null || playlistIdsForSetlistName.isEmpty()) {
      // This is the first time the playlist has been fetched
      return Optional.empty();
    } else {
      // Setlist name has been found again, check if the playlist already exists
      for (String playlistId : playlistIdsForSetlistName) {
        Playlist playlist = playlistService.getPlaylist(playlistId);
        List<PlaylistTrack> playlistTracks = Arrays.asList(playlist.getTracks().getItems());
        if (setlistTracks.size() == playlistTracks.size()) {
          List<String> currentSetlistTrackIds = setlistTracks.stream()
            .map(TrackSearchResult::getSearchResult)
            .map(Track::getId)
            .collect(Collectors.toList());
          List<String> existingPlaylistTrackIds = playlistTracks.stream()
            .map(PlaylistTrack::getTrack)
            .map(IPlaylistItem::getId)
            .collect(Collectors.toList());
          if (currentSetlistTrackIds.equals(existingPlaylistTrackIds)) {
            return Optional.of(playlist);
          }
        }
      }
    }
    return Optional.empty();
  }

  protected void addSetlistToCache(String name, String id) {
    if (!createdSetlists.containsKey(name)) {
      createdSetlists.put(name, new CopyOnWriteArrayList<>());
    }
    createdSetlists.get(name).add(id);
  }
}
