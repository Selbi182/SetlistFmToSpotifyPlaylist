package spotify.setlist;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.Semaphore;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import spotify.setlist.creator.SetlistCreator;
import spotify.setlist.data.SetlistCreationResponse;
import spotify.setlist.util.SetlistUtils;

@RestController
public class SetlistController {
  private final Semaphore semaphore;

  private final SetlistCreator setlistCreator;
  private final long bootTime;

  SetlistController(SetlistCreator setlistCreator) {
    final int MAX_CONCURRENT_REQUESTS = 5;
    this.semaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);
    this.setlistCreator = setlistCreator;
    this.bootTime = System.currentTimeMillis();
  }

  @RequestMapping("/")
  public ModelAndView converter(Model model) {
    model.addAttribute("bootTime", bootTime);
    model.addAttribute("playlistCount", setlistCreator.getSetlistCounterFormatted());
    return new ModelAndView("converter.html");
  }

  @CrossOrigin
  @RequestMapping("/create")
  public ResponseEntity<SetlistCreationResponse> createSpotifySetlistFromSetlistFmByParam(@RequestParam("url") String url, @RequestParam(value = "options") String options)
    throws MalformedURLException, NotFoundException, IndexOutOfBoundsException {
    try {
      semaphore.acquire();
      String setlistFmId = SetlistUtils.getIdFromSetlistFmUrl(url);
      SetlistCreationResponse setlistCreationResponse = setlistCreator.createSetlist(setlistFmId, options);
      return ResponseEntity.ok(setlistCreationResponse);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      e.printStackTrace();
      return ResponseEntity.internalServerError().body(null);
    } finally {
      semaphore.release();
    }
  }

  @CrossOrigin
  @RequestMapping("/counter")
  public ResponseEntity<Integer> createdSetlistsCounter() {
    return ResponseEntity.ok(setlistCreator.getSetlistCounter());
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<String> handleNotFoundException(NotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
  }

  @ExceptionHandler(IOException.class)
  public ResponseEntity<String> handleIOException(IOException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleGenericException(Exception e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
  }
}
