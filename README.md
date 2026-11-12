# Pairo

A spot-the-match card game: every two cards share exactly one symbol, and you race to find it. The decks are finite projective planes of prime order (2, 3, 5 and 7), so the "exactly one" rule holds for every pair. The full deck has 57 cards with 8 symbols each.

| Folder | What it is | Status |
|---|---|---|
| `web/` | Single-page game: Conquest (strategy campaign with army battles), Battle (armies on one phone), Play Online (cards or armies), 12 levels, Sprint, Wheels, Duel, epic music, animation | Most complete; the published artifact and GitHub Pages site |
| `android/` | Android Studio project (Java, minSdk 24, targetSdk 36): Levels, Sprint, Wheels, Duel, music and animation | Unit-tested; not yet built on a device |
| `ios/` | Xcode project (SwiftUI, iOS 17): Solo Sprint and Duel | First version; not yet compiled |
| `desktop-java/` | Single-file Swing game: Solo Sprint and Duel | Runs with `java Pairo.java` |

Conquest, Battle and online play are web-only for now. The audio is original and rendered from note data in `android/tools/` (`epic.json`, `render_epic.py`, `render.py`).

Each folder's README has build, test and store-release steps.
