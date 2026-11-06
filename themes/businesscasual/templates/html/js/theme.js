(() => {
  const palette = document.querySelector("#dark-palette");
  const system = window.matchMedia(palette.media);
  const key = "business-casual-theme";
  const localPreview = location.protocol === "file:";
  const siteRoot = new URL("../../", document.currentScript.src);
  const previewChoice = new URL(location.href).searchParams.get(key);

  function readPreference() {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  let preference = localPreview && previewChoice ? previewChoice : readPreference();
  let button;

  function update() {
    const dark = preference ? preference === "dark" : system.matches;
    palette.media = preference ? (dark ? "all" : "not all") : system.media;
    button?.setAttribute("aria-checked", String(dark));
  }

  function updatePreviewLinks() {
    if (!localPreview || !preference) return;
    for (const link of document.querySelectorAll("a[href]")) {
      const target = new URL(link.href);
      if (target.href.startsWith(siteRoot.href) && /\.html$/i.test(target.pathname)) {
        target.searchParams.set(key, preference);
        link.href = target.href;
      }
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    button = document.querySelector("#theme-toggle");
    button.addEventListener("click", () => {
      preference = button.getAttribute("aria-checked") === "true" ? "light" : "dark";
      update();
      try {
        localStorage.setItem(key, preference);
      } catch {
        // Local previews also pass the preference in page links.
      }
      updatePreviewLinks();
    });
    update();
    updatePreviewLinks();
    button.hidden = false;
  });

  window.addEventListener("storage", (event) => {
    if (!localPreview && (event.key === key || event.key === null)) {
      preference = readPreference();
      update();
    }
  });

  window.addEventListener("pageshow", (event) => {
    if (event.persisted && !localPreview) {
      preference = readPreference();
      update();
    }
  });
  system.addEventListener("change", update);
  update();
})();
