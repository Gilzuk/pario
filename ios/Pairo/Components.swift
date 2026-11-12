import SwiftUI

enum Theme {
    static let background = LinearGradient(
        colors: [Color(red: 0.34, green: 0.22, blue: 0.86), Color(red: 0.94, green: 0.34, blue: 0.55)],
        startPoint: .topLeading, endPoint: .bottomTrailing)
    static let ink = Color(red: 0.20, green: 0.12, blue: 0.45)
}

struct CardView: View {
    let card: PlayCard
    var onTap: ((Int) -> Void)? = nil

    var body: some View {
        GeometryReader { geo in
            let d = min(geo.size.width, geo.size.height)
            ZStack {
                Circle()
                    .fill(.white)
                    .shadow(color: .black.opacity(0.25), radius: d * 0.04, y: d * 0.02)
                Circle()
                    .strokeBorder(Theme.ink.opacity(0.12), lineWidth: max(2, d * 0.012))
                ForEach(card.slots) { slot in
                    Text(Deck.symbols[slot.symbol])
                        .font(.system(size: d * slot.size))
                        .rotationEffect(.degrees(slot.rotation))
                        .frame(width: d * slot.size * 1.45, height: d * slot.size * 1.45)
                        .contentShape(Circle())
                        .onTapGesture { onTap?(slot.symbol) }
                        .allowsHitTesting(onTap != nil)
                        .offset(x: d * slot.x, y: d * slot.y)
                        .accessibilityLabel(Text(Deck.symbols[slot.symbol]))
                        .accessibilityAddTraits(onTap != nil ? .isButton : [])
                }
            }
            .frame(width: d, height: d)
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

struct Shake: GeometryEffect {
    var animatableData: CGFloat
    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(CGAffineTransform(translationX: 12 * sin(animatableData * .pi * 4), y: 0))
    }
}

struct BigButton: View {
    let title: String
    var systemImage: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title)
            }
            .font(.title3.weight(.bold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .foregroundStyle(Theme.ink)
        }
        .buttonStyle(.plain)
    }
}

struct MenuButton: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: systemImage)
                    .font(.title2.weight(.semibold))
                    .frame(width: 48, height: 48)
                    .background(Theme.ink.opacity(0.1), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.title3.weight(.bold))
                    Text(subtitle).font(.subheadline).opacity(0.7)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.headline).opacity(0.5)
            }
            .padding(14)
            .background(.white, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .foregroundStyle(Theme.ink)
        }
        .buttonStyle(.plain)
    }
}

struct StatPill: View {
    let systemImage: String
    let text: String

    var body: some View {
        Label(text, systemImage: systemImage)
            .font(.headline.monospacedDigit())
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(.white.opacity(0.18), in: Capsule())
    }
}

struct CloseButton: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.headline.weight(.bold))
                .frame(width: 40, height: 40)
                .background(.white.opacity(0.18), in: Circle())
                .foregroundStyle(.white)
        }
        .accessibilityLabel("Close")
    }
}
