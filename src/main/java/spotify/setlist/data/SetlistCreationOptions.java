package spotify.setlist.data;

public record SetlistCreationOptions(boolean includeTapesMain, boolean includeTapesForeign, boolean includeCoverOriginals, boolean includeMedleys, boolean attachImage) {
}
