// ==UserScript==
// @name        setlist.fm - Create Spotify Playlist
// @namespace   selbi
// @match       https://www.setlist.fm/setlist/*/*/*.html
// @grant       none
// @version     1.0
// @author      Selbi
//
// ==/UserScript==
const CONTROLLER_URL = "";

let path = window.location.pathname;
let idStart = path.lastIndexOf("-") + 1;
let idEnd = path.lastIndexOf(".html");
let setlistId = path.slice(idStart, idEnd);

let container = document.querySelector(".setlistHeader > .row > .pull-right");
container.innerHTML = '<input placeholder="Custom target playlist..."><i class="fa fa-spotify fa-stack-2x" />';
let spotifyButton = container.querySelector("i");
spotifyButton.style.textAlign = "right";
spotifyButton.style.width = "50px";
spotifyButton.style.cursor = "pointer";
spotifyButton.style.color = "#1DB954";
spotifyButton.title = "Create Spotify Playlist";

let active = false;
spotifyButton.onclick = () => {
  if (!active) {
    let password = prompt("Enter password:");
    if (password) {
      active = true;
      let customTargetPlaylistContainer = container.querySelector("input");
      spotifyButton.style.cursor = "initial";
      spotifyButton.style.color = "#7F7F7F";
      fetch(CONTROLLER_URL, {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          "password": password,
          "setlistFmId": setlistId,
          "targetPlaylist": customTargetPlaylistContainer.value,
          "dry": false
        })
      })
      .then(response => response.json())
      .then(() => alert("Playlist created!"))
      .catch(ex => alert(ex));
    }
  } else {
    alert("The setlist has already been created!");
  }
};