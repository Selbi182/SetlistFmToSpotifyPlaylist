package spotify.setlist.creator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class CounterManager {
  private static final String COUNTER_FILE_NAME = "counter.txt";

  private final AtomicInteger cachedCount;
  private final DecimalFormat decimalFormat;

  CounterManager() throws IOException {
    int readCount = Integer.parseInt(Files.readString(Paths.get(COUNTER_FILE_NAME)).trim());
    this.cachedCount = new AtomicInteger(readCount);

    this.decimalFormat = new DecimalFormat("#,###", new DecimalFormatSymbols(Locale.US));
  }

  public int getSetlistCounter() {
    return cachedCount.get();
  }

  public String getSetlistCounterFormatted() {
    return decimalFormat.format(getSetlistCounter());
  }

  public synchronized void incrementSetlistCounter() {
    try {
      int newSetlistCounter = cachedCount.incrementAndGet();
      Files.writeString(Paths.get(COUNTER_FILE_NAME), String.valueOf(newSetlistCounter));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
