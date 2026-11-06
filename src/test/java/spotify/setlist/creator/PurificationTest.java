package spotify.setlist.creator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import spotify.setlist.util.SetlistUtils;

class PurificationTest {

  private void testPurified(String rawSongTitle, String expectedPurifiedSongTitle) {
    String purified = SetlistUtils.purifyString(rawSongTitle);
    assertEquals(expectedPurifiedSongTitle, purified);
  }

  ///////////////////////////////

  @Test
  void purifySongTitleTest_German() {
    testPurified("Straße", "strasse");
    testPurified("Über sieben Brücken musst du gehn'", "ueber sieben bruecken musst du gehn");
    testPurified("Mädel mit öl", "maedel mit oel");
    testPurified("Größte", "groesste");
    testPurified("Fußgänger", "fussgaenger");
  }

  @Test
  void purifySongTitleTest_Italian() {
    testPurified("canzóni per l estate", "canzoni per l estate");
    testPurified("perché ti amo", "perche ti amo");
    testPurified("più che puoi", "piu che puoi");
    testPurified("là dove si canta", "la dove si canta");
    testPurified("andrà tutto bene", "andra tutto bene");
  }

  @Test
  void purifySongTitleTest_French() {
    testPurified("deja vu", "deja vu"); // control (no diacritics)
    testPurified("déjà vu", "deja vu");
    testPurified("noël ensemble", "noel ensemble");
    testPurified("frère jacques", "frere jacques");
  }

  @Test
  void purifySongTitleTest_SpanishPortuguese() {
    testPurified("corazón espinado", "corazon espinado");
    testPurified("canción del mariachi", "cancion del mariachi");
    testPurified("niñez", "ninez");
    testPurified("amanhã", "amanha");
    testPurified("coração vagabundo", "coracao vagabundo");
    testPurified("senõrita", "senorita");
  }

  @Test
  void purifySongTitleTest_NordicEasternEurope() {
    testPurified("björk", "bjoerk");
    testPurified("ångström", "angstroem");
    testPurified("żubrówka", "zubrowka");
    testPurified("čajovna", "cajovna");
    testPurified("şarkı", "sarki");
  }

  @Test
  void purifySongTitleTest_TurkishCasingEdge() {
    testPurified("İstanbul", "istanbul");
    testPurified("istanbul", "istanbul");
  }

  @Test
  void purifySongTitleTest_CombiningMarks() {
    testPurified("café", "cafe");
    testPurified("café del mar", "cafe del mar");
  }

  @Test
  void purifySongTitleTest_WhitespaceAndTrim() {
    testPurified("  leading  and   multiple   spaces  ", "leading and multiple spaces");
    testPurified("tabs\tand\nnewlines", "tabs and newlines");
    testPurified("  ", "");
    testPurified("\t\n", "");
  }

  @Test
  void purifySongTitleTest_Mixed() {
    testPurified("Resumé / resumé", "resume resume");
    testPurified("Déjà vu – strasse – più", "deja vu strasse piu");
    testPurified("Mädchen corazón déjà", "maedchen corazon deja");
    testPurified("Über niño śnić", "ueber nino snic");
  }

  @Test
  void purifySongTitleTest_LigaturesAndSpecialLetters() {
    testPurified("cœur de pirate", "coeur de pirate");
    testPurified("æther", "aether");
    testPurified("smørrebrød", "smorrebrod");
    testPurified("łódź", "lodz");
    testPurified("đorđe", "dorde");
  }
}
