package spotify.setlist.creator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
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

  private final AtomicInteger createdSetlists;

  SetlistCreator(SpotifyApi spotifyApi, PlaylistService playlistService, SpotifyOptimizedExecutorService spotifyOptimizedExecutorService, SpotifyLogger spotifyLogger) {
    this.spotifyApi = spotifyApi;
    this.playlistService = playlistService;
    this.executorService = spotifyOptimizedExecutorService;
    this.logger = spotifyLogger;

    this.createdSetlists = new AtomicInteger();

    String setlistFmApiToken = System.getenv(SETLIST_FM_API_TOKEN_ENV);
    if (setlistFmApiToken == null || setlistFmApiToken.isBlank()) {
      throw new IllegalStateException(SETLIST_FM_API_TOKEN_ENV + " environment variable is missing!");
    }
    this.setlistFmApiToken = setlistFmApiToken;
  }

  @PostConstruct
  void calculateExistingSetlists() {
    refreshCreatedSetlistsCounterAndRemoveDeadPlaylists();
  }

  @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
  public void refreshCreatedSetlistsCounterAndRemoveDeadPlaylists() {
    List<PlaylistSimplified> allUserPlaylists = SpotifyCall.executePaging(spotifyApi.getListOfCurrentUsersPlaylists());

    List<Callable<String>> toRemove = allUserPlaylists.stream()
      .filter(pl -> (pl.getTracks().getTotal() == 0))
      .map(pl -> (Callable<String>) () -> SpotifyCall.execute(spotifyApi.unfollowPlaylist(pl.getId())))
      .collect(Collectors.toList());
    if (!toRemove.isEmpty()) {
      executorService.executeAndWait(toRemove);
    }

    int total = allUserPlaylists.size() - toRemove.size();
    createdSetlists.set(total);
  }

  public int getSetlistCounter() {
    return createdSetlists.get();
  }

  /**
   * Create a setlist playlist from the given setlist.fm ID
   *
   *
   * @param setlistFmId the setlist.fm ID
   * @param options any potential option flags (separated by comma)
   * @return a SetlistCreationResponse with the result
   * @throws NotFoundException if either the setlist or any of its songs couldn't be found
   */
  public SetlistCreationResponse createSetlist(String setlistFmId, String options) throws NotFoundException {
    // Find the setlist.fm setlist
    Setlist setlist = SetlistFmApi.getSetlist(setlistFmId, setlistFmApiToken);

    // Assemble the name for the playlist and search for each song on Spotify
    String setlistName = SetlistUtils.assemblePlaylistName(setlist);
    List<Track> songsFromSpotify = findSongsOnSpotify(setlist, options);
    int totalSetlistSongsCount = setlist.getSongs().size();
    int searchResultCount = songsFromSpotify.size();
    if (songsFromSpotify.isEmpty() || searchResultCount < totalSetlistSongsCount / 2) {
      throw new NotFoundException("No songs found");
    }

    // Calculate missed songs (should be 0 ideally)
    int missedSongs = totalSetlistSongsCount - searchResultCount;

    // Search for existing playlists that match the name and tracks
    // If there is a match, return that instead one instead of creating an entirely new playlist
    Optional<Playlist> existingSetlistPlaylist = searchForExistingSetlistPlaylist(setlistName, songsFromSpotify);
    if (existingSetlistPlaylist.isPresent()) {
      Playlist existingPlaylist = existingSetlistPlaylist.get();
      SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, existingPlaylist.getId(), missedSongs);
      logger.info(String.format("Existing setlist requested: %s - %s", existingPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
      return setlistCreationResponse;
    }

    // Create the playlist on Spotify with appropriate name, description, and image
    String description = SetlistUtils.assembleDescription(setlist);
    Playlist targetPlaylist = playlistService.createPlaylist(setlistName, description, true);
    attachArtistImage(setlist, targetPlaylist);
    playlistService.addTracksToPlaylist(targetPlaylist, songsFromSpotify);

    // Log and return the result
    SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, targetPlaylist.getId(), missedSongs);
    logger.info(String.format("New setlist created: %s - %s", targetPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
    createdSetlists.incrementAndGet();
    return setlistCreationResponse;
  }

  private List<Track> findSongsOnSpotify(Setlist setlist, String options) {
    List<String> splitOptions = Arrays.asList(options.split(","));
    boolean includeTapes = splitOptions.contains("tapes");
    boolean includeCoverOriginals = splitOptions.contains("covers");
    boolean includeMedleys = splitOptions.contains("medleys");
    boolean strictSearch = splitOptions.contains("strict-search");

    List<Callable<Track>> callables = new ArrayList<>();
    for (Setlist.Song song : setlist.getSongs()) {
      callables.add(() -> {
        if (!song.isTape() && !song.isMedleyPart()
          || song.isTape() && includeTapes
          || song.isMedleyPart() && includeMedleys) {
          return searchTrack(song, includeCoverOriginals, strictSearch);
        }
        return null;
      });
    }
    return executorService.executeAndWait(callables).stream()
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  public Track searchTrack(Setlist.Song song, boolean includeCoverOriginals, boolean strictSearch) {
    String queryArtistName = song.isTape() ? song.getOriginalArtistName() : song.getArtistName();
    String searchQuery = buildSearchQuery(song.getSongName(), queryArtistName, strictSearch);

    List<Track> searchResults = Arrays.asList(SpotifyCall.execute(spotifyApi.searchTracks(searchQuery)).getItems());

    if (!searchResults.isEmpty()) {
      return searchResults.stream()
        .filter(track -> queryArtistName.equals(SpotifyUtils.getFirstArtistName(track)))
        .filter(track -> !SetlistUtils.isShallowLive(track.getName()))
        .filter(track -> SetlistUtils.isStartContained(track.getName(), song.getSongName()))
        .findFirst()
        .orElse(null);
    } else if (song.isCover() && includeCoverOriginals) {
      String fallbackCoverSearchQuery = buildSearchQuery(song.getSongName(), song.getOriginalArtistName(), strictSearch);
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
      return String.format("artist:%s track:%s", artistName, songName);
    }
    return artistName + " " + songName;
  }

  private Optional<Playlist> searchForExistingSetlistPlaylist(String setlistName, List<Track> setlistTracks) {
    List<PlaylistSimplified> currentUsersPlaylists = playlistService.getCurrentUsersPlaylists();
    for (PlaylistSimplified playlistSimplified : currentUsersPlaylists) {
      if (playlistSimplified.getTracks().getTotal().equals(setlistTracks.size()) && Objects.equals(setlistName, playlistSimplified.getName())) {
        Playlist playlist = playlistService.getPlaylist(playlistSimplified.getId());
        List<Track> allPlaylistTracks = playlistService.getAllPlaylistSongs(playlist);
        if (allPlaylistTracks.equals(setlistTracks)) {
          return Optional.of(playlist);
        }
      }
    }
    return Optional.empty();
  }

  private void attachArtistImage(Setlist setlist, Playlist targetPlaylist) {
    Artist[] artists = SpotifyCall.execute(spotifyApi.searchArtists(setlist.getArtistName())).getItems();
    if (artists.length > 0) {
      Artist artist = artists[0];
      String image = SpotifyUtils.findLargestImage(artist.getImages());
      playlistService.attachImageToPlaylist(targetPlaylist, image);
    }
  }
}
