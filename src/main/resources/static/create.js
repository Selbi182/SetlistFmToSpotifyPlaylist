(function() {
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
      active = true;
      inputField.setAttribute("disabled", "");
      submitButton.setAttribute("disabled", "");
      submitButton.innerHTML = "Creating Playlist...";
      spinner.classList.add("show");

      fetch(`/create?url=${url}`)
        .then(response => response.json())
        .then(setlistCreationResponse => {
          submitButton.innerHTML = "Playlist Created! Loading Embed...";

          setTimeout(() => {
            inputField.classList.add("hide");
            submitButton.classList.add("hide");
            spinner.classList.remove("show");

            let playlistId = setlistCreationResponse.playlistId;
            let playlistUrl = `https://open.spotify.com/embed/playlist/${playlistId}?utm_source=generator`;
            playlistEmbed.innerHTML = `<iframe src="${playlistUrl}" width="100%" height="380" frameBorder="0" allowfullscreen="" allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"></iframe>`;
          }, 2000)

        })
        .catch(ex => alert(ex));
    }
  }

  let regex = /https?:\/\/(www\.)?setlist\.fm\/setlist\/[\w+\-]+\/\d+\/[\w+\-]+\.html/i;
  function verifySetlistFmUrl(url) {
    return regex.test(url);
  }
})();