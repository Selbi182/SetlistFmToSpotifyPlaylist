package spotify.setlist.creator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import se.michaelthelin.spotify.model_objects.specification.Track;
import spotify.SetlistFmToSpotifyPlaylist;
import spotify.api.SpotifyApiManager;
import spotify.api.events.SpotifyApiException;
import spotify.config.SpotifyApiConfig;
import spotify.services.PlaylistService;
import spotify.services.UserService;
import spotify.setlist.creator.misc.CounterManager;
import spotify.setlist.creator.misc.CreationCache;
import spotify.setlist.data.Setlist;
import spotify.setlist.data.TrackSearchResult;
import spotify.setlist.util.SetlistUtils;
import spotify.spring.SpringPortConfig;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyOptimizedExecutorService;

@SpringBootTest(classes = {
  SpotifyApiConfig.class,
  SpotifyApiManager.class,
  SpringPortConfig.class,
  SpotifyLogger.class,
  SpotifyOptimizedExecutorService.class,
  PlaylistService.class,
  UserService.class,
  SetlistCreator.class,
  CreationCache.class,
  CounterManager.class,
  SetlistFmToSpotifyPlaylist.SetlistFmBotSpotifySettings.class
})
@EnableConfigurationProperties
class SearchTest {

  @Autowired
  private SetlistCreator setlistCreator;

  @Autowired
  private SpotifyApiManager spotifyApiManager;

  private static boolean initialized = false;

  @BeforeEach
  void initializeTests() {
    if (!initialized) {
      login();
      initialized = true;
    }
  }

  private void login() {
    try {
      spotifyApiManager.initialLogin();
    } catch (SpotifyApiException e) {
      fail("Couldn't log in to Spotify Web API!");
    }
  }

  ///////////////////////////////

  private Track searchTrack(String artist, String title) {
    Setlist.Song song = new Setlist.Song(1, title, artist, artist, "", false, false, false);
    TrackSearchResult trackSearchResult = setlistCreator.searchTrack(song, true);
    return trackSearchResult.getSearchResult();
  }

  private void testPositive(String artist, String title) {
    Track track = searchTrack(artist, title);
    assertNotNull(track, "Track must not be null");
    assertEquals(artist, track.getArtists()[0].getName());
    assertTrue(SetlistUtils.containsIgnoreCaseNormalized(track.getName(), title) || SetlistUtils.containsIgnoreCaseNormalized(title, track.getName()));
  }

  private void testNegative(String artist, String title) {
    Track track = searchTrack(artist, title);
    assertNull(track);
  }

  ///////////////////////////////

  @Test
  void searchTracksPositiveTest() {
    testPositive("Metallica", "Nothing Else Matters");
    testPositive("Finsterforst", "Ecce Homo");
    testPositive("HotWax", "Rip It Out");
    testPositive("D-A-D", "Evil Twin");
    testPositive("Enter Shikari", "Sorry, You're Not a Winner");
    testPositive("Dream Theater", "Act I: Scene Two: II. Strange Déjà Vu");
    testPositive("Der Weg einer Freiheit", "Vergängnis");
    testPositive("Ghost Bath", "The Silver Flower, Part 2");
    testPositive("Pendulum", "The Island Part 1");
    testPositive("Pendulum", "The Island Part II");
  }

  @Test
  void searchTracksNegativeTest() {
    testNegative("Metallica", "some metallica song that doesn't exist lol");
    testNegative("DragonForce", "E.P.M."); // real song, but not available on Spotify
  }

}
