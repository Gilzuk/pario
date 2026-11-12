import SwiftUI

@main
struct PairoApp: App {
    var body: some Scene {
        WindowGroup { HomeView() }
    }
}

// MARK: - Home

struct HomeView: View {
    enum Screen: String, Identifiable { case solo, duel; var id: String { rawValue } }

    @State private var screen: Screen?
    @State private var showRules = false
    @State private var demo = PlayCard(index: Int.random(in: Deck.cards.indices))
    @AppStorage(SoloGame.bestKey) private var best = 0

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 18) {
                Spacer(minLength: 8)
                Text("Pairo")
                    .font(.system(size: 60, weight: .black, design: .rounded))
                    .foregroundStyle(.white)
                Text("Every two cards share exactly one symbol.\nFind it first.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white.opacity(0.85))
                CardView(card: demo)
                    .frame(maxWidth: 250)
                    .id(demo.id)
                    .transition(.scale.combined(with: .opacity))
                    .onTapGesture {
                        withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                            demo = PlayCard(index: Int.random(in: Deck.cards.indices))
                        }
                    }
                    .padding(.vertical, 8)
                Spacer(minLength: 8)
                MenuButton(title: "Solo Sprint",
                           subtitle: best > 0 ? "Best: \(best) matches" : "As many matches as you can in 60s",
                           systemImage: "timer") { screen = .solo }
                MenuButton(title: "Duel",
                           subtitle: "Two players, one device",
                           systemImage: "person.2.fill") { screen = .duel }
                Button("How to Play") { showRules = true }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .padding(.top, 4)
                Spacer(minLength: 8)
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: 520)
        }
        .fullScreenCover(item: $screen) { screen in
            switch screen {
            case .solo: SoloView()
            case .duel: DuelView()
            }
        }
        .sheet(isPresented: $showRules) { RulesView() }
    }
}

// MARK: - Solo

struct SoloView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var game = SoloGame()

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 14) {
                HStack(spacing: 10) {
                    CloseButton { game.stop(); dismiss() }
                    Spacer()
                    StatPill(systemImage: "star.fill", text: "\(game.score)")
                    StatPill(systemImage: "timer", text: "\(max(0, Int(game.timeLeft.rounded(.up))))")
                }
                .foregroundStyle(.white)

                ProgressView(value: max(0, game.timeLeft), total: SoloGame.duration)
                    .tint(.white)

                Spacer(minLength: 0)
                CardView(card: game.top) { game.tap($0) }
                    .id(game.top.id)
                    .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity),
                                            removal: .scale(scale: 0.5).combined(with: .opacity)))
                Text("Tap the symbol both cards share")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.85))
                CardView(card: game.hand) { game.tap($0) }
                    .id(game.hand.id)
                    .transition(.scale(scale: 0.6).combined(with: .opacity))
                    .modifier(Shake(animatableData: CGFloat(game.shakes)))
                Spacer(minLength: 0)
            }
            .padding()
            .frame(maxWidth: 520)

            if game.phase != .playing { overlay }
        }
        .onDisappear { game.stop() }
    }

    private var overlay: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()
            VStack(spacing: 16) {
                if game.phase == .ready {
                    Text("Solo Sprint").font(.largeTitle.weight(.black))
                    Text("Tap the one symbol that appears on both cards. Every match scores a point. A wrong tap costs \(Int(SoloGame.penalty)) seconds.")
                        .multilineTextAlignment(.center)
                    BigButton(title: "Start", systemImage: "play.fill") { game.start() }
                } else {
                    Text(game.isNewBest ? "New best! 🎉" : "Time's up!")
                        .font(.title.weight(.black))
                    Text("\(game.score)")
                        .font(.system(size: 80, weight: .black, design: .rounded))
                    Text("Best: \(game.bestScore)").font(.headline).opacity(0.8)
                    BigButton(title: "Play again", systemImage: "arrow.clockwise") { game.start() }
                    Button("Menu") { dismiss() }.font(.headline)
                }
            }
            .foregroundStyle(.white)
            .padding(28)
            .frame(maxWidth: 360)
            .background(Theme.ink.opacity(0.92), in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .padding()
        }
        .transition(.opacity)
    }
}

// MARK: - Duel

struct DuelView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var game = DuelGame()

    var body: some View {
        GeometryReader { geo in
            let w = min(geo.size.width, 520)
            let handSize = min(w * 0.82, geo.size.height * 0.33)
            let centerSize = min(w * 0.55, geo.size.height * 0.22)
            ZStack {
                Theme.background.ignoresSafeArea()
                VStack(spacing: 0) {
                    PlayerArea(game: game, player: 1, cardSize: handSize)
                        .rotationEffect(.degrees(180))
                    Spacer(minLength: 0)
                    CardView(card: game.center)
                        .frame(width: centerSize, height: centerSize)
                        .id(game.center.id)
                        .transition(.scale(scale: 0.7).combined(with: .opacity))
                    Spacer(minLength: 0)
                    PlayerArea(game: game, player: 0, cardSize: handSize)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)

                VStack {
                    Spacer()
                    HStack { CloseButton { dismiss() }; Spacer() }
                    Spacer()
                }
                .padding(.horizontal)

                if let winner = game.winner { winnerOverlay(winner) }
            }
        }
        .persistentSystemOverlays(.hidden)
    }

    private func winnerOverlay(_ winner: Int) -> some View {
        ZStack {
            Color.black.opacity(0.5).ignoresSafeArea()
            VStack(spacing: 40) {
                banner(winner: winner).rotationEffect(.degrees(180))
                VStack(spacing: 12) {
                    BigButton(title: "Rematch", systemImage: "arrow.clockwise") {
                        withAnimation { game.reset() }
                    }
                    Button("Menu") { dismiss() }.font(.headline).foregroundStyle(.white)
                }
                .frame(maxWidth: 300)
                banner(winner: winner)
            }
            .padding()
        }
        .transition(.opacity)
    }

    private func banner(winner: Int) -> some View {
        VStack(spacing: 4) {
            Text("Player \(winner + 1) wins!").font(.largeTitle.weight(.black))
            Text("\(game.scores[0]) – \(game.scores[1])").font(.title2.weight(.bold)).opacity(0.85)
        }
        .foregroundStyle(.white)
    }
}

struct PlayerArea: View {
    let game: DuelGame
    let player: Int
    let cardSize: CGFloat

    var body: some View {
        VStack(spacing: 10) {
            ZStack {
                CardView(card: game.hands[player]) { game.tap(player: player, symbol: $0) }
                    .id(game.hands[player].id)
                    .transition(.scale(scale: 0.6).combined(with: .opacity))
                    .opacity(game.frozen[player] ? 0.4 : 1)
                if game.frozen[player] {
                    Image(systemName: "snowflake")
                        .font(.system(size: cardSize * 0.3, weight: .bold))
                        .foregroundStyle(.white)
                        .allowsHitTesting(false)
                }
            }
            .frame(width: cardSize, height: cardSize)
            .modifier(Shake(animatableData: CGFloat(game.shakes[player])))

            HStack(spacing: 10) {
                Text("Player \(player + 1)").font(.headline)
                HStack(spacing: 4) {
                    ForEach(0..<DuelGame.target, id: \.self) { i in
                        Circle()
                            .fill(i < game.scores[player] ? Color.white : Color.white.opacity(0.25))
                            .frame(width: 9, height: 9)
                    }
                }
            }
            .foregroundStyle(.white)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Player \(player + 1): \(game.scores[player]) of \(DuelGame.target)")
        }
    }
}

// MARK: - Rules

struct RulesView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("The secret") {
                    Text("There are 57 cards with 8 symbols each. Any two cards share exactly one symbol, never more and never less. Symbols can be different sizes and rotations, so look carefully.")
                }
                Section("Solo Sprint") {
                    Text("Tap the symbol that appears on both cards. Each match scores a point and deals a new card. A wrong tap costs \(Int(SoloGame.penalty)) seconds. Score as much as you can in \(Int(SoloGame.duration)) seconds.")
                }
                Section("Duel") {
                    Text("Lay the phone flat between two players. Each player finds the symbol shared by their own card and the centre card, then taps it on their card. The winner's card becomes the new centre card. A wrong tap freezes you for a moment. First to \(DuelGame.target) wins.")
                }
            }
            .navigationTitle("How to Play")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
    }
}
