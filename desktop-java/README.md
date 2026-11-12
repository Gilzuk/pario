# Pairo (Java edition)

Spot-the-match card game for the desktop: Java 17+ and Swing, one source file, no dependencies.

## Run

- **Double-click `Pairo.jar`**, or run `java -jar Pairo.jar`
- Or from source with no build step: `java Pairo.java`
- Self-test: `java Pairo.java --test` checks the deck (all 1,596 card pairs share exactly one symbol) and plays through the solo and duel logic.

Requires a Java 17+ runtime, for example from adoptium.net.

## Modes

- **Solo Sprint:** 60 seconds. Click the symbol both cards share. A wrong click costs 3 s, and your best score is saved.
- **Duel:** each player matches their own card against the centre card. A wrong click freezes that player for 1.5 s, and the first to 10 wins. Works best on a touchscreen.
- **Keys:** Esc goes back to the menu. Space or Enter starts a new round.

## Build the JAR yourself

    javac --release 17 -d build Pairo.java
    jar cfe Pairo.jar Pairo -C build .
