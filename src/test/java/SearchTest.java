import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import se.michaelthelin.spotify.model_objects.specification.Track;
import spotify.SetlistFmToSpotifyPlaylist;
import spotify.api.SpotifyApiManager;
import spotify.api.events.SpotifyApiException;
import spotify.config.SpotifyApiConfig;
import spotify.services.PlaylistService;
import spotify.services.UserService;
import spotify.setlist.creator.SetlistCreator;
import spotify.setlist.data.Setlist;
import spotify.setlist.util.SetlistUtils;
import spotify.spring.SpringPortConfig;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyOptimizedExecutorService;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
  SpotifyApiConfig.class,
  SpotifyApiManager.class,
  SpringPortConfig.class,
  SpotifyLogger.class,
  SpotifyOptimizedExecutorService.class,
  PlaylistService.class,
  UserService.class,
  SetlistCreator.class,
  SetlistFmToSpotifyPlaylist.SetlistFmBotSpotifySettings.class
})
@EnableConfigurationProperties
public class SearchTest {

  @Autowired
  private SetlistCreator setlistCreator;

  @Autowired
  private SpotifyApiManager spotifyApiManager;

  private static boolean initialized = false;

  @Before
  public void initializeTests() {
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
    Setlist.Song song = new Setlist.Song(title, artist, artist, true, true, true);
    return setlistCreator.searchTrack(song, true, true);
  }

  private void testPositive(String artist, String title) {
    Track track = searchTrack(artist, title);
    assertEquals(artist, track.getArtists()[0].getName());
    assertTrue(SetlistUtils.isStartContained(track.getName(), title));
  }

  private void testNegative(String artist, String title) {
    Track track = searchTrack(artist, title);
    assertNull(track);
  }

  ///////////////////////////////

  @Test
  public void searchTracksPositiveTest() {
    testPositive("Metallica", "Nothing Else Matters");
    testPositive("Finsterforst", "Ecce Homo");
    testPositive("HotWax", "Rip It Out");
    testPositive("D-A-D", "Evil Twin");
  }

  @Test
  public void searchTracksNegativeTest() {
    testNegative("Metallica", "some metallica song that doesn't exist lol");
    testNegative("DragonForce", "E.P.M."); // real song, but not available on Spotify
  }

}

