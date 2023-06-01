package spotify.setlist;

import java.io.IOException;
import java.net.MalformedURLException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  private static final String DEFAULT_OPTIONS = "tapes,covers,medleys";

  private final SetlistCreator setlistCreator;

  SetlistController(SetlistCreator setlistCreator) {
    this.setlistCreator = setlistCreator;
  }

  @RequestMapping("/")
  public ModelAndView creationModel() {
    return new ModelAndView("create.html");
  }

  @CrossOrigin
  @RequestMapping("/create")
  public ResponseEntity<SetlistCreationResponse> createSpotifySetlistFromSetlistFmByParam(
      @RequestParam("url") String url,
      @RequestParam(value = "options", defaultValue = DEFAULT_OPTIONS) String options)
    throws MalformedURLException, NotFoundException, IndexOutOfBoundsException {
    String setlistFmId = SetlistUtils.getIdFromSetlistFmUrl(url);
    SetlistCreationResponse setlistCreationResponse = setlistCreator.createSetlist(setlistFmId, options);
    return ResponseEntity.ok(setlistCreationResponse);
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
