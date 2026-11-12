# Pairo for Android (Java)

Spot-the-match card game for Android phones. It's written in Java with one custom `View` and has no third-party libraries, ads, network access, or permissions.

- **Levels:** 12 levels with stars. Reach the goal before time runs out for ★, and score more for ★★ and ★★★. Each level unlocks the next, and progress is saved on the phone.
  - **1–4:** smaller decks first (3, 4, 6, then 8 symbols per card). Every deck size keeps the "exactly one shared symbol" rule.
  - **5–8:** spinning cards, tiny symbols, and symbols that reshuffle.
  - **9–12:** harsher penalties, a 30-second level, and finally Grandmaster, with everything at once.
- **Solo Sprint:** match as many cards as you can in 60 seconds. A wrong tap costs 3 s, and your best score is saved.
- **Wheels** (new): a 60-second sprint where each card is two wheels, an inner ring of 3 symbols and an outer ring of 5, turning in opposite directions. The rings speed up with every match (×1 up to ×2.6), and Wheels keeps its own best score.
- **Duel:** lay the phone flat between two players. Each player has their own card facing them, and both can tap at the same time (multi-touch). A wrong tap freezes that player for 1.5 s, and the first to 10 wins.
- **Music:** an original epic theme in D minor with taiko drums, a string ostinato, brass and a choir pad. It speeds up in the last 10 seconds of a Solo round. The speaker button mutes everything, and the setting is remembered.
- **Sound effects:** match, miss, freeze, countdown ticks, the "Go!" cue, time-up and a win fanfare.
- **Animation:** a 3-2-1 countdown, matched cards flying into place, a burst and "+1" on each match, a red ✕ and "−3s" on a miss, a pulsing timer near the end, confetti on a new best or a Duel win, and drifting symbols on the menu. Turning off animations in Android's accessibility settings also turns these off.
- Haptic feedback, edge-to-edge layout, predictive Back (Android 13+), and the clock and music pause when the app goes to the background.

minSdk 24 (Android 7.0, about 98% of devices) · targetSdk 36 (Android 16)

## Project layout

| File | Role |
|---|---|
| `app/src/main/java/.../Core.java` | Decks (projective planes of order 2, 3, 5 and 7), card layout, the 12 levels, star progress, the timed round (Sprint or level) and Duel. Pure Java with no Android imports. |
| `app/src/main/java/.../Symbols.java` | 57 symbols: 10 vector shapes × 6 colours |
| `app/src/main/java/.../GameView.java` | All screens, drawing, multi-touch, haptics |
| `app/src/main/java/.../Sound.java` | Looping music (MediaPlayer) and sound effects (SoundPool), mute, end-of-round speed-up |
| `app/src/main/res/raw/` | `music.ogg` (20 s seamless loop) and `sfx_*.ogg` |
| `tools/` | `epic.json` + `render_epic.py` produce the music; `render.py` produces the effects. Edit the notes and run `python3 tools/render_epic.py app/src/main/res/raw` (needs numpy, scipy, ffmpeg) |
| `app/src/main/java/.../MainActivity.java` | Edge-to-edge window, Back handling, pause/resume |
| `app/src/test/.../CoreTest.java` | JUnit tests: every deck is valid, symbols never overlap or leave the card, levels award stars and unlock correctly, plus the Sprint and Duel rules |
| `app/src/main/res/mipmap-*` | Adaptive launcher icon (with themed-icon layer) and legacy icons |
| `store/` | Play Store icon (512), feature graphic (1024×500), phone screenshots |

## Run it

1. Install **Android Studio** and choose **File → Open** on this folder. Let Gradle sync; it downloads the Android plugin and SDK the first time.
2. Plug in a phone with USB debugging on, or create an emulator, then press **Run ▶**.
3. Unit tests: `./gradlew test`, or right-click `CoreTest` → Run.

If Studio suggests updating the Android Gradle Plugin, accept it.

## Publish on Google Play

1. **Change the app ID.** In `app/build.gradle`, set `applicationId` to something you own, such as `io.github.yourname.pairo`. Google Play rejects `com.example.*` IDs, and the ID can never change after the first upload.
2. **Developer account:** play.google.com/console, $25 one-time fee with identity verification.
   - *New personal accounts* must run a **closed test with at least 12 testers for 14 consecutive days** before they can publish to production. Plan for this.
3. **Build the release bundle:** Build → Generate Signed App Bundle → Android App Bundle → create a new keystore. Back up the `.jks` file and passwords; `.gitignore` already excludes them. Upload the resulting `app-release.aab`.
4. **Play App Signing:** accept the default, so Google holds the app-signing key and your keystore is only the upload key.
5. **Store listing:** use `store/play-icon-512.png`, `store/feature-graphic-1024x500.png`, and at least two phone screenshots (`store/screenshot-*.png` are 1080×2400 renders, or take your own from a device).
6. **App content forms:**
   - Privacy policy URL: required. One paragraph is enough: "Pairo collects no data. Your best score is stored only on your device."
   - Data safety: "No data collected or shared."
   - Ads: No. Content rating questionnaire: all "No", which gives Everyone / PEGI 3.
   - Target audience: if you include under-13s, the app falls under the Families policy. It qualifies because it has no ads or data collection, but you must say so in the form.
7. Send the build through closed testing, then production, and submit for review.

## Store listing (draft)

**Title:** Pairo: Spot the Match
**Short description (80 chars):** Every two cards share one symbol. Spot it first! Solo sprint or 2-player duel.
**Full description:**
> Every two cards share exactly one symbol. Can you find it before your opponent does?
>
> SOLO SPRINT: Race the clock. Spot the match, tap it, and the next card flies in. Wrong guesses cost you time. How many can you get in 60 seconds?
>
> DUEL: Lay your phone on the table and challenge a friend face to face. Each of you has your own card, and both race to match the centre card. Wrong tap? You're frozen. First to 10 wins.
>
> • 57 symbols, 57 unique cards, and a fresh layout every deal
> • Two players on one phone, with true multi-touch
> • No ads, no accounts, no internet needed

**Category:** Games → Card (or Casual) · **Tags:** Family, Puzzle

## Trademark note

Don't use "Dobble", "Spot It!", or their artwork in the title, description, or tags. The game mechanic and its mathematics aren't protected, and all names and artwork here are original.
