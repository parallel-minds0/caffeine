(() => {
  const button = document.querySelector("#theme-toggle");
  const palette = document.querySelector("#dark-palette");
  const system = window.matchMedia(palette.media);
  const key = "business-casual-theme";
  let preference;

  try {
    preference = localStorage.getItem(key);
  } catch {
    // The toggle still works when storage is unavailable.
  }

  function update() {
    const dark = preference ? preference === "dark" : system.matches;
    palette.media = preference ? (dark ? "all" : "not all") : system.media;
    button.setAttribute("aria-checked", String(dark));
  }

  button.addEventListener("click", () => {
    preference = button.getAttribute("aria-checked") === "true" ? "light" : "dark";
    update();
    try {
      localStorage.setItem(key, preference);
    } catch {
      // Keep the current page's selection.
    }
  });

  system.addEventListener("change", update);
  update();
  button.hidden = false;
})();
