(function() {
  let inputField = document.getElementById("input");
  let submitButton = document.getElementById("submit");

  inputField.oninput = (e) => {
    let value = e.target.value;
    // TODO verify input as you type to match some regex for the URL
    if (value) {
      submitButton.removeAttribute("disabled");
    } else {
      submitButton.setAttribute("disabled", "");
    }
  }

  submitButton.onclick = () => {
    // TODO the actual submission
    // TODO lock submit button
    // TODO present the playlist URL
  }
})();