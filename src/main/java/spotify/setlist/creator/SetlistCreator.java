package spotify.setlist.creator;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.AlbumType;
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
import spotify.setlist.data.TrackSearchResult;
import spotify.setlist.setlistfm.SetlistFmApi;
import spotify.setlist.util.SetlistUtils;
import spotify.spring.SpringPortConfig;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyOptimizedExecutorService;
import spotify.util.SpotifyUtils;

@EnableScheduling
@Component
public class SetlistCreator {
  private static final String SETLIST_FM_API_TOKEN_ENV = "setlist_bot.setlist_fm_api_token";
  private static final String SETLIST_FM_DEBUG_ENV = "setlist_bot.debug_mode";

  private final SpotifyApi spotifyApi;
  private final PlaylistService playlistService;
  private final SpotifyOptimizedExecutorService executorService;
  private final SpotifyLogger logger;
  private final Environment environment;
  private final int port;

  private String setlistFmApiToken;

  /**
   * Maps setlist names to lists of playlist IDs
   * (in case one setlist name has different setlists, such as different setlists each night despite being on the same tour)
   */
  private final Map<String, List<String>> createdSetlists;

  private final DecimalFormat decimalFormat;

  SetlistCreator(SpotifyApi spotifyApi,
      PlaylistService playlistService,
      SpotifyOptimizedExecutorService spotifyOptimizedExecutorService,
      SpotifyLogger spotifyLogger,
      Environment environment,
      SpringPortConfig springPortConfig) {
    this.spotifyApi = spotifyApi;
    this.playlistService = playlistService;
    this.executorService = spotifyOptimizedExecutorService;
    this.logger = spotifyLogger;
    this.environment = environment;
    this.port = springPortConfig.getPort();

    this.createdSetlists = new ConcurrentHashMap<>();
    this.decimalFormat = new DecimalFormat("#,###");
  }

  @PostConstruct
  void init() {
    String setlistFmApiToken = environment.getProperty(SETLIST_FM_API_TOKEN_ENV);
    if (setlistFmApiToken == null || setlistFmApiToken.isBlank()) {
      throw new IllegalStateException(SETLIST_FM_API_TOKEN_ENV + " environment variable is missing!");
    } else {
      this.setlistFmApiToken = setlistFmApiToken;
      logger.info("setlist.fm API token set!");
    }

    String debugModeEnv = environment.getProperty(SETLIST_FM_DEBUG_ENV);
    if ("true".equals(debugModeEnv)) {
      logger.warning("Debug mode enabled, playlist counter calculation has been skipped!");
    } else {
      logger.info("Calculating and cleaning up existing playlists... (this might take a while)");
      refreshCreatedSetlistsCounterAndRemoveDeadPlaylists();
    }
    logger.info("Booted up! http://localhost:" + port);
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

  public String getSetlistCounterFormatted() {
    return decimalFormat.format(getSetlistCounter());
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
    List<TrackSearchResult> spotifySearchResults = findSongsOnSpotify(setlist, options);
    int totalSetlistSongsCount = setlist.getSongs().size();
    long searchResultCount = spotifySearchResults.stream()
      .filter(TrackSearchResult::hasResult)
      .count();
    if (spotifySearchResults.isEmpty() || searchResultCount < totalSetlistSongsCount / 2) {
      throw new NotFoundException("No songs found");
    }

    // Now purge all missed songs, so we can add the actual results
    List<TrackSearchResult> spotifySearchResultsFiltered = spotifySearchResults.stream()
      .filter(TrackSearchResult::hasResult)
      .collect(Collectors.toList());

    // Search for existing playlists that match the name and tracks
    // If there is a match, return that instead one instead of creating an entirely new playlist
    Optional<Playlist> existingSetlistPlaylist = searchForExistingSetlistPlaylist(setlistName, spotifySearchResultsFiltered);
    if (existingSetlistPlaylist.isPresent()) {
      Playlist existingPlaylist = existingSetlistPlaylist.get();
      long timeTaken = System.currentTimeMillis() - start;
      SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, existingPlaylist.getId(), spotifySearchResultsFiltered, timeTaken);
      logger.info(String.format("Existing setlist requested: %s - %s", existingPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
      return setlistCreationResponse;
    }

    // Create the playlist on Spotify with appropriate name, description, and image
    String description = SetlistUtils.assembleDescription(setlist);
    Playlist targetPlaylist = playlistService.createPlaylist(setlistName, description, true);
    attachArtistImage(setlist, targetPlaylist);
    List<Track> tracksToAdd = spotifySearchResultsFiltered.stream().map(TrackSearchResult::getSearchResult).collect(Collectors.toList());
    playlistService.addTracksToPlaylist(targetPlaylist, tracksToAdd);

    // Log and return the result
    long timeTaken = System.currentTimeMillis() - start;
    SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, targetPlaylist.getId(), spotifySearchResults, timeTaken);
    logger.info(String.format("New setlist created: %s - %s", targetPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
    addSetlistToCache(setlistName, targetPlaylist.getId());
    return setlistCreationResponse;
  }

  private List<TrackSearchResult> findSongsOnSpotify(Setlist setlist, String options) {
    List<String> splitOptions = Arrays.asList(options.split(","));
    boolean includeTapes = splitOptions.contains("tapes");
    boolean includeCoverOriginals = splitOptions.contains("covers");
    boolean includeMedleys = splitOptions.contains("medleys");
    boolean strictSearch = splitOptions.contains("strict-search");

    // This was originally done using SpotifyOptimizedExecutorService,
    // but ironically enough, it is significantly faster in a simple for-loop,
    // as it's less likely to cause 429 Too Many Requests errors this way.
    List<TrackSearchResult> trackSearchResults = new ArrayList<>();
    for (Setlist.Song song : setlist.getSongs()) {
      boolean notSkipped = !song.isTape() && !song.isMedleyPart()
        || song.isTape() && includeTapes
        || song.isMedleyPart() && includeMedleys;
      if (notSkipped) {
        TrackSearchResult trackSearchResult = searchTrack(song, includeCoverOriginals, strictSearch);
        trackSearchResults.add(trackSearchResult);
      } else {
        trackSearchResults.add(TrackSearchResult.skipped(song));
      }
    }
    return trackSearchResults;
  }

  public TrackSearchResult searchTrack(Setlist.Song song, boolean includeCoverOriginals, boolean strictSearch) {
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
      TrackSearchResult bestSearchResult = findBestSearchResult(song, strictSearch, songName, searchResults, queryArtistName, false);
      if (bestSearchResult.hasResult()) {
        return bestSearchResult;
      } else {
        return findBestSearchResult(song, strictSearch, songName, searchResults, queryArtistName, true);
      }
    } else if (song.isCover() && includeCoverOriginals) {
      String fallbackCoverSearchQuery = buildSearchQuery(songName, song.getOriginalArtistName(), strictSearch);
      Track[] fallbackCoverSearchResults = SpotifyCall.execute(spotifyApi.searchTracks(fallbackCoverSearchQuery).limit(4)).getItems();
      if (fallbackCoverSearchResults.length > 0) {
        Arrays.sort(fallbackCoverSearchResults, Comparator.comparingInt(Track::getPopularity));
        Track coverOriginal = fallbackCoverSearchResults[fallbackCoverSearchResults.length - 1];
        return TrackSearchResult.coverOriginal(song, coverOriginal);
      }
    }
    return TrackSearchResult.notFound(song);
  }

  private TrackSearchResult findBestSearchResult(Setlist.Song song, boolean strictSearch, String songName, List<Track> searchResults, String queryArtistName, boolean allowLive) {
    List<Track> matchingSongs = searchResults.stream()
      .filter(track -> SetlistUtils.isStartContained(queryArtistName, SpotifyUtils.getFirstArtistName(track)))
      .filter(track -> allowLive || !SetlistUtils.isShallowLive(track.getName()))
      .filter(track -> SetlistUtils.containsIgnoreCaseNormalized(track.getName(), songName))
      .sorted(Comparator.comparing(t -> t.getAlbum().getReleaseDate()))
      .collect(Collectors.toList());

    // Where possible, try to find songs from official artist's albums first
    for (Track track : matchingSongs) {
      String firstArtistName = SpotifyUtils.getFirstArtistName(track.getAlbum());
      if (AlbumType.ALBUM.equals(track.getAlbum().getAlbumType()) && queryArtistName.equalsIgnoreCase(firstArtistName) && songName.equalsIgnoreCase(track.getName())) {
        return TrackSearchResult.exactMatch(song, track);
      }
      if (AlbumType.ALBUM.equals(track.getAlbum().getAlbumType()) && SetlistUtils.isStartContained(queryArtistName, firstArtistName) && SetlistUtils.isStartContained(track.getName(), songName)) {
        return TrackSearchResult.closeMatch(song, track);
      }
    }

    // Exact string match
    for (Track track : matchingSongs) {
       if (track.getName().equalsIgnoreCase(songName)) {
         return TrackSearchResult.exactMatch(song, track);
      }
    }

    // Starts-with match (purified)
    for (Track track : matchingSongs) {
      if (SetlistUtils.isStartContained(track.getName(), songName)) {
        return TrackSearchResult.closeMatch(song, track);
      }
    }

    // Contains match (requires strict search to be off)
    if (!strictSearch) {
      for (Track track : matchingSongs) {
        if (SetlistUtils.containsIgnoreCase(track.getName(), songName)) {
          return TrackSearchResult.closeMatch(song, track);
        }
      }
    }

    // Otherwise, no song found :(
    return TrackSearchResult.notFound(song);
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

  private Optional<Playlist> searchForExistingSetlistPlaylist(String setlistName, List<TrackSearchResult> setlistTracks) {
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

  private void attachArtistImage(Setlist setlist, Playlist targetPlaylist) {
    attachArtistImage(setlist, targetPlaylist, 10);
  }

  private void attachArtistImage(Setlist setlist, Playlist targetPlaylist, int remainingAttempts) {
    try {
      if (remainingAttempts > 0) {
        Artist[] artists = SpotifyCall.execute(spotifyApi.searchArtists(setlist.getArtistName())).getItems();
        if (artists.length > 0) {
          Artist artist = artists[0];
          String image = SpotifyUtils.findLargestImage(artist.getImages());
          playlistService.attachImageToPlaylist(targetPlaylist, image);
        }
      }
    } catch (Exception e) {
      attachArtistImage(setlist, targetPlaylist, remainingAttempts - 1);
      logger.debug("Retrying attaching artist image for " + setlist.getArtistName() + " (remaining attempts: " + (remainingAttempts - 1) + ")");
    }
    if (remainingAttempts <= 0) {
      logger.warning("Failed to attach artist image -- " + setlist);
    }
  }
}
