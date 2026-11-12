# Pairo for the web

`pairo.html` is the complete game as one page: Levels, Play Online, Sprint, Wheels and same-phone Duel, plus music and animation. The music is embedded as an MP3.

It's published as a Claude artifact. That's where it gets the page wrapper (doctype and viewport tag) and the runtime capabilities that online play uses: `room` for live matches and `user` for player names. Opened as a plain file, everything except Play Online works.

## GitHub Pages

`docs/index.html` is this page wrapped in a full HTML document so GitHub Pages can serve it as-is. Don't edit it by hand. After changing `pairo.html`, rebuild it from the repository root:

    python3 web/build_pages.py

To publish: in the repository's **Settings → Pages**, set **Source** to *Deploy from a branch*, **Branch** to `main` and the folder to `/docs`. If the folder is left at `/ (root)`, the small `index.html` at the repository root forwards visitors to `docs/`, so the game opens either way. On Pages, every mode works except Play Online, which needs the Claude artifact runtime; the page explains this when someone opens it.
