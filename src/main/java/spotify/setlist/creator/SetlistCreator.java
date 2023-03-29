package spotify.setlist.creator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;
import spotify.api.SpotifyCall;
import spotify.services.PlaylistService;
import spotify.services.TrackService;
import spotify.setlist.data.Setlist;
import spotify.setlist.data.SetlistCreationResponse;
import spotify.setlist.setlistfm.SetlistFmApi;
import spotify.setlist.util.SetlistUtils;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyOptimizedExecutorService;
import spotify.util.SpotifyUtils;

@Component
public class SetlistCreator {
  private static final String SETLIST_FM_API_TOKEN_ENV = "setlist_fm_api_token";
  private final String setlistFmApiToken;

  private final SpotifyApi spotifyApi;
  private final TrackService trackService;
  private final PlaylistService playlistService;
  private final SpotifyOptimizedExecutorService executorService;
  private final SpotifyLogger logger;

  SetlistCreator(SpotifyApi spotifyApi, PlaylistService playlistService, TrackService trackService, SpotifyOptimizedExecutorService spotifyOptimizedExecutorService, SpotifyLogger spotifyLogger) {
    this.spotifyApi = spotifyApi;
    this.playlistService = playlistService;
    this.trackService = trackService;
    this.executorService = spotifyOptimizedExecutorService;
    this.logger = spotifyLogger;

    String setlistFmApiToken = System.getenv(SETLIST_FM_API_TOKEN_ENV);
    if (setlistFmApiToken == null || setlistFmApiToken.isBlank()) {
      throw new IllegalStateException(SETLIST_FM_API_TOKEN_ENV + " environment variable is missing!");
    }
    this.setlistFmApiToken = setlistFmApiToken;
  }

  /**
   * Create a setlist playlist from the given setlist.fm ID
   *
   * @param setlistFmId the setlist.fm ID
   * @return a SetlistCreationResponse with the result
   * @throws NotFoundException if either the setlist or any of its songs couldn't be found
   */
  public SetlistCreationResponse createSetlist(String setlistFmId) throws NotFoundException {
    // Find the setlist.fm setlist
    Setlist setlist = SetlistFmApi.getSetlist(setlistFmId, setlistFmApiToken);

    // Assemble the name for the playlist and search for each song on Spotify
    String setlistName = SetlistUtils.assemblePlaylistName(setlist);
    List<Track> songsFromSpotify = findSongsOnSpotify(setlist);

    // Search for existing playlists that match the name and tracks
    // If there is a match, return that instead one instead of creating an entirely new playlist
    Optional<Playlist> existingSetlistPlaylist = searchForExistingSetlistPlaylist(setlistName, songsFromSpotify);
    if (existingSetlistPlaylist.isPresent()) {
      Playlist existingPlaylist = existingSetlistPlaylist.get();
      SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, existingPlaylist.getId());
      logger.info(String.format("Existing setlist requested: %s - %s", existingPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
      return setlistCreationResponse;
    }

    // Create the playlist on Spotify with appropriate name, description, and image
    String description = SetlistUtils.assembleDescription(setlist);
    Playlist targetPlaylist = playlistService.createPlaylist(setlistName, description, true);
    attachArtistImage(setlist, targetPlaylist);
    playlistService.addTracksToPlaylist(targetPlaylist, songsFromSpotify);

    // Log and return the result
    SetlistCreationResponse setlistCreationResponse = new SetlistCreationResponse(setlist, targetPlaylist.getId());
    logger.info(String.format("New setlist created: %s - %s", targetPlaylist.getName(), setlistCreationResponse.getPlaylistUrl()));
    return setlistCreationResponse;
  }

  private List<Track> findSongsOnSpotify(Setlist setlist) throws NotFoundException {
    List<Callable<Track>> callables = new ArrayList<>();
    for (String songName : setlist.getSongNames()) {
      callables.add(() -> trackService.searchTrack(songName, setlist.getArtistName()));
    }
    List<Track> tracksSearchResults = executorService.executeAndWait(callables);
    if (tracksSearchResults.contains(null)) {
      throw new NotFoundException("Couldn't find all songs");
    }
    return tracksSearchResults;
  }

  private Optional<Playlist> searchForExistingSetlistPlaylist(String setlistName, List<Track> setlistTracks) {
    List<PlaylistSimplified> currentUsersPlaylists = playlistService.getCurrentUsersPlaylists();
    for (PlaylistSimplified playlistSimplified : currentUsersPlaylists) {
      if (playlistSimplified.getTracks().getTotal().equals(setlistTracks.size()) && Objects.equals(setlistName, playlistSimplified.getName())) {
        Playlist playlist = playlistService.getPlaylist(playlistSimplified.getId());
        List<Track> allPlaylistTracks = playlistService.getAllPlaylistTracks(playlist);
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
