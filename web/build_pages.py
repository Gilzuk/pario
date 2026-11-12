"""Builds docs/index.html (served by GitHub Pages) from web/pairo.html.

web/pairo.html is page content for a Claude artifact: the artifact host wraps it in the
document skeleton when it publishes. GitHub Pages serves files as they are, so this script
adds that skeleton: doctype, charset, the mobile viewport, and padding that keeps the game
clear of a phone's notch and home bar.

Run from the repository root:  python3 web/build_pages.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "web" / "pairo.html"
TARGET = ROOT / "docs" / "index.html"

HEAD = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="description" content="Pairo: every two cards share exactly one symbol. Find it first.">
<meta name="theme-color" content="#5738DB">
<!-- Generated from web/pairo.html by web/build_pages.py. Edit the source, then rebuild. -->
<style>
  html { box-sizing: border-box; padding: env(safe-area-inset-top, 0px) 0 env(safe-area-inset-bottom, 0px); }
  *, *::before, *::after { box-sizing: inherit; }
</style>
</head>
<body>
"""
TAIL = "\n</body>\n</html>\n"


def main() -> None:
    page = SOURCE.read_text(encoding="utf-8")
    # Move the page's <title> (its first line) into the head.
    title = ""
    if page.startswith("<title>"):
        end = page.index("</title>") + len("</title>")
        title, page = page[:end], page[end:].lstrip("\n")
    head = HEAD.replace("</head>", title + "\n</head>") if title else HEAD
    TARGET.parent.mkdir(exist_ok=True)
    TARGET.write_text(head + page + TAIL, encoding="utf-8")
    print(f"wrote {TARGET.relative_to(ROOT)} ({TARGET.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
