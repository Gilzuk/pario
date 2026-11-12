import Foundation

/// A deck built on the finite projective plane of order 7:
/// 57 symbols, 57 cards, 8 symbols per card, and any two cards share exactly one symbol.
enum Deck {
    static let order = 7
    static let symbolsPerCard = order + 1

    static let symbols: [String] = [
        "🐶", "🐱", "🦊", "🐻", "🐼", "🐸", "🐵", "🦁", "🐯", "🐮",
        "🐷", "🐔", "🐧", "🦉", "🐙", "🦀", "🐢", "🐝", "🦋", "🐞",
        "🌵", "🌻", "🍀", "🍄", "🌙", "⭐️", "☀️", "🔥", "💧", "❄️",
        "⚡️", "🌈", "🍎", "🍋", "🍉", "🍇", "🍓", "🍕", "🍩", "🧀",
        "🥕", "🌶️", "🎈", "🎁", "🔑", "⚓️", "🚀", "🚗", "⚽️", "🎸",
        "🎲", "✏️", "💡", "⏰", "❤️", "👻", "🤖"
    ]

    /// Each card is a list of symbol indices.
    static let cards: [[Int]] = {
        let n = order
        var result: [[Int]] = []
        // Lines y = a·x + b, plus the point at infinity for slope a.
        for a in 0..<n {
            for b in 0..<n {
                var card = (0..<n).map { x in n * x + (a * x + b) % n }
                card.append(n * n + a)
                result.append(card)
            }
        }
        // Vertical lines x = c, plus the vertical point at infinity.
        for c in 0..<n {
            var card = (0..<n).map { y in n * c + y }
            card.append(n * n + n)
            result.append(card)
        }
        // The line at infinity.
        result.append(Array((n * n)...(n * n + n)))
        return result
    }()

    static func sharedSymbol(_ a: Int, _ b: Int) -> Int {
        Set(cards[a]).intersection(cards[b]).first ?? -1
    }
}

/// Draws card indices from a shuffled pile, reshuffling when empty.
struct Dealer {
    private var pile: [Int] = []

    mutating func draw(avoiding inPlay: [Int]) -> Int {
        while true {
            if pile.isEmpty { pile = Array(Deck.cards.indices).shuffled() }
            let next = pile.removeLast()
            if !inPlay.contains(next) { return next }
        }
    }
}

struct SymbolSlot: Identifiable, Hashable {
    let symbol: Int
    /// Offset from the card centre, in units of card diameter.
    let x: Double
    let y: Double
    /// Font size, in units of card diameter.
    let size: Double
    let rotation: Double
    var id: Int { symbol }
}

/// A card in play: its deck index plus a randomised layout.
struct PlayCard: Identifiable, Equatable {
    let id = UUID()
    let index: Int
    let slots: [SymbolSlot]

    init(index: Int) {
        self.index = index
        let symbols = Deck.cards[index].shuffled()
        let ring = symbols.count - 1
        let base = Double.random(in: 0..<(2 * Double.pi))
        var slots: [SymbolSlot] = []
        for (i, s) in symbols.enumerated() {
            if i == 0 {
                slots.append(SymbolSlot(symbol: s, x: 0, y: 0,
                                        size: .random(in: 0.16...0.21),
                                        rotation: .random(in: -180...180)))
            } else {
                let angle = base + Double(i - 1) * 2 * Double.pi / Double(ring)
                    + .random(in: -0.1...0.1)
                let r = Double.random(in: 0.29...0.32)
                slots.append(SymbolSlot(symbol: s, x: r * cos(angle), y: r * sin(angle),
                                        size: .random(in: 0.11...0.15),
                                        rotation: .random(in: -180...180)))
            }
        }
        self.slots = slots
    }

    static func == (lhs: PlayCard, rhs: PlayCard) -> Bool { lhs.id == rhs.id }
}
