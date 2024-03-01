package spotify.setlist.creator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import spotify.api.SpotifyCall;
import spotify.services.PlaylistService;
import spotify.setlist.data.Setlist;
import spotify.setlist.data.SetlistCreationResponse;
import spotify.setlist.setlistfm.SetlistFmApi;
import spotify.setlist.util.SetlistUtils;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyOptimizedExecutorService;
import spotify.util.SpotifyUtils;

@EnableScheduling
@Component
public class SetlistCreator {

  private static final String SETLIST_FM_API_TOKEN_ENV = "setlist_fm_api_token";
  private final String setlistFmApiToken;

  private final SpotifyApi spotifyApi;
  private final PlaylistService playlistService;
  private final SpotifyOptimizedExecutorService executorService;
  private final SpotifyLogger logger;

  /**
   * Maps setlist names to lists of playlist IDs
   * (in case one setlist name has different setlists, such as different setlists each night despite being on the same tour)
   */
  private final Map<String, List<String>> createdSetlists;

  SetlistCreator(SpotifyApi spotifyApi, PlaylistService playlistService, SpotifyOptimizedExecutorService spotifyOptimizedExecutorService, SpotifyLogger spotifyLogger) {
    this.spotifyApi = spotifyApi;
    this.playlistService = playlistService;
    this.executorService = spotifyOptimizedExecutorService;
    this.logger = spotifyLogger;

    this.createdSetlists = new ConcurrentHashMap<>();

    String setlistFmApiToken = System.getenv(SETLIST_FM_API_TOKEN_ENV);
    if (setlistFmApiToken == null || setlistFmApiToken.isBlank()) {
      throw new IllegalStateException(SETLIST_FM_API_TOKEN_ENV + " environment variable is missing!");
    }
    this.setlistFmApiToken = setlistFmApiToken;
  }

  @PostConstruct
  void calculateExistingSetlists() {
    if (System.getenv("setlist_fm_bot_debug_mode") != null) {
      logger.info("Debug mode enabled, playlist counter calculation has been skipped!");
    } else {
      logger.info("Calculating and cleaning up existing playlists... (this might take a while)");
      refreshCreatedSetlistsCounterAndRemoveDeadPlaylists();
    }
  }

  @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.DAYS)
  public void refreshCreatedSetlistsCounterAndRemoveDeadPlaylists() {
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

  private void addSetlistToCache(String name, String id) {
    if (!createdSetlists.containsKey(name)) {
      createdSetlists.put(name, new CopyOnWriteArrayList<>());
    }
    createdSetlists.get(name).add(id);
  }

  public int getSetlistCounter() {
    return createdSetlists.values().stream()
      .mapToInt(List::size)
      .sum();
  }

  /**
   * Create a setlist playlist from the given setlist.fm ID
   *
   * @param setlistFmId the setlist.fm ID
   * @param options any potential option flags (separated by comma)
   * @return a SetlistCreationResponse with the result
   * @throws NotFoundException if either the setlist or any of its songs couldn't be found
   */
  public SetlistCreationResponse createSetlist(String setlistFmId, String options) throws NotFoundException {
    long start = System.currentTimeMillis();

    // Find the setlist.fm setlist
    Setlist setlist = SetlistFmApi.getSetlist(setlistFmId, setlistFmApiToken);

    // Assemble the name for the playlist and search for each song on Spotify
    String setlistName = setlist.toString();
    List<Track> songsFromSpotify = findSongsOnSpotify(setlist, options);
    int totalSetlistSongsCount = setlist.getSongs().size();
    int searchResultCount = songsFromSpotify.size();
    if (songsFromSpotify.isEmpty() || searchResultCount < totalSetlistSongsCount / 2) {
      throw new NotFoundException("No songs found");
    }

    // Find the missed songs (should be 0 ideally)
    List<Setlist.SongWithIndex> missedSongs = new ArrayList<>();
    for (int i = 0; i < setlist.getSongs().size(); i++) {
      Track searchResultSong = songsFromSpotify.get(i);
      if (searchResultSong == null) {
        Setlist.Song setlistSong = setlist.getSongs().get(i);
        missedSongs.add(new Setlist.SongWithIndex(setlistSong, i + 1));
      }
    }
    songsFromSpotify = songsFromSpotify.stream()
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    // Search for existing playlists that match the name and tracks
    // If there is a match, return that instead one instead of creating an entirely new playlist
    Optional<Playlist> existingSetlistPlaylist = searchForExistingSetlistPlaylist(setlistName, songsFromSpotify);
    if (existingSetlistPlaylist.isPresent()) {
      Playlist existingPlaylist = existingSetlistPlaylist.get();
      long timeTaken = System.currentTimeMillis() - start;
      SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, existingPlaylist.getId(), missedSongs, timeTaken);
      logger.info(String.format("Existing setlist requested: %s - %s", existingPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
      return setlistCreationResponse;
    }

    // Create the playlist on Spotify with appropriate name, description, and image
    String description = SetlistUtils.assembleDescription(setlist);
    Playlist targetPlaylist = playlistService.createPlaylist(setlistName, description, true);
    attachArtistImage(setlist, targetPlaylist);
    playlistService.addTracksToPlaylist(targetPlaylist, songsFromSpotify);

    // Log and return the result
    long timeTaken = System.currentTimeMillis() - start;
    SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, targetPlaylist.getId(), missedSongs, timeTaken);
    logger.info(String.format("New setlist created: %s - %s", targetPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
    addSetlistToCache(setlistName, targetPlaylist.getId());
    return setlistCreationResponse;
  }

  private List<Track> findSongsOnSpotify(Setlist setlist, String options) {
    List<String> splitOptions = Arrays.asList(options.split(","));
    boolean includeTapes = splitOptions.contains("tapes");
    boolean includeCoverOriginals = splitOptions.contains("covers");
    boolean includeMedleys = splitOptions.contains("medleys");
    boolean strictSearch = splitOptions.contains("strict-search");

    // This was originally done using SpotifyOptimizedExecutorService,
    // but ironically enough, it is significantly faster in a simple for-loop,
    // as it's less likely to cause 429 Too Many Requests errors this way.
    List<Track> tracks = new ArrayList<>();
    for (Setlist.Song song : setlist.getSongs()) {
      Track track = null;
      if (!song.isTape() && !song.isMedleyPart()
          || song.isTape() && includeTapes
          || song.isMedleyPart() && includeMedleys) {
          track = searchTrack(song, includeCoverOriginals, strictSearch);
        }
      tracks.add(track);
    }
    return tracks;
  }

  public Track searchTrack(Setlist.Song song, boolean includeCoverOriginals, boolean strictSearch) {
    String queryArtistName = song.isTape() ? song.getOriginalArtistName() : song.getArtistName();
    String songName = song.getSongName();
    String searchQuery = buildSearchQuery(songName, queryArtistName, strictSearch);

    List<Track> searchResults = Arrays.asList(SpotifyCall.execute(spotifyApi.searchTracks(searchQuery)).getItems());

    // One-time retry for songs starting with "The", because sometimes that word is missing on the Spotify version of the track
    if (searchResults.isEmpty() && StringUtils.startsWithIgnoreCase(songName, "The ")) {
      songName = songName.replaceFirst("The ", "");
      searchQuery = buildSearchQuery(songName, queryArtistName, strictSearch);
      searchResults = Arrays.asList(SpotifyCall.execute(spotifyApi.searchTracks(searchQuery)).getItems());
    }

    if (!searchResults.isEmpty()) {
      final String finalSongName = songName;
      List<Track> matchingSongs = searchResults.stream()
        .filter(track -> SetlistUtils.isStartContained(queryArtistName, SpotifyUtils.getFirstArtistName(track)))
        .filter(track -> !SetlistUtils.isShallowLive(track.getName()))
        .filter(track -> SetlistUtils.containsIgnoreCaseNormalized(track.getName(), finalSongName))
        .collect(Collectors.toList());
      if (matchingSongs.size() > 1) {
        // If multiple songs containing the song name have been found, try to find the exact matching song

        // Exact string match
        for (Track track : matchingSongs) {
           if (track.getName().equalsIgnoreCase(songName)) {
            return track;
          }
        }

        // Starts with match
        for (Track track : matchingSongs) {
          if (SetlistUtils.isStartContained(track.getName(), songName)) {
            return track;
          }
        }

        // Contains match (requires strict search to be off)
        if (!strictSearch) {
          for (Track track : matchingSongs) {
            if (SetlistUtils.containsIgnoreCase(track.getName(), songName)) {
              return track;
            }
          }
        }
      } else if (matchingSongs.size() == 1){
        return matchingSongs.get(0);
      }
    } else if (song.isCover() && includeCoverOriginals) {
      String fallbackCoverSearchQuery = buildSearchQuery(songName, song.getOriginalArtistName(), strictSearch);
      Track[] fallbackCoverSearchResults = SpotifyCall.execute(spotifyApi.searchTracks(fallbackCoverSearchQuery).limit(4)).getItems();
      if (fallbackCoverSearchResults.length > 0) {
        Arrays.sort(fallbackCoverSearchResults, Comparator.comparingInt(Track::getPopularity));
        return fallbackCoverSearchResults[fallbackCoverSearchResults.length - 1];
      }
    }
    return null;
  }

  private String buildSearchQuery(String songName, String artistName, boolean strictSearch) {
    if (strictSearch) {
      // Replace all special characters with white space cause Spotify struggled with apostrophes and such during strict search
      String artistNamePurified = SetlistUtils.purifyString(artistName);
      String songNamePurified = SetlistUtils.purifyString(songName);
      return String.format("artist:%s track:%s", artistNamePurified, songNamePurified);
    }
    return artistName + " " + songName;
  }

  private Optional<Playlist> searchForExistingSetlistPlaylist(String setlistName, List<Track> setlistTracks) {
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

  private void attachArtistImage(Setlist setlist, Playlist targetPlaylist) {
    try {
      Artist[] artists = SpotifyCall.execute(spotifyApi.searchArtists(setlist.getArtistName())).getItems();
      if (artists.length > 0) {
        Artist artist = artists[0];
        String image = SpotifyUtils.findLargestImage(artist.getImages());
        playlistService.attachImageToPlaylist(targetPlaylist, image);
      }
    } catch (Exception e) {
      logger.warning("Failed to attach artist image -- " + setlist);
      e.printStackTrace();
    }
  }
}
