(function() {
  setCopyrightYear();
  refreshConvertedSetlistsCounter();

  ///////////////////////

  let inputField = document.getElementById("input");
  let submitButton = document.getElementById("submit");
  let formatInfo = document.getElementById("format-info");
  let spinner = document.getElementById("spinner");
  let playlistEmbed = document.getElementById("playlist-embed");

  let active = false;

  inputField.oninput = (e) => {
    let url = e.target.value;

    if (!active && verifySetlistFmUrl(url)) {
      submitButton.removeAttribute("disabled");
      formatInfo.classList.add("hide");
    } else {
      submitButton.setAttribute("disabled", "");
      formatInfo.classList.remove("hide");
    }
  }

  submitButton.onclick = () => {
    let url = inputField.value;
    if (verifySetlistFmUrl(url)) {
      setFormDisabled(true);

      let options = [...document.querySelectorAll('#options > input:checked')].map(e => e.id).join(",");

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
            if (missedSongs > 0) {
              let plural = missedSongs !== 1;
              alert(`${missedSongs} song${plural ? "s" : ""} were ignored by options or couldn't be found on Spotify and ${plural ? "have" : "has"} been omitted from the playlist. If you think this can't be, please let me know on GitHub or the forum page and I'll take a look at it!`);
            }
          }, 2000)

        })
        .catch(ex => {
          alert(ex);
          setFormDisabled(false);
        });
    }
  }

  let regex = /https?:\/\/(www\.)?setlist\.fm\/setlist\/[\w+\-]+\/\d+\/[\w+\-]+\.html/i;
  function verifySetlistFmUrl(url) {
    return regex.test(url);
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