const inputField = document.getElementById("input");
const submitButton = document.getElementById("submit");
const spinner = document.getElementById("spinner");

// Entry Point
window.addEventListener('load', () => initPage());

/////////////////////////////
// Main Web Request Handling

function createSpotifyPlaylistFromSetlistFmSetlist(url) {
  if (isValidSetlistUrl(url)) {
    let options = [...document.querySelectorAll('#options input:checked')].map(e => e.id).join(",");
    setFormDisabled(true);

    fetch(`/create?url=${url}&options=${options}`)
      .then(response => {
        if (response.status >= 200 && response.status < 300) {
          return response.json();
        }
        throw errorText;
      })
      .then(setlistCreationResponse => {
        displayResults(setlistCreationResponse);
      })
      .catch(ex => {
        alert(ex);
        setFormDisabled(false);
      });
  }
}

function displayResults(setlistCreationResponse) {
  /**
   * @typedef {Object} SetlistCreationResponse - Wrapper for a singular setlist creation response.
   * @property {string} artistName - Name of the artist.
   * @property {string} eventDate - Date of the event.
   * @property {string} city - City and/or venue where the event took place.
   * @property {string} playlistId - ID of the result playlist.
   * @property {string} playlistUrl - URL of the playlist.
   * @property {number} timeTaken - Time taken for the operation.
   * @property {TrackSearchResult[]} searchResults - Array of the search results.
   *
   * @typedef {Object} TrackSearchResult - Wrapper for a single track search result.
   * @property {string} resultType - Type of result (e.g. "MATCH", "NOT_FOUND", etc.).
   * @property {SetlistSong} song - The song from the setlist.fm setlist.
   * @property {SpotifySong} searchResult - The track from the Spotify search result, may be null.
   *
   * @typedef {Object} SetlistSong - Song info from setlist.fm.
   * @property {number} index - Index of the song.
   * @property {string} songName - Name of the song.
   * @property {string} artistName - Name of the artist.
   *
   * @typedef {Object} SpotifySong - Song info from Spotify.
   * @property {number} discNumber - Disc number.
   * @property {number} durationMs - Duration of the song in milliseconds.
   * @property {string} href - URL of the track.
   */

  // Hide progress elements
  inputField.classList.add("hide");
  submitButton.classList.add("hide");
  spinner.classList.remove("show");

  // Create the Spotify playlist embed
  window.onSpotifyIframeApiReady = (IFrameAPI) => {
    // noinspection JSUnresolvedFunction
    IFrameAPI.createController(
      document.getElementById("playlist-embed"),
      {uri: 'spotify:playlist:' + setlistCreationResponse.playlistId},
      () => {}
    );
  };

  // Direct link
  let directLink = document.getElementById("direct-link");
  directLink.href = setlistCreationResponse.playlistUrl;
  directLink.innerHTML = setlistCreationResponse.playlistUrl;

  // Search results
  let searchResults = setlistCreationResponse.searchResults;
  if (searchResults.length > 0) {
    let searchResultsContainer = document.getElementById("search-results");
    searchResultsContainer.title = "Click to toggle detailed search results...";
    searchResultsContainer.onclick = () => {
      searchResultsContainer.classList.toggle("no-collapse");
    };

    // Summary Header
    let foundCount = searchResults.filter(sr => !!sr.searchResult).length;
    let totalCount = searchResults.length;
    let summaryHeader = document.createElement("th");
    let timeTaken = `~${(setlistCreationResponse.timeTaken / 1000).toFixed(1)}s`
    summaryHeader.innerHTML = `Playlist created with ${foundCount} of ${totalCount} songs in ${timeTaken}`;
    summaryHeader.colSpan = 3;
    searchResultsContainer.append(summaryHeader);

    // The actual rows
    for (let searchResult of searchResults) {
      let searchResultRow = document.createElement("tr");
      if (searchResult.resultType.includes("MATCH")) {
        searchResultRow.classList.add("match");
      }

      let searchResultIndex = document.createElement("td");
      searchResultIndex.classList.add("search-result-index");
      searchResultIndex.innerHTML = searchResult.song.index;

      let searchResultName = document.createElement("td");
      searchResultName.classList.add("search-result-name");
      searchResultName.innerHTML = searchResult.searchResult?.name || searchResult.song.songName;

      let searchResultType = document.createElement("td");
      searchResultType.classList.add("search-result-type");
      searchResultType.innerHTML = searchResult.resultType.split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(' ');

      searchResultRow.append(searchResultIndex, searchResultName, searchResultType);
      searchResultsContainer.append(searchResultRow);
    }
  }

  // Finally, show results
  let resultsContainer = document.getElementById("results");
  resultsContainer.classList.add("show");
  refreshConvertedSetlistsCounter();
}

/////////////////////////////
// Initialize Page

function initPage() {
  // Set copyright year
  let copyrightYear = document.getElementById("copyright-year");
  let startYear = copyrightYear.innerHTML;
  let currentYear = new Date().getFullYear().toString();
  copyrightYear.innerHTML = startYear < currentYear
    ? `${startYear} \u2013 ${currentYear}`
    : currentYear;

  // Create Ko-fi button
  try {
    // noinspection JSUnresolvedFunction
    kofiwidget2.init('Support Me On Ko-fi!', '#1DB954', 'T6T8S1H5E');
    // noinspection JSUnresolvedFunction
    let kofiButton = kofiwidget2.getHTML();
    let kofiButtonWrapper = document.getElementById("kofi-button");
    kofiButtonWrapper.innerHTML += kofiButton;
  } catch (ex) {
    console.error("Failed to load Ko-fi button", ex);
  }

  // Show counter
  document.getElementById("counter").classList.add("show");

  // Verify URL when editing the text box
  inputField.oninput = (e) => {
    verifyUrl(e.target.value);
  }

  submitButton.onclick = () => {
    createSpotifyPlaylistFromSetlistFmSetlist(inputField.value);
  }

  // Setup detailed options button
  let detailedOptionsButton = document.getElementById("detailed-options");
  detailedOptionsButton.onclick = () => {
    document.getElementById("options").classList.add("detailed");
    detailedOptionsButton.classList.add("hide");
  }

  // Setup one-click addon functionality via the ?auto URL parameter
  const urlParams = new URLSearchParams(window.location.search);
  const autoSetlistUrl = urlParams.get('auto');
  if (autoSetlistUrl && isValidSetlistUrl(autoSetlistUrl)) {
    inputField.value = autoSetlistUrl;
    verifyUrl(autoSetlistUrl);
    submitButton.click();
  }
}

/////////////////////////////
// Utility Functions & Misc

const validSetlistUrlRegex = /^https?:\/\/(www\.)?setlist\.fm\/setlist\/[\w+\-]+\/\d+\/[\w+\-]+\.html$/i;
function isValidSetlistUrl(url) {
  return validSetlistUrlRegex.test(url);
}

function verifyUrl(url) {
  const formatInfo = document.getElementById("format-info");
  if (isValidSetlistUrl(url)) {
    submitButton.removeAttribute("disabled");
    formatInfo.classList.remove("show");
  } else {
    submitButton.setAttribute("disabled", "");
    formatInfo.classList.add("show");
  }
}

function setFormDisabled(disabled) {
  let options = document.getElementById("options");
  if (disabled) {
    inputField.setAttribute("disabled", "");
    submitButton.setAttribute("disabled", "");
    submitButton.innerHTML = "Creating Playlist...";
    spinner.classList.add("show");
    options.classList.add("hide");
  } else {
    inputField.removeAttribute("disabled");
    submitButton.removeAttribute("disabled");
    submitButton.innerHTML = "Create Playlist";
    spinner.classList.remove("show");
    options.classList.remove("hide");
  }
}

function refreshConvertedSetlistsCounter() {
  let counter = document.getElementById("counter");
  fetch("/counter")
    .then(result => result.text())
    .then(text => counter.innerHTML = numberWithCommas(text))
    .catch(ex => console.error(ex));
}

function numberWithCommas(number) {
  return number.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

const errorText =
  `ERROR: Failed to create playlist!

  Possible reasons:
  \u2022 Couldn't find the setlist on setlist.fm
  \u2022 More than half of the of the songs couldn't be found on Spotify
  \u2022 Songs on Spotify don't always have accurate names
  \u2022 Special characters sometimes cause issues with Spotify's search logic
      
  Please retry the process with 'Strict Search' disabled.
  
  If the problem persists or if you believe it's a bug, PLEASE report the issue on GitHub or the setlist.fm forum along with a link to the problematic setlist, and I'll gladly take a look at it.
  
  Thank you!`
    .split('\n').map(line => line.trim()).join('\n');
