const inputField = document.getElementById("input");
const submitButton = document.getElementById("submit");
const options = document.getElementById("options");
const spinner = document.getElementById("spinner");
const progressInfo = document.getElementById("progress-info");

// Entry Point
window.addEventListener('load', () => initPage());

/////////////////////////////
// Main Web Request Handling

let resultsFound = false;


function createSpotifyPlaylistFromSetlistFmSetlist(url) {
  if (isValidSetlistUrl(url)) {
    setFormEnabled(false);

    let socket = new WebSocket(`/convert-ws`);
    socket.onopen = () => {
      socket.send(JSON.stringify({
        url: url,
        options: getSelectedSettings()
      }));
    };
    socket.onmessage = (event) => {
      let data = event.data;
      if (data === "ERROR") {
        alert(errorText);
        socket.close();
      }
      try {
        let json = JSON.parse(data);
        if (json.hasOwnProperty("searchResults")) {
          resultsFound = true;
          displayResults(json);
          socket.close();
        }
      } catch(ex) {
        progressInfo.textContent = data;
      }

    };
    socket.onclose = () => {
      setFormEnabled(true);
    };
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

      let searchResultTypeIcon = document.createElement("td");

      const iconMapping = {
        "MATCH": "\u2714",
        "CLOSE_MATCH": "\uFE0F\u2713",
        "COVER_ORIGINAL": "\uD83D\uDD04\uFE0E",
        "SKIPPED": "\u23E9\uFE0E",
        "NOT_FOUND": "\u274C\uFE0E"
      };
      searchResultTypeIcon.classList.add("search-result-type-icon");
      searchResultTypeIcon.innerHTML = iconMapping[searchResult.resultType];

      searchResultRow.append(searchResultIndex, searchResultName, searchResultType, searchResultTypeIcon);
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

  // Load stored settings
  let storedSettings = loadStoredSettings();
  if (!!storedSettings) {
    let previousOptionsArray = storedSettings.split(",");
    getOptionElems()
      .filter(e => previousOptionsArray.includes(e.id))
      .forEach(e => e.checked = true);
    console.info("Loaded stored settings: " + previousOptionsArray)
  } else {
    // Check all by default
    getOptionElems()
      .forEach(e => e.setAttribute("checked", ""));
    console.info("No previous settings found, enabling everything by default!")
  }

  // Update stored settings whenever a checkbox is changed
  options.onclick = () => {
    saveSettingsToStore();
  }

  // Verify URL when editing the text box
  inputField.oninput = (e) => {
    verifyUrl(e.target.value);
  }

  // Submission logic
  inputField.onkeydown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      createSpotifyPlaylistFromSetlistFmSetlist(inputField.value);
    }
  }
  submitButton.onclick = () => {
    createSpotifyPlaylistFromSetlistFmSetlist(inputField.value);
  }

  // Setup philosophy text
  document.getElementById("bonus-headline").onclick = () => {
    alert(philosophyText);
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
// Settings Management

function getOptionElems() {
  return [...document.querySelectorAll('#options input')];
}

function getSelectedSettings() {
  return getOptionElems()
    .filter(e => e.checked)
    .map(e => e.id).join(",");
}

const settingsStoreKey = "setlistOptions";
function loadStoredSettings() {
  return localStorage.getItem(settingsStoreKey);
}
function saveSettingsToStore() {
  let settingsToSave = getSelectedSettings();
  localStorage.setItem(settingsStoreKey, settingsToSave);
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

    let formatInfoAvg = formatInfo.querySelector("#format-info-avg");
    formatInfoAvg.classList.toggle("show", url.includes("/average-setlist/"));
  }
}

function setFormEnabled(enabled) {
  if (resultsFound) {
    inputField.setAttribute("disabled", "");
    submitButton.setAttribute("disabled", "");
    spinner.classList.remove("show");
    progressInfo.classList.remove("show");
    options.classList.add("hide");
  } else {
    if (enabled) {
      inputField.removeAttribute("disabled");
      submitButton.removeAttribute("disabled");
      submitButton.innerHTML = "Create Playlist";
      spinner.classList.remove("show");
      progressInfo.classList.remove("show");
      options.classList.remove("hide");
    } else {
      inputField.setAttribute("disabled", "");
      submitButton.setAttribute("disabled", "");
      submitButton.innerHTML = "Creating Playlist...";
      spinner.classList.add("show");
      progressInfo.classList.add("show");
      options.classList.add("hide");
    }
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
  \u2022 Spotify's API is currently down (unfortunately, the only option here is to wait...)
  
  If the problem persists or if you believe it's a bug, PLEASE report the issue on GitHub or the setlist.fm forum along with a link to the problematic setlist, and I'll gladly take a look at it.
  
  Thank you!`
    .split('\n').map(line => line.trim()).join('\n');

const philosophyText = `setlist.fm to Spotify – by Selbi

This tool is a 100% non-profit plaything I coded a couple of years ago to help me organize (and get excited about) concerts again, after COVID-19 completely ruined my enjoyment of music for a while.

However, I've heard concerns from people about privacy, so let me clarify my ambition and philosophy with this project: I HATE modern tech! Google, Meta, Apple, Amazon, Microsoft—these companies have infiltrated our lives to the point where data harvesting isn't just something you "might" do; it's become the universally agreed-upon currency to "unite" us all. But what it's really done is leave us with software designed to keep us glued to platforms instead of providing the actual information we're looking for.

The thing is, I didn't realize just how bad it was until I first created this setlist conversion tool. Starting from scratch, I was curious about how Google's ecosystem (Analytics, AdSense, and the like) worked. What I learned turned my stomach: Google doesn't care about you unless you include their plugins. Seriously, this page was nearly invisible on Google, even if you typed the name out character by character. The moment I added Google's tracking scripts to see how many people were using the tool, I became close to the top-ranking result. Once I got the data I was curious about (about 250 monthly visitors by the end of 2024, for the record), I removed the Google junk again. The result? I immediately dropped down so many ranks that you'd have more luck finding this tool through the setlist.fm forum post than, you know, directly getting the link from Google. And the only way to revert that is re-inserting the Google spyware again. It's ridiculous.

So, I made a decision. I axed Google and any other big tech from this service. I made a solemn vow to never, ever, EVER include ads or data tracking (and if you want to support me in any way, my Ko-fi page is the best way to do so). I believe good software that respects the customer as a FREAKING HUMAN BEING can still exist, we just need to finally embrace it.

And yes, before you say it, I'm fully aware of the irony in boycotting these companies only to make a tool that directly supports Spotify. You can't win 'em all, I guess. That said, I'd argue Spotify is more interested in pushing their AI-artist-infested playlists than caring about a few songs from a concert.

– Selbi (Jan 4, 2025)`;