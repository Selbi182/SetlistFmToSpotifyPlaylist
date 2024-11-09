package spotify.setlist;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import spotify.setlist.creator.CounterManager;

@RestController
public class SetlistController {
  private final CounterManager counterManager;

  private final long bootTime;

  SetlistController(CounterManager counterManager) {
    this.counterManager = counterManager;
    this.bootTime = System.currentTimeMillis();
  }

  @RequestMapping("/")
  public ModelAndView converter(Model model) {
    model.addAttribute("bootTime", bootTime);
    model.addAttribute("playlistCount", counterManager.getSetlistCounterFormatted());
    return new ModelAndView("converter.html");
  }

  @CrossOrigin
  @RequestMapping("/counter")
  public ResponseEntity<String> createdSetlistsCounter() {
    return ResponseEntity.ok(counterManager.getSetlistCounterFormatted());
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
