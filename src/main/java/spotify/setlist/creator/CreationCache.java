package spotify.setlist.creator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
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
  /**
   * Limit of playlists a single Spotify account can actually have. This doesn't appear to be documented anywhere,
   * I learned this the hard way when the bot stopped working one day at exactly this amount of playlists.
   */
  public static final int SPOTIFY_PLAYLIST_LIMIT_REAL = 11000;

  /**
   * Limit of playlists the bot should target. Currently set to 1000 playlists less.
   */
  public static final int SPOTIFY_PLAYLIST_LIMIT_TARGET = SPOTIFY_PLAYLIST_LIMIT_REAL - 1000;


  private final SpotifyApi spotifyApi;
  private final CounterManager counterManager;
  private final PlaylistService playlistService;
  private final SpotifyOptimizedExecutorService executorService;


  /**
   * Maps setlist names to lists of playlist IDs
   * (in case one setlist name has different setlists, such as different setlists each night despite being on the same tour)
   */
  private final Map<String, List<String>> createdSetlists;

  CreationCache (SpotifyApi spotifyApi,
      CounterManager counterManager,
      PlaylistService playlistService,
      SpotifyOptimizedExecutorService spotifyOptimizedExecutorService) {
    this.spotifyApi = spotifyApi;
    this.counterManager = counterManager;
    this.playlistService = playlistService;
    this.executorService = spotifyOptimizedExecutorService;

    this.createdSetlists = new ConcurrentHashMap<>();
  }

  @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.DAYS)
  public void refreshCreatedSetlistsCounterAndRemoveDeadPlaylists() {
    List<PlaylistSimplified> allUserPlaylists = playlistService.getCurrentUsersPlaylists();

    int playlistOverflowCount = allUserPlaylists.size() - SPOTIFY_PLAYLIST_LIMIT_TARGET;
    if (playlistOverflowCount > 0) {
      // Housekeeping:
      // Thankfully, the results of getCurrentUsersPlaylists are already in chronological order from newest to oldest,
      // so all we need to do is start at the bottom and keep looking for dead, unused playlists until we can kill off
      // enough obsolete ones to land below the target limit of 10000.

      ListIterator<PlaylistSimplified> iterator = allUserPlaylists.listIterator(allUserPlaylists.size());
      List<PlaylistSimplified> obsolete = new ArrayList<>();
      while (iterator.hasPrevious() && playlistOverflowCount > 0) {
        PlaylistSimplified ps = iterator.previous();

        Playlist pl = playlistService.getPlaylist(ps.getId());
        if (pl.getFollowers() != null && pl.getFollowers().getTotal() != null && pl.getFollowers().getTotal() == 0
          || pl.getTracks() != null && pl.getTracks().getTotal() != null && pl.getTracks().getTotal() == 0) {
          obsolete.add(ps);
          playlistOverflowCount--;
        }
      }

      if (!obsolete.isEmpty()) {
        List<Callable<String>> toRemove = obsolete.stream()
          .map(pl -> (Callable<String>) () -> SpotifyCall.execute(spotifyApi.unfollowPlaylist(pl.getId())))
          .collect(Collectors.toList());
        System.out.println("Deleting " + toRemove.size() + " old playlists!");
        executorService.executeAndWait(toRemove);
      }

      // Recursive call to build the creation cache with new data (slow but meh)
      refreshCreatedSetlistsCounterAndRemoveDeadPlaylists();
    } else {
      // Build the creation cache
      createdSetlists.clear();
      for (PlaylistSimplified ps : allUserPlaylists) {
        String name = ps.getName();
        String id = ps.getId();
        addSetlistToCache(name, id);
      }
    }
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
