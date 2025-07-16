package spotify.setlist.data;

public class SetlistCreationOptions {
  private final boolean includeTapesMain;
  private final boolean includeTapesForeign;
  private final boolean includeCoverOriginals;
  private final boolean includeMedleys;
  private final boolean attachImage;

  public SetlistCreationOptions(boolean includeTapesMain, boolean includeTapesForeign, boolean includeCoverOriginals, boolean includeMedleys, boolean attachImage) {
    this.includeTapesMain = includeTapesMain;
    this.includeTapesForeign = includeTapesForeign;
    this.includeCoverOriginals = includeCoverOriginals;
    this.includeMedleys = includeMedleys;
    this.attachImage = attachImage;
  }

  public boolean isIncludeTapesMain() {
    return includeTapesMain;
  }

  public boolean isIncludeTapesForeign() {
    return includeTapesForeign;
  }

  public boolean isIncludeCoverOriginals() {
    return includeCoverOriginals;
  }

  public boolean isIncludeMedleys() {
    return includeMedleys;
  }

  public boolean isAttachImage() {
    return attachImage;
  }
}
