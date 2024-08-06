package spotify.setlist.data;

public class SetlistCreationOptions {
  private final boolean includeTapesMain;
  private final boolean includeTapesForeign;
  private final boolean includeCoverOriginals;
  private final boolean includeMedleys;
  private final boolean attachImage;
  private final boolean strictSearch;

  public SetlistCreationOptions(boolean includeTapesMain, boolean includeTapesForeign, boolean includeCoverOriginals, boolean includeMedleys, boolean attachImage, boolean strictSearch) {
    this.includeTapesMain = includeTapesMain;
    this.includeTapesForeign = includeTapesForeign;
    this.includeCoverOriginals = includeCoverOriginals;
    this.includeMedleys = includeMedleys;
    this.attachImage = attachImage;
    this.strictSearch = strictSearch;
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

  public boolean isStrictSearch() {
    return strictSearch;
  }
}
