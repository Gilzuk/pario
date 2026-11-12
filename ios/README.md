# Pairo: a spot-the-match card game for iOS

Native SwiftUI, iOS 17+, iPhone and iPad. No third-party dependencies, no network, no data collection.

## What's inside

| File | Role |
|---|---|
| `Pairo/Deck.swift` | 57-card deck from the projective plane of order 7 (any two cards share exactly one symbol), dealer, randomised card layouts |
| `Pairo/Games.swift` | Game logic: **Solo Sprint** (60 s, −3 s per wrong tap, best score saved) and **Duel** (2 players on one device, first to 10, wrong tap freezes 1.5 s) |
| `Pairo/Components.swift` | Card view, buttons, shake effect, theme |
| `Pairo/Screens.swift` | App entry, home, solo, duel, rules screens |
| `Pairo/Assets.xcassets` | 1024×1024 app icon (opaque), accent colour |
| `Pairo/PrivacyInfo.xcprivacy` | Privacy manifest (UserDefaults reason CA92.1, no tracking) |
| `Config/Info.plist` | Sets `ITSAppUsesNonExemptEncryption = NO` so uploads skip the export-compliance prompt |

## Run it

1. Open `Pairo.xcodeproj` in Xcode 16 or later (on a Mac).
2. Select the **Pairo** target → *Signing & Capabilities* → choose your Team.
3. Change the bundle ID from `com.example.pairo` to your own, such as `com.gilzukerman.pairo`.
4. Pick a simulator or your iPhone and press ⌘R.

## Ship it to the App Store

1. **Apple Developer Program**: enrol at developer.apple.com ($99/year).
2. **App Store Connect** → My Apps → **+ New App**: platform iOS, your bundle ID, SKU `pairo-001`.
   - If "Pairo" is already taken, pick another name. The display name lives in `INFOPLIST_KEY_CFBundleDisplayName` in the build settings.
3. **Archive**: in Xcode, choose *Any iOS Device (arm64)*, then **Product → Archive → Distribute App → App Store Connect → Upload**.
4. **Screenshots**: run on the iPhone 16 Pro Max simulator (6.9") and an iPad Pro 13" simulator, then press ⌘S. You need at least one per size. Good shots are the home screen, Solo mid-game, Duel mid-game, and the results screen.
5. **App Privacy**: answer "Data Not Collected".
6. **Age rating**: answer "None" to everything, which gives 4+.
7. **Privacy Policy URL**: required even with no data collection. A one-paragraph page (GitHub Pages, Notion) saying "Pairo collects no personal data; your best score is stored only on your device" is enough.
8. Attach the build, fill in the listing below, and **Submit for Review**.

## Store listing (draft)

**Name:** Pairo – Spot the Match
**Subtitle:** Fast symbol-matching card game
**Category:** Games → Family (secondary: Card)
**Keywords:** match,symbol,spot,speed,card,party,family,reflex,kids,duel,two player,brain

**Description:**
> Every two cards share exactly one symbol. Can you find it before your opponent does?
>
> SOLO SPRINT: Race the clock. Spot the match, tap it, and the next card flies in. Wrong guesses cost you time. How many can you get in 60 seconds?
>
> DUEL: Lay your phone on the table and challenge a friend face to face. Each of you has your own card, and both race to match the centre card. Wrong tap? You're frozen. First to 10 wins.
>
> • 57 colourful symbols and 57 unique cards
> • Every card is laid out fresh, with new sizes and rotations each time
> • Haptic feedback
> • No ads, no accounts, no internet needed
> • Great for kids and adults

**Promotional text:** Quick reflexes, sharp eyes. Beat your best score or challenge a friend on one phone.

## Trademark note

Do **not** use "Dobble", "Spot It!", or their artwork in the name, keywords, or screenshots, because they are registered trademarks. The underlying game mechanic and the mathematics are not protected, and the name, symbols, and visuals in this project are all original. Apple's system emoji are fine to use inside an iOS app.

## Easy next steps

- Sound effects (`AVAudioPlayer` with short .caf files)
- Game Center leaderboard for Solo Sprint
- Themed symbol packs (letters for kids, Hebrew letters, flags)
- Localisation (Hebrew is a natural first one; SwiftUI flips layout automatically)
