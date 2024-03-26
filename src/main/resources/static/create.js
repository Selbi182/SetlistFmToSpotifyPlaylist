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
(function () {
  setCopyrightYear();
  createKofiButton();
  document.getElementById("counter").classList.add("show");

  ///////////////////////

  const errorText =
    `ERROR: Failed to create playlist!

    Possible reasons:
    \u2022 Couldn't find the setlist on setlist.fm
    \u2022 More than half of the of the songs couldn't be found on Spotify
    \u2022 Songs on Spotify don't always have accurate names
    \u2022 Special characters sometimes cause issues with Spotify's search logic
        
    Please retry the process with 'Strict Search' disabled.
    
    If the problem persists or if you believe it's a bug, PLEASE report the issue on GitHub or the setlist.fm forum along with a link to the problematic setlist, and I'll gladly take a look at it.
    
    Thank you!`.split('\n').map(line => line.trim()).join('\n');

  const validSetlistUrlRegex = /^https?:\/\/(www\.)?setlist\.fm\/setlist\/[\w+\-]+\/\d+\/[\w+\-]+\.html$/i;

  let inputField = document.getElementById("input");
  let submitButton = document.getElementById("submit");
  let formatInfo = document.getElementById("format-info");
  formatInfo.classList.add("hide");
  let spinner = document.getElementById("spinner");
  let playlistEmbed = document.getElementById("playlist-embed");

  let active = false;

  inputField.oninput = (e) => {
    verifyUrl(e.target.value);
  }

  submitButton.onclick = () => {
    let url = inputField.value;
    if (isValidSetlistUrl(url)) {
      let options = [...document.querySelectorAll('#options input:checked')].map(e => e.id).join(",");

      setFormDisabled(true);
      fetch(`/create?url=${url}&options=${options}`)
        .then(response => {
          if (response.status !== 200) {
            throw errorText;
          }
          return response.json();
        })
        .then(setlistCreationResponse => {
          submitButton.innerHTML = "Playlist Created! Loading Embed...";

          setTimeout(() => {
            inputField.classList.add("hide");
            submitButton.classList.add("hide");
            spinner.classList.remove("show");

            let playlistId = setlistCreationResponse.playlistId;
            let playlistUrl = `https://open.spotify.com/embed/playlist/${playlistId}?utm_source=generator`;
            playlistEmbed.innerHTML = `<iframe src="${playlistUrl}" width="100%" height="380" frameBorder="0" allowfullscreen="" allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture" loading="lazy"></iframe>` + `<div id="summary"><a href="${setlistCreationResponse.playlistUrl}" target="_blank">${setlistCreationResponse.playlistUrl}</a> // Generated in ${(setlistCreationResponse.timeTaken / 1000).toFixed(1)}s</div>`

            refreshConvertedSetlistsCounter();

            let searchResults = setlistCreationResponse.searchResults;
            if (searchResults.length > 0) {
              let searchResultsContainer = document.getElementById("search-results");
              searchResultsContainer.classList.add("show");
              searchResultsContainer.setAttribute("data-header-text", `Search Results:`);
              for (let searchResult of searchResults) {
                let searchResultRow = document.createElement("tr");
                if (searchResult.resultType.includes("MATCH")) {
                  searchResultRow.classList.add("collapse");
                }
                let searchResultType = searchResult.resultType.split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(' ');

                searchResultRow.innerHTML = `<td class="search-result-index">${searchResult.song.index}.</td><td class="search-result-name">${searchResult.song.songName}</td><td class="search-result-type">${searchResultType}</td>`;
                searchResultsContainer.append(searchResultRow);
              }

              let fullSummaryButton = document.createElement("div");
              fullSummaryButton.innerHTML = "Show detailed summary...";
              fullSummaryButton.id = "full-summary-button";
              fullSummaryButton.onclick = () => {
                searchResultsContainer.childNodes.forEach(child => child.classList.remove("collapse"));
                fullSummaryButton.remove();
              };
              searchResultsContainer.append(fullSummaryButton);
            }
          }, 1000)

        })
        .catch(ex => {
          alert(ex);
          setFormDisabled(false);
        });
    }
  }

  let detailedOptionsButton = document.getElementById("detailed-options");
  detailedOptionsButton.onclick = () => {
    document.getElementById("options").classList.add("detailed");
    detailedOptionsButton.classList.add("hide");
  }

  const urlParams = new URLSearchParams(window.location.search);
  const autoSetlistUrl = urlParams.get('auto');
  if (autoSetlistUrl && isValidSetlistUrl(autoSetlistUrl)) {
    inputField.value = autoSetlistUrl;
    verifyUrl(autoSetlistUrl);
    submitButton.click();
  }

  function isValidSetlistUrl(url) {
    return validSetlistUrlRegex.test(url);
  }

  function verifyUrl(url) {
    if (!active && isValidSetlistUrl(url)) {
      submitButton.removeAttribute("disabled");
      formatInfo.classList.add("hide");
    } else {
      submitButton.setAttribute("disabled", "");
      formatInfo.classList.remove("hide");
    }
  }

  function setFormDisabled(disabled) {
    let options = document.getElementById("options");
    if (disabled) {
      active = true;
      inputField.setAttribute("disabled", "");
      submitButton.setAttribute("disabled", "");
      submitButton.innerHTML = "Creating Playlist...";
      spinner.classList.add("show");
      options.classList.add("hide");
    } else {
      active = false;
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

  function setCopyrightYear() {
    let copyrightYear = document.getElementById("copyright-year");
    let startYear = copyrightYear.innerHTML;
    let currentYear = new Date().getFullYear().toString();
    copyrightYear.innerHTML = startYear < currentYear
      ? `${startYear} \u2013 ${currentYear}`
      : currentYear;
  }

  function createKofiButton() {
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
  }
})();