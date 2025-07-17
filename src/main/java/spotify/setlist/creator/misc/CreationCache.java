package spotify.setlist.creator.misc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import spotify.api.events.SpotifyApiException;
import spotify.services.PlaylistService;
import spotify.setlist.data.TrackSearchResult;
import spotify.util.SpotifyLogger;

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
  private final PlaylistService playlistService;
  private final SpotifyLogger logger;

  /**
   * Maps setlist names to lists of playlist IDs
   * (in case one setlist name has different setlists, such as different setlists each night despite being on the same tour)
   */
  private final Map<String, List<String>> createdSetlists;

  CreationCache (SpotifyApi spotifyApi,
    PlaylistService playlistService,
    SpotifyLogger logger) {
    this.spotifyApi = spotifyApi;
    this.playlistService = playlistService;
    this.logger = logger;

    this.createdSetlists = new ConcurrentHashMap<>();
  }

  @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.DAYS)
  public void refreshCreatedSetlistsCounterAndRemoveDeadPlaylists() {
    // Housekeeping:
    // Thankfully, the results of getCurrentUsersPlaylists are already in chronological order from newest to oldest,
    // so all we need to do is start at the bottom and delete enough old playlists until we land below the target limit of 10000.
    int playlistCount = SpotifyCall.execute(spotifyApi.getListOfCurrentUsersPlaylists()).getTotal();
    int playlistOverflowCount = playlistCount - SPOTIFY_PLAYLIST_LIMIT_TARGET;
    if (playlistOverflowCount > 0) {
      logger.warning("Deleting " + playlistOverflowCount + " old playlists!");
      List<PlaylistSimplified> overflownPlaylists = SpotifyCall.executePaging(spotifyApi.getListOfCurrentUsersPlaylists().offset(SPOTIFY_PLAYLIST_LIMIT_TARGET));
      if (!overflownPlaylists.isEmpty()) {
        // This part used to be done with the SpotifyOptimizedExecutorService, but for some reason it would lead to a bunch of
        // "Forbidden" and "Insufficient client scope" exceptions. My guess is that Spotify doesn't like it when too many playlists
        // are unfollowed at once, so I had to simplify it into a foreach loop. That seemed to have resolved the issue.
        for (PlaylistSimplified pl : overflownPlaylists) {
          try {
            playlistService.deletePlaylist(pl);
          } catch (SpotifyApiException e) {
            logger.error("Failed to unfollow playlist during housekeeping: " + pl.getName());
            e.printStackTrace();
          }
        }
        logger.warning("Housekeeping done!");
      }
    }

    // Build the creation cache (be warned: at 10000 playlists, this takes an INSANE amount of time!)
    List<PlaylistSimplified> allUserPlaylists = playlistService.getCurrentUsersPlaylists();
    createdSetlists.clear();
    for (PlaylistSimplified ps : allUserPlaylists) {
      String name = ps.getName();
      String id = ps.getId();
      addSetlistToCache(name, id);
    }
  }

  public Optional<Playlist> searchForExistingSetlistPlaylist(String setlistName, List<TrackSearchResult> setlistTracks) {
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

  public void addSetlistToCache(String name, String id) {
    if (!createdSetlists.containsKey(name)) {
      createdSetlists.put(name, new CopyOnWriteArrayList<>());
    }
    createdSetlists.get(name).add(id);
  }
}
