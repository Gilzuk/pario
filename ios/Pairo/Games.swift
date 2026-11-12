import SwiftUI
import Observation
import UIKit

enum Haptics {
    static func success() { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    static func error() { UINotificationFeedbackGenerator().notificationOccurred(.error) }
}

// MARK: - Solo Sprint

@MainActor
@Observable
final class SoloGame {
    enum Phase { case ready, playing, finished }

    static let duration: Double = 60
    static let penalty: Double = 3
    nonisolated static let bestKey = "bestSolo"

    private(set) var phase: Phase = .ready
    private(set) var top: PlayCard
    private(set) var hand: PlayCard
    private(set) var score = 0
    private(set) var timeLeft: Double = SoloGame.duration
    private(set) var shakes = 0
    private(set) var bestScore: Int
    private(set) var isNewBest = false

    @ObservationIgnored private var dealer = Dealer()
    @ObservationIgnored private var timerTask: Task<Void, Never>?

    init() {
        var d = Dealer()
        let a = d.draw(avoiding: [])
        let b = d.draw(avoiding: [a])
        top = PlayCard(index: a)
        hand = PlayCard(index: b)
        dealer = d
        bestScore = UserDefaults.standard.integer(forKey: Self.bestKey)
    }

    func start() {
        let a = dealer.draw(avoiding: [])
        let b = dealer.draw(avoiding: [a])
        top = PlayCard(index: a)
        hand = PlayCard(index: b)
        score = 0
        timeLeft = Self.duration
        isNewBest = false
        phase = .playing

        timerTask?.cancel()
        timerTask = Task { [weak self] in
            var last = Date()
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(100))
                guard let self, self.phase == .playing else { return }
                let now = Date()
                self.timeLeft -= now.timeIntervalSince(last)
                last = now
                if self.timeLeft <= 0 { self.finish() }
            }
        }
    }

    func tap(_ symbol: Int) {
        guard phase == .playing else { return }
        if symbol == Deck.sharedSymbol(top.index, hand.index) {
            score += 1
            Haptics.success()
            let next = dealer.draw(avoiding: [top.index, hand.index])
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                top = hand
                hand = PlayCard(index: next)
            }
        } else {
            Haptics.error()
            withAnimation(.linear(duration: 0.3)) { shakes += 1 }
            timeLeft -= Self.penalty
            if timeLeft <= 0 { finish() }
        }
    }

    func stop() {
        timerTask?.cancel()
        timerTask = nil
    }

    private func finish() {
        timeLeft = 0
        phase = .finished
        stop()
        if score > bestScore {
            bestScore = score
            isNewBest = true
            UserDefaults.standard.set(score, forKey: Self.bestKey)
        }
    }
}

// MARK: - Duel (two players, one device)

@MainActor
@Observable
final class DuelGame {
    static let target = 10
    static let freeze: Double = 1.5

    private(set) var center: PlayCard
    private(set) var hands: [PlayCard]
    private(set) var scores = [0, 0]
    private(set) var frozen = [false, false]
    private(set) var shakes = [0, 0]
    private(set) var winner: Int?

    @ObservationIgnored private var dealer = Dealer()

    init() {
        var d = Dealer()
        let c = d.draw(avoiding: [])
        let p0 = d.draw(avoiding: [c])
        let p1 = d.draw(avoiding: [c, p0])
        center = PlayCard(index: c)
        hands = [PlayCard(index: p0), PlayCard(index: p1)]
        dealer = d
    }

    func reset() {
        let c = dealer.draw(avoiding: [])
        let p0 = dealer.draw(avoiding: [c])
        let p1 = dealer.draw(avoiding: [c, p0])
        center = PlayCard(index: c)
        hands = [PlayCard(index: p0), PlayCard(index: p1)]
        scores = [0, 0]
        frozen = [false, false]
        winner = nil
    }

    func tap(player: Int, symbol: Int) {
        guard winner == nil, !frozen[player] else { return }
        if symbol == Deck.sharedSymbol(center.index, hands[player].index) {
            Haptics.success()
            scores[player] += 1
            let next = dealer.draw(avoiding: [center.index, hands[0].index, hands[1].index])
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                center = hands[player]
                hands[player] = PlayCard(index: next)
            }
            if scores[player] >= Self.target {
                withAnimation(.easeOut) { winner = player }
            }
        } else {
            Haptics.error()
            withAnimation(.linear(duration: 0.3)) { shakes[player] += 1 }
            frozen[player] = true
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(DuelGame.freeze))
                withAnimation { self?.frozen[player] = false }
            }
        }
    }
}
