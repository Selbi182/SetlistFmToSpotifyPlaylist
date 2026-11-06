package spotify.setlist.creator.misc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import spotify.util.SpotifyLogger;

@Component
public class AnnouncementManager {
  private static final String ANNOUNCEMENT_FILE_NAME = "announcement.html";

  private static final String SETLIST_FM_SHOW_ANNOUNCEMENT = "setlist_bot.show_announcement";

  private final String announcementMessage;
  AnnouncementManager(Environment environment, SpotifyLogger log) {
    String showAnnouncementEnv = environment.getProperty(SETLIST_FM_SHOW_ANNOUNCEMENT);
    if ("true".equals(showAnnouncementEnv)) {
      String parsedAnnouncementText = "";
      File file = Paths.get(ANNOUNCEMENT_FILE_NAME).toFile();
      try {
        parsedAnnouncementText = Files.readString(file.toPath());
        log.info("Announcement ENABLED!");
      } catch (IOException e) {
        log.info("Announcement DISABLED! (no announcement.html file found)");
      }
      this.announcementMessage = parsedAnnouncementText;
    } else {
      this.announcementMessage = "";
      log.info("Announcement DISABLED! (setlist_bot.show_announcement is false)");
    }
  }

  public String getAnnouncementMessage() {
    return announcementMessage;
  }

  public boolean hasAnnouncement() {
    return !announcementMessage.isEmpty();
  }
}
