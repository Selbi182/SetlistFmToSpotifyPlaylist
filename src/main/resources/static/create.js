(function() {
  setCopyrightYear();
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
            throw "ERROR: Couldn't find setlist, the given setlist is empty, or none of the songs could be found on Spotify. If you think this can't be, please let me know on GitHub or the forum page and I'll take a look at it!";
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
            playlistEmbed.innerHTML = `<iframe src="${playlistUrl}" width="100%" height="380" frameBorder="0" allowfullscreen="" allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"></iframe>`;
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

              let plural = missedSongs.length !== 1;
              alert(`${missedSongs.length} song${plural ? "s were" : " was"} ignored by options or couldn't be found on Spotify and ${plural ? "have" : "has"} been omitted from the playlist. If you think this can't be, please let me know on GitHub or the forum page and I'll take a look at it!`);
            }
          }, 2000)

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
      .then(text => counter.innerHTML = numberWithCommas(text));

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
})();