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

const SETLIST_FM_API_TOKEN = "";
const USER_ID = "";

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

var active = false;
spotifyButton.onclick = () => {
  if (!active) {
    if (confirm("Do you really want to create a playlist for this setlist?")) {
      active = true;
      let customTargetPlaylistContainer = container.querySelector("input");
      let targetPlaylistId = "";
      if (customTargetPlaylistContainer.value) {
        const segments = new URL(customTargetPlaylistContainer.value).pathname.split('/');
        targetPlaylistId = segments.pop() || segments.pop(); // handle potential trailing slash
      }
      spotifyButton.style.cursor = "initial";
      spotifyButton.style.color = "#7F7F7F";
      fetch(CONTROLLER_URL, {
        method: 'POST',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          "setlistFmId": setlistId,
          "setlistFmApiToken": SETLIST_FM_API_TOKEN,
          "userId": USER_ID,
          "targetPlaylist": targetPlaylistId,
          "create": true
        })
      })
          .then(response => response.json())
          .then(response => {
            alert("Playlist created!");
          })
    }
  } else {
    alert("The setlist has already been created!");
  }
};