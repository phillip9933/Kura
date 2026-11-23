import Foundation
import SwiftUI

enum WalletUpcoming {
    static func date(for card: WalletCard, now: Date = Date(), timeZone: TimeZone = .current) -> Date? {
        guard card.section == .passes, !card.archived else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        // The manual expiry picker stores a calendar day. Imported pass timestamps retain their exact instant.
        let expiry = card.expires.map { card.passStyle == nil ? endOfDay($0, calendar: calendar) : $0 }
        if let expiry, expiry < now { return nil }
        let scheduled = card.passStyle?.relevantDate ?? [
            "Relevant date", "relevantDate", "Event date", "eventDate"
        ].compactMap { card.fields[$0] }.compactMap { parse($0, calendar: calendar) }.first
        guard let date = scheduled ?? expiry, date >= now else { return nil }
        return date
    }

    private static func endOfDay(_ date: Date, calendar: Calendar) -> Date {
        guard let end = calendar.dateInterval(of: .day, for: date)?.end else { return date }
        return Date(timeIntervalSinceReferenceDate: end.timeIntervalSinceReferenceDate.nextDown)
    }

    private static func parse(_ raw: String, calendar: Calendar) -> Date? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        if let exact = PassImport.date(text) { return exact }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.isLenient = false
        for format in ["yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS"] {
            formatter.dateFormat = format
            if let date = formatter.date(from: text), formatter.string(from: date) == text {
                return format == "yyyy-MM-dd" ? endOfDay(date, calendar: calendar) : date
            }
        }
        return nil
    }
}

struct WalletTileColor: Equatable {
    var red: Double, green: Double, blue: Double, alpha = 1.0

    static let base = Self(red: 23 / 255, green: 23 / 255, blue: 23 / 255)
    static let black = Self(red: 0, green: 0, blue: 0)
    static let white = Self(red: 1, green: 1, blue: 1)
    var color: Color { Color(red: red, green: green, blue: blue, opacity: alpha) }

    static func parse(_ raw: String?) -> Self? {
        guard let text = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else { return nil }
        if text.hasPrefix("#") || text.hasPrefix("0x") {
            let hex = text.dropFirst(text.hasPrefix("#") ? 1 : 2)
            guard [6, 8].contains(hex.count), hex.allSatisfy(\.isHexDigit), let value = UInt32(hex, radix: 16) else { return nil }
            func channel(_ shift: Int) -> Double { Double((value >> shift) & 255) / 255 }
            return Self(red: channel(16), green: channel(8), blue: channel(0), alpha: hex.count == 8 ? channel(24) : 1)
        }
        let hasAlpha = text.hasPrefix("rgba(")
        guard (hasAlpha || text.hasPrefix("rgb(")), text.hasSuffix(")") else { return nil }
        let parts = text.dropFirst(hasAlpha ? 5 : 4).dropLast().split(separator: ",", omittingEmptySubsequences: false)
        let values = parts.compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard parts.count == (hasAlpha ? 4 : 3), parts.count == values.count,
              values.prefix(3).allSatisfy({ (0...255).contains($0) }),
              !hasAlpha || (0...1).contains(values[3]) else { return nil }
        return Self(red: values[0] / 255, green: values[1] / 255, blue: values[2] / 255, alpha: hasAlpha ? values[3] : 1)
    }

    func over(_ background: Self) -> Self {
        Self(red: red * alpha + background.red * (1 - alpha), green: green * alpha + background.green * (1 - alpha),
             blue: blue * alpha + background.blue * (1 - alpha))
    }

    func contrast(with other: Self) -> Double {
        func luminance(_ c: Self) -> Double {
            func linear(_ x: Double) -> Double { x <= 0.04045 ? x / 12.92 : pow((x + 0.055) / 1.055, 2.4) }
            return 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
        }
        let a = luminance(self), b = luminance(other)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }
}

struct WalletTileStyle {
    let background: WalletTileColor
    let foreground: WalletTileColor
    let label: WalletTileColor

    init(card: WalletCard) {
        let pass = card.passStyle
        let colorKeys = card.section == .passes ? ["Background color", "backgroundColor", "Card color", "color"] :
            ["Card color", "color", "Background color", "backgroundColor"]
        let base = WalletTileColor.parse(card.tileField(colorKeys) ?? pass?.backgroundColor)?.over(.base) ?? .base
        let ink: WalletTileColor = base.contrast(with: .white) >= base.contrast(with: .black) ? .white : .black
        func legible(_ raw: String?, fallback: WalletTileColor) -> WalletTileColor {
            guard let value = WalletTileColor.parse(raw)?.over(base), base.contrast(with: value) >= 4.5 else { return fallback }
            return value
        }
        background = base
        foreground = legible(card.tileField(["Text color", "Foreground color", "foregroundColor"]) ?? pass?.foregroundColor, fallback: ink)
        label = legible(card.tileField(["Label color", "labelColor"]) ?? pass?.labelColor, fallback: foreground)
    }
}

private extension WalletCard {
    func tileField(_ keys: [String]) -> String? {
        for key in keys {
            if let text = fields[key]?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty { return text }
        }
        return nil
    }
}

struct WalletTileDetail: Equatable {
    let label: String
    let value: String

    static func payment(for card: WalletCard, currency: String, locale: Locale = .current) -> [Self] {
        guard card.section == .cards else { return [] }
        var details: [Self] = []
        for (label, keys) in [("Balance", ["Balance", "balance"]), ("Credit limit", ["Credit limit", "maxlimit"])] {
            if let raw = card.tileField(keys) {
                let decimal = FieldDefinition(name: label, kind: .currency).accepts(raw)
                    ? Decimal(string: raw, locale: Locale(identifier: "en_US_POSIX")) : nil
                let value = decimal.map { $0.formatted(.currency(code: currency).locale(locale).rounded(rule: .toNearestOrAwayFromZero)) } ?? raw
                details.append(Self(label: label, value: value))
            }
        }
        if let raw = card.tileField(["Billing day", "Billing day of month", "billdate"]), let day = Int(raw), (1...31).contains(day) {
            let ordinals = NumberFormatter()
            ordinals.locale = locale
            ordinals.numberStyle = .ordinal
            details.append(Self(label: "Billing day", value: ordinals.string(from: NSNumber(value: day)) ?? raw))
        }
        return details
    }

    static func maskedNumber(for card: WalletCard) -> String? {
        let keys = card.section == .identities ? ["Document number", "value"] : ["Number", "Card number", "Member number", "Borrower number", "number"]
        guard let number = card.tileField(keys) else { return nil }
        let suffix = number.filter { !$0.isWhitespace && $0 != "-" }.suffix(4)
        return suffix.isEmpty ? nil : "•••• " + suffix
    }
}

struct WalletTile: View {
    let card: WalletCard
    let showFront: Bool
    let currency: String

    private var heading: String {
        if card.section == .cards, let network = card.tileField(["Network", "network"]) { return network }
        return card.category.isEmpty ? card.section.rawValue : card.category
    }

    var body: some View {
        let style = WalletTileStyle(card: card)
        let front = showFront ? card.media.front : nil
        VStack(alignment: .leading, spacing: 6) {
            Group {
                if let front {
                    MediaThumbnail(data: front, maximumPixels: 720)
                        .frame(maxWidth: .infinity).aspectRatio(1.586, contentMode: .fit)
                        .accessibilityHidden(true)
                } else {
                    artwork(style)
                }
            }
            .foregroundStyle(style.foreground.color)
            .background(style.background.color)
            .clipShape(RoundedRectangle(cornerRadius: 13))
            if front != nil { Text(card.name).font(.headline).lineLimit(2) }
            if card.section == .passes, let organization = card.tileField(["Organization"]) ?? card.passStyle?.organization,
               !organization.isEmpty, organization != card.name {
                Text(organization).font(.caption).foregroundStyle(.secondary)
            }
            ForEach(WalletTileDetail.payment(for: card, currency: currency), id: \.label) { detail in
                Text("\(detail.label): \(detail.value)").font(.caption).foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func artwork(_ style: WalletTileStyle) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack {
                if let logo = card.media.logo {
                    MediaThumbnail(data: logo).frame(width: 26, height: 26).accessibilityHidden(true)
                } else { Image(systemName: card.section.icon).accessibilityHidden(true) }
                Text(heading).font(.caption.weight(.semibold))
            }.foregroundStyle(style.label.color)
            Text(card.name).font(.headline).lineLimit(2)
            if card.section == .passes {
                let primary = card.passStyle?.groups["primaryFields"] ?? []
                if primary.isEmpty, !card.payload.isEmpty {
                    BarcodePreview(payload: card.payload, format: card.format)
                        .frame(maxWidth: .infinity, maxHeight: 52).accessibilityHidden(true)
                } else {
                    ForEach(Array(primary.prefix(2).enumerated()), id: \.offset) { _, field in
                        if !field.label.isEmpty { Text(field.label).font(.caption2).foregroundStyle(style.label.color) }
                        Text(field.displayValue).font(.subheadline.weight(.medium)).lineLimit(2)
                    }
                }
            } else {
                if let masked = WalletTileDetail.maskedNumber(for: card) {
                    Text(masked).font(.subheadline.monospacedDigit())
                        .accessibilityLabel(Text("Ending in \(masked.dropFirst(5))"))
                }
                if card.section == .cards, let expiry = card.expires {
                    Text(expiry, format: .dateTime.month(.twoDigits).year(.twoDigits)).font(.caption)
                        .accessibilityLabel(Text("Expires \(expiry.formatted(.dateTime.month(.wide).year()))"))
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: 104, alignment: .topLeading)
    }
}
