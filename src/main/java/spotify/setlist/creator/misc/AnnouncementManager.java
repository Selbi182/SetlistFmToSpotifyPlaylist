package spotify.setlist.creator.misc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class AnnouncementManager {
  private static final String ANNOUNCEMENT_FILE_NAME = "announcement.html";

  private final String announcementMessage;
  AnnouncementManager() {
    String parsedAnnouncementText = "";
    File file = Paths.get(ANNOUNCEMENT_FILE_NAME).toFile();
    try {
      parsedAnnouncementText = Files.readString(file.toPath());
      System.out.println("Successfully read and parsed announcement.html file!");
    } catch (IOException e) {
      System.out.println("No announcement.html file found!");
    }
    this.announcementMessage = parsedAnnouncementText;
  }

  public String getAnnouncementMessage() {
    return announcementMessage;
  }

  public boolean hasAnnouncement() {
    return !announcementMessage.isEmpty();
  }
}
