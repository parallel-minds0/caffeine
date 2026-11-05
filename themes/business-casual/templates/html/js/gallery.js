(() => {
  const viewer = document.querySelector("#image-viewer");
  if (typeof viewer.showModal !== "function") return;

  const image = viewer.querySelector("img");
  const count = viewer.querySelector('[data-viewer="count"]');
  const previous = viewer.querySelector('[data-viewer="previous"]');
  const next = viewer.querySelector('[data-viewer="next"]');
  let items;
  let index;
  let opener;

  function show(position) {
    index = (position + items.length) % items.length;
    image.src = items[index].href;
    image.alt = items[index].querySelector("img").alt;
    count.textContent = `${index + 1} / ${items.length}`;
  }

  document.querySelectorAll(".gallery").forEach((gallery) => {
    const links = Array.from(gallery.querySelectorAll("a"));
    links.forEach((link, position) => {
      link.addEventListener("click", (event) => {
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        opener = link;
        items = links;
        previous.disabled = next.disabled = links.length < 2;
        show(position);
        viewer.showModal();
      });
    });
  });

  previous.addEventListener("click", () => show(index - 1));
  next.addEventListener("click", () => show(index + 1));
  viewer.addEventListener("keydown", (event) => {
    if (event.key === "ArrowLeft") show(index - 1);
    else if (event.key === "ArrowRight") show(index + 1);
    else return;
    event.preventDefault();
  });
  viewer.addEventListener("click", (event) => {
    if (event.target === viewer) viewer.close();
  });
  viewer.addEventListener("close", () => opener.focus());
})();
