package spotify.setlist.data;

import se.michaelthelin.spotify.model_objects.specification.Track;

@SuppressWarnings("unused")
public class TrackSearchResult {
  private final Setlist.Song song;
  private final Track searchResult;
  private final ResultType resultType;

  TrackSearchResult(Setlist.Song song, Track searchResult, ResultType resultType) {
    this.song = song;
    this.searchResult = searchResult;
    this.resultType = resultType;
  }

  public static TrackSearchResult of(Setlist.Song song, Track searchResult, ResultType resultType) {
    return new TrackSearchResult(song, searchResult, resultType);
  }

  public static TrackSearchResult exactMatch(Setlist.Song song, Track track) {
    return of(song, track, ResultType.MATCH);
  }

  public static TrackSearchResult closeMatch(Setlist.Song song, Track track) {
    return of(song, track, ResultType.CLOSE_MATCH);
  }

  public static TrackSearchResult coverOriginal(Setlist.Song song, Track coverOriginal) {
    return of(song, coverOriginal, ResultType.COVER_ORIGINAL);
  }

  public static TrackSearchResult skipped(Setlist.Song song) {
    return of(song, null, ResultType.SKIPPED);
  }

  public static TrackSearchResult notFound(Setlist.Song song) {
    return of(song, null, ResultType.NOT_FOUND);
  }

  public Setlist.Song getSong() {
    return song;
  }

  public Track getSearchResult() {
    return searchResult;
  }

  public ResultType getResultType() {
    return resultType;
  }

  public boolean hasResult() {
    return searchResult != null;
  }

  public enum ResultType {
    MATCH,
    CLOSE_MATCH,
    COVER_ORIGINAL,
    SKIPPED,
    NOT_FOUND
  }
}
