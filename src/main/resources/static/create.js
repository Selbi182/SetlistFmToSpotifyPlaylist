(function() {
  setCopyrightYear();
  createKofiButton();
  refreshConvertedSetlistsCounter();

  ///////////////////////

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
            throw "ERROR: Failed to create playlist\n\n"
            + "Possible reasons:\n"
            + "\u2022 Couldn't find the setlist on setlist.fm\n"
            + "\u2022 A majority of the songs (at least 50%) couldn't be found on Spotify\n"
            + "\u2022 Songs on Spotify don't always have 100% accurate names\n"
            + "\u2022 Special characters sometimes cause issues with Spotify's search logic\n\n"
            + "Please retry the process with 'Strict Search' disabled. If the problem persists or if you believe it's a bug, "
            + "PLEASE report the issue on GitHub or the setlist.fm forum along with a link to the problematic setlist, and I'll gladly take a look at it. "
            + "Thank you!";
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
            playlistEmbed.innerHTML =
                `<div id="summary"><a href="${setlistCreationResponse.playlistUrl}" target="_blank">${setlistCreationResponse.playlistUrl}</a> // Generated in ${(setlistCreationResponse.timeTaken / 1000).toFixed(1)}s</div>`
              + `<iframe src="${playlistUrl}" width="100%" height="380" frameBorder="0" allowfullscreen="" allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture" loading="lazy"></iframe>`;

            refreshConvertedSetlistsCounter();

            let missedSongs = setlistCreationResponse.missedSongs;
            if (missedSongs.length > 0) {
              let missedSongsContainer = document.getElementById("missed-songs");
              missedSongsContainer.classList.add("show");
              missedSongsContainer.setAttribute("data-header-text", `Missed Songs (${missedSongs.length}):`);
              for (let missedSong of missedSongs) {
                let missedSongElem = document.createElement("tr");
                missedSongElem.innerHTML = `<td class="missed-song-index">${missedSong.index}.</td><td class="missed-song-name">${missedSong.songName}</td>`;
                missedSongsContainer.append(missedSongElem);
              }
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