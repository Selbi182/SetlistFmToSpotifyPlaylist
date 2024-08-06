package spotify.setlist.creator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.Track;
import spotify.api.SpotifyCall;
import spotify.services.PlaylistService;
import spotify.setlist.data.Setlist;
import spotify.setlist.data.SetlistCreationOptions;
import spotify.setlist.data.SetlistCreationResponse;
import spotify.setlist.data.TrackSearchResult;
import spotify.setlist.setlistfm.SetlistFmApi;
import spotify.setlist.util.SetlistUtils;
import spotify.spring.SpringPortConfig;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyUtils;

@Component
public class SetlistCreator {
  public static boolean debugMode = false;

  private static final String SETLIST_FM_API_TOKEN_ENV = "setlist_bot.setlist_fm_api_token";
  private static final String SETLIST_FM_DEBUG_ENV = "setlist_bot.debug_mode";

  private final CreationCache creationCache;
  private final SpotifyApi spotifyApi;
  private final PlaylistService playlistService;
  private final SpotifyLogger logger;
  private final Environment environment;
  private final int port;

  private String setlistFmApiToken;

  SetlistCreator(CreationCache creationCache,
      SpotifyApi spotifyApi,
      PlaylistService playlistService,
      SpotifyLogger spotifyLogger,
      Environment environment,
      SpringPortConfig springPortConfig) {
    this.creationCache = creationCache;
    this.spotifyApi = spotifyApi;
    this.playlistService = playlistService;
    this.logger = spotifyLogger;
    this.environment = environment;
    this.port = springPortConfig.getPort();
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
    debugMode = "true".equals(debugModeEnv);

    if (debugMode) {
      logger.warning("Debug mode enabled, playlist counter calculation has been skipped!");
    } else {
      logger.info("Calculating and cleaning up existing playlists... (this might take a while)");
      creationCache.refreshCreatedSetlistsCounterAndRemoveDeadPlaylists();
    }
    logger.info("Booted up! http://localhost:" + port);
  }

  /**
   * Create a setlist playlist from the given setlist.fm ID
   *
   * @param setlistFmId the setlist.fm ID
   * @param options any potential option flags
   * @return a SetlistCreationResponse with the result
   * @throws NotFoundException if either the setlist or any of its songs couldn't be found
   */
  public SetlistCreationResponse convertSetlistToPlaylist(String setlistFmId, SetlistCreationOptions options, WebSocketSession session) throws NotFoundException {
    long start = System.currentTimeMillis();

    // Find the setlist.fm setlist
    sendMessage(session, "Fetching data from setlist.fm...");
    Setlist setlist = SetlistFmApi.getSetlist(setlistFmId, setlistFmApiToken);

    // Assemble the name for the playlist and search for each song on Spotify
    String setlistName = setlist.toString();
    List<TrackSearchResult> spotifySearchResults = findSongsOnSpotify(setlist, options, session);
    int totalSetlistSongsCount = setlist.getSongs().size();
    long searchResultCount = spotifySearchResults.stream()
      .filter(TrackSearchResult::hasResult)
      .count();
    if (spotifySearchResults.isEmpty() || searchResultCount < totalSetlistSongsCount / 2) {
      sendMessage(session, "Operation failed.");
      throw new NotFoundException("No songs found");
    }

    // Now purge all missed songs, so we can add the actual results
    List<TrackSearchResult> spotifySearchResultsFiltered = spotifySearchResults.stream()
      .filter(TrackSearchResult::hasResult)
      .collect(Collectors.toList());

    // Search for existing playlists that match the name and tracks
    // If there is a match, return that instead one instead of creating an entirely new playlist
    sendMessage(session, "Looking for existing playlist...");
    Optional<Playlist> existingSetlistPlaylist = creationCache.searchForExistingSetlistPlaylist(setlistName, spotifySearchResultsFiltered);
    if (existingSetlistPlaylist.isPresent()) {
      Playlist existingPlaylist = existingSetlistPlaylist.get();
      long timeTaken = System.currentTimeMillis() - start;
      SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, options, existingPlaylist.getId(), spotifySearchResults, timeTaken, true);
      logger.info(String.format("Existing setlist requested: %s - %s", existingPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
      return setlistCreationResponse;
    }

    // Create the playlist on Spotify with appropriate name, description, and image
    sendMessage(session, "Creating new playlist...");
    String description = SetlistUtils.assembleDescription(setlist);
    Playlist targetPlaylist = playlistService.createPlaylist(setlistName, description, true);
    sendMessage(session, "Adding tracks to playlist...");
    List<Track> tracksToAdd = spotifySearchResultsFiltered.stream().map(TrackSearchResult::getSearchResult).collect(Collectors.toList());
    playlistService.addTracksToPlaylist(targetPlaylist, tracksToAdd);

    // Attach image
    if (options.isAttachImage() && !debugMode) {
      sendMessage(session, "Attaching image...");
      tracksToAdd.stream()
        .filter(t -> SpotifyUtils.getFirstArtistName(t).equals(setlist.getArtistName()))
        .findFirst()
        .ifPresent(t -> {
          ArtistSimplified artist = t.getArtists()[0];
          Artist fullArtist = SpotifyCall.execute(spotifyApi.getArtist(artist.getId()));
          attachArtistImage(fullArtist, targetPlaylist);
        });
    }

    // Log and return the result
    sendMessage(session, "Almost there...");
    long timeTaken = System.currentTimeMillis() - start;
    SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, options, targetPlaylist.getId(), spotifySearchResults, timeTaken, false);
    logger.info(String.format("New setlist created: %s - %s", targetPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
    if (debugMode) {
      SpotifyCall.execute(spotifyApi.unfollowPlaylist(targetPlaylist.getId()));
      logger.warning("Debug playlist deleted!");
    } else {
      creationCache.addSetlistToCache(setlistName, targetPlaylist.getId());
    }
    return setlistCreationResponse;
  }

  private List<TrackSearchResult> findSongsOnSpotify(Setlist setlist, SetlistCreationOptions options, WebSocketSession session) {
    // This was originally done using SpotifyOptimizedExecutorService,
    // but ironically enough, it is significantly faster in a simple for-loop,
    // as it's less likely to cause 429 Too Many Requests errors this way.
    List<Setlist.Song> songs = setlist.getSongs();
    List<TrackSearchResult> trackSearchResults = new ArrayList<>();
    for (int i = 0; i < songs.size(); i++) {
      Setlist.Song song = songs.get(i);
      sendMessage(session, String.format("Searching for the tracks on Spotify... (%d of %d)", i + 1, songs.size()));
      boolean notSkipped = !song.isTape() && !song.isMedleyPart()
        || song.isTape() && (song.isCover() ? options.isIncludeTapesForeign() : options.isIncludeTapesMain())
        || song.isMedleyPart() && options.isIncludeMedleys();
      if (notSkipped) {
        TrackSearchResult trackSearchResult = searchTrack(song, options.isIncludeCoverOriginals(), options.isStrictSearch());
        trackSearchResults.add(trackSearchResult);
      } else {
        trackSearchResults.add(TrackSearchResult.skipped(song));
      }
    }
    return trackSearchResults;
  }

  // visible for testing
  TrackSearchResult searchTrack(Setlist.Song song, boolean includeCoverOriginals, boolean strictSearch) {
    String queryArtistName = song.isTape() ? song.getOriginalArtistName() : song.getArtistName();
    String songName = song.getSongName();
    String searchQuery = buildSearchQuery(songName, queryArtistName, strictSearch);

    List<Track> searchResults = Arrays.asList(SpotifyCall.execute(spotifyApi.searchTracks(searchQuery)).getItems());

    // One-time retry for songs starting with "The ", because sometimes that word is missing on the Spotify version of the track
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

  private TrackSearchResult findBestSearchResult(Setlist.Song song, boolean strictSearch, String songName, List<Track> searchResults, String queryArtistName, boolean allowAlternateVersions) {
    List<Track> matchingSongs = searchResults.stream()
      .filter(track -> SetlistUtils.isStartContained(queryArtistName, SpotifyUtils.getFirstArtistName(track)))
      .filter(track -> allowAlternateVersions || !SetlistUtils.containsAlternateVersionWord(songName, track.getName()))
      .filter(track -> SetlistUtils.containsIgnoreCaseNormalized(track.getName(), songName))
      .sorted(Comparator.comparing(t -> t.getAlbum().getReleaseDate()))
      .collect(Collectors.toList());

    // Where possible, try to find songs from official artist's albums first
    for (Track track : matchingSongs) {
      String firstArtistName = SpotifyUtils.getFirstArtistName(track.getAlbum());
      if (SetlistUtils.isInAlbum(track)) {
        if (queryArtistName.equalsIgnoreCase(firstArtistName) && songName.equalsIgnoreCase(track.getName())) {
          return TrackSearchResult.exactMatch(song, track);
        }
      }
    }

    // If that failed, retry it but this time with a lesser strict match
    for (Track track : matchingSongs) {
      String firstArtistName = SpotifyUtils.getFirstArtistName(track.getAlbum());
      if (SetlistUtils.isInAlbum(track)) {
        if (SetlistUtils.isStartContained(queryArtistName, firstArtistName) && SetlistUtils.isStartContained(track.getName(), songName)) {
          return TrackSearchResult.closeMatch(song, track);
        }
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

  /**
   * Attaches the first image of the given artist of the setlist as the playlist image.
   * Will fail if the artist has no images.
   * Note: Due to a weird quirk with Spotify's API, it will sometimes fail with Not Found
   *       despite the artist clearly having images. What's weirder is that upon multiple
   *       retries it will magically start working again. Therefore, this method will
   *       automatically retry the attachment process up to 10 times, which so far has
   *       always worked.
   *
   * @param artist the artist to get the image from
   * @param targetPlaylist the playlist to attach the image to
   */
  private void attachArtistImage(Artist artist, Playlist targetPlaylist) {
    String image = SpotifyUtils.findLargestImage(artist.getImages());
    if (image != null) {
      for (int i = 1; i <= 10; i++) {
        try {
          playlistService.attachImageToPlaylist(targetPlaylist, image);
          return;
        } catch (Exception e) {
          logger.debug("Retrying attaching artist image for " + artist.getName() + " (attempt: " + i + ")");
        }
      }
      logger.error("Failed to attach artist image -- " + artist.getName());
    }
  }
  
  private void sendMessage(WebSocketSession session, String text) {
    try {
      session.sendMessage(new TextMessage(text));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
