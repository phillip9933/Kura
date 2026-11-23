import CryptoKit
import Foundation

struct PassField: Codable, Equatable {
    let key: String
    let label: String
    let value: String
    var numberValue: Bool?
    var currencyCode: String?
    var numberStyle: String?
    var dateStyle: String?
    var timeStyle: String?

    var displayValue: String {
        if let currencyCode, let number = Decimal(string: value, locale: Locale(identifier: "en_US_POSIX")) {
            let formatter = NumberFormatter()
            formatter.numberStyle = .currency
            formatter.currencyCode = currencyCode
            return formatter.string(from: NSDecimalNumber(decimal: number)) ?? value
        }
        if let numberStyle, let number = Decimal(string: value, locale: Locale(identifier: "en_US_POSIX")) {
            let formatter = NumberFormatter()
            let styles: [String: NumberFormatter.Style] = ["PKNumberStylePercent": .percent, "PKNumberStyleDecimal": .decimal,
                                                           "PKNumberStyleSpellOut": .spellOut, "PKNumberStyleScientific": .scientific]
            formatter.numberStyle = styles[numberStyle] ?? .decimal
            return formatter.string(from: NSDecimalNumber(decimal: number)) ?? value
        }
        if (dateStyle != nil || timeStyle != nil), let date = PassImport.date(value) {
            let styles: [String: DateFormatter.Style] = ["PKDateStyleShort": .short, "PKDateStyleMedium": .medium,
                                                         "PKDateStyleLong": .long, "PKDateStyleFull": .full]
            let formatter = DateFormatter()
            formatter.dateStyle = styles[dateStyle ?? ""] ?? .none
            formatter.timeStyle = styles[timeStyle ?? ""] ?? .none
            let result = formatter.string(from: date)
            if !result.isEmpty { return result }
        }
        return value
    }

    var passJSON: [String: Any] {
        var result: [String: Any] = ["key": key, "label": label, "value": value]
        if numberValue == true, let decimal = Decimal(string: value, locale: Locale(identifier: "en_US_POSIX")) {
            result["value"] = NSDecimalNumber(decimal: decimal)
        }
        result["currencyCode"] = currencyCode
        result["numberStyle"] = numberStyle
        result["dateStyle"] = dateStyle
        result["timeStyle"] = timeStyle
        return result
    }
}

struct PassAppearance: Codable, Equatable {
    var type: String
    var logoText: String
    var organization: String
    var transitType: String
    var serialNumber: String
    var relevantDate: Date?
    var backgroundColor: String?
    var foregroundColor: String?
    var labelColor: String?
    var groups: [String: [PassField]]
    var artwork: [String: Data]

    static let groupNames = ["headerFields", "primaryFields", "secondaryFields", "auxiliaryFields", "backFields"]
    static let types = ["boardingPass", "eventTicket", "coupon", "storeCard", "generic"]
    static let artNames = ["strip", "thumbnail", "footer", "icon"]

    func validate() throws {
        guard Self.types.contains(type), groups.keys.allSatisfy(Self.groupNames.contains),
              [logoText, organization, transitType, serialNumber].allSatisfy({ $0.utf8.count <= 8192 }),
              [backgroundColor, foregroundColor, labelColor].allSatisfy({ ($0?.utf8.count ?? 0) <= 128 }),
              groups.values.allSatisfy({ $0.count <= 100 }),
              groups.values.joined().allSatisfy({ field in
                  field.key.utf8.count <= 160 && field.label.utf8.count <= 160 && field.value.utf8.count <= 8192
              }),
              artwork.keys.allSatisfy(Self.artNames.contains),
              artwork.values.reduce(0, { $0 + $1.count }) <= PassArchive.expandedLimit
        else { throw WalletMediaError.damagedPass }
        for bytes in artwork.values { _ = try WalletMedia.imageSource(bytes) }
    }
}

struct ImportedPass: Identifiable {
    let id = UUID()
    let name: String
    let category: String
    let section: WalletSection
    let payload: String
    let barcodeFormat: BarcodeFormat
    let fields: [String: String]
    let media: WalletMedia
    let expires: Date?
    let appearance: PassAppearance
    let manifestChecked: Bool
    var organization: String { appearance.organization }
    var serialNumber: String { appearance.serialNumber }
    var format: String { barcodeFormat.rawValue }

    func makeCard() throws -> WalletCard {
        try WalletCard(id: id, name: name, payload: payload, section: section, format: barcodeFormat,
                       category: category, fields: fields, expires: expires, media: media, passStyle: appearance)
    }
}

enum PassImport {
    static func read(_ url: URL) async throws -> [ImportedPass] {
        try parse(WalletMedia.read(url, limit: PassArchive.inputLimit))
    }

    static func parse(_ data: Data, languages: [String] = Locale.preferredLanguages) throws -> [ImportedPass] {
        var budget = PassArchive.Budget()
        return try parse(data, depth: 0, budget: &budget, languages: languages)
    }

    private static func parse(_ data: Data, depth: Int, budget: inout PassArchive.Budget,
                              languages: [String]) throws -> [ImportedPass] {
        guard depth <= 3 else { throw WalletMediaError.damagedPass }
        let files = try PassArchive.unpack(data, budget: &budget)
        let roots = files.keys.filter { $0 == "pass.json" || $0.hasSuffix("/pass.json") }
        let bundles = files.keys.filter { path in
            let name = path.lowercased()
            return name.hasSuffix(".pkpass") || name.hasSuffix(".pkpasses")
        }.sorted()
        if roots.isEmpty {
            guard !bundles.isEmpty else { throw WalletMediaError.damagedPass }
            var passes: [ImportedPass] = []
            for path in bundles {
                guard let nested = files[path] else { throw WalletMediaError.damagedPass }
                passes += try parse(nested, depth: depth + 1, budget: &budget, languages: languages)
            }
            return passes
        }
        guard roots.count == 1, let root = roots.first, bundles.isEmpty else { throw WalletMediaError.damagedPass }
        let prefix = String(root.dropLast("pass.json".count))
        var part: [String: Data] = [:]
        for (path, bytes) in files {
            guard path.hasPrefix(prefix) else { throw WalletMediaError.damagedPass }
            part[String(path.dropFirst(prefix.count))] = bytes
        }
        return [try decode(part, languages: languages)]
    }

    private static func json(_ data: Data) throws -> [String: Any] {
        guard data.count <= PassArchive.jsonLimit else { throw WalletMediaError.size }
        var depth = 0, quoted = false, escaped = false
        for byte in data {
            if quoted {
                if escaped { escaped = false }
                else if byte == 0x5c { escaped = true }
                else if byte == 0x22 { quoted = false }
            } else {
                switch byte {
                case 0x22: quoted = true
                case 0x5b, 0x7b: depth += 1
                case 0x5d, 0x7d: depth -= 1
                default: break
                }
                guard (0...32).contains(depth) else { throw WalletMediaError.damagedPass }
            }
        }
        guard depth == 0, !quoted, let value = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { throw WalletMediaError.damagedPass }
        return value
    }

    static func manifestDigest(_ bytes: Data) -> String {
        // Apple pass manifests prescribe SHA-1. This verifies file integrity only.
        Insecure.SHA1.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
    }

    static func date(_ value: Any?) -> Date? {
        guard let text = value as? String else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: text) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: text)
    }

    private static func decode(_ files: [String: Data], languages: [String]) throws -> ImportedPass {
        guard let descriptor = files["pass.json"] else { throw WalletMediaError.damagedPass }
        if let bytes = files["manifest.json"] {
            guard let manifest = try json(bytes) as? [String: String],
                  Set(manifest.keys) == Set(files.keys).subtracting(["manifest.json", "signature"])
            else { throw WalletMediaError.damagedPass }
            for (key, digest) in manifest {
                guard let input = files[key], manifestDigest(input) == digest.lowercased()
                else { throw WalletMediaError.damagedPass }
            }
        }
        let pass = try json(descriptor)
        guard pass["formatVersion"] as? Int == 1 else { throw WalletMediaError.damagedPass }
        let types = PassAppearance.types.filter { pass[$0] != nil }
        guard types.count == 1, let type = types.first, let body = pass[type] as? [String: Any]
        else { throw WalletMediaError.damagedPass }

        let choices = languages.flatMap { [$0, String($0.prefix(while: { $0 != "-" }))] } + ["en", "Base"]
        let localized = choices.compactMap { files[$0 + ".lproj/pass.strings"] }.first
        let strings: [String: String]
        if let localized {
            guard let values = try PropertyListSerialization.propertyList(from: localized, options: [], format: nil) as? [String: String]
            else { throw WalletMediaError.damagedPass }
            strings = values
        } else { strings = [:] }
        func local(_ value: Any?) -> String {
            if let text = value as? String { return strings[text] ?? text }
            return (value as? NSNumber)?.stringValue ?? ""
        }
        func asset(_ name: String) -> Data? {
            let names = ["@3x.png", "@2x.png", ".png"].map { name + $0 }
            for language in choices {
                if let image = names.compactMap({ files[language + ".lproj/" + $0] }).first { return image }
            }
            return names.compactMap { files[$0] }.first
        }

        let organization = local(pass["organizationName"])
        let title = [local(pass["description"]), local(pass["logoText"]), organization].first { !$0.isEmpty }
        guard let title else { throw WalletMediaError.damagedPass }
        var groups: [String: [PassField]] = [:]
        for group in PassAppearance.groupNames {
            guard body[group] != nil else { continue }
            guard let values = body[group] as? [[String: Any]], values.count <= 100 else { throw WalletMediaError.damagedPass }
            groups[group] = values.map {
                PassField(key: $0["key"] as? String ?? "", label: local($0["label"]), value: local($0["value"]),
                          numberValue: $0["value"] is NSNumber, currencyCode: $0["currencyCode"] as? String,
                          numberStyle: $0["numberStyle"] as? String, dateStyle: $0["dateStyle"] as? String,
                          timeStyle: $0["timeStyle"] as? String)
            }
        }
        let extensionData = (pass["userInfo"] as? [String: Any])?["app.kura.wallet"] as? [String: Any] ?? [:]
        let fields = extensionData["customFields"] as? [String: String] ?? [:]
        let fallback: [String: String] = ["boardingPass": "Boarding Pass", "eventTicket": "Event", "coupon": "Coupon", "storeCard": "Loyalty"]
        let section: WalletSection
        switch extensionData["section"] as? String {
        case "wallets": section = .cards
        case "identities": section = .identities
        case "passes": section = .passes
        default: section = type == "storeCard" ? .cards : .passes
        }
        let codes = (pass["barcodes"] as? [[String: Any]]) ?? (pass["barcode"] as? [String: Any]).map { [$0] } ?? []
        guard codes.count <= 32 else { throw WalletMediaError.damagedPass }
        let code = codes.compactMap { item -> (BarcodeFormat, String)? in
            guard let name = item["format"] as? String, let format = BarcodeFormat(passKitName: name) ?? BarcodeFormat(rawValue: name),
                  let message = item["message"] as? String else { return nil }
            return (format, message)
        }.first
        if !codes.isEmpty && code == nil { throw WalletMediaError.damagedPass }

        var media = WalletMedia()
        try media.replace(.logo, with: asset("logo"))
        try media.replace(.front, with: asset("background"))
        var art: [String: Data] = [:]
        for name in PassAppearance.artNames { art[name] = asset(name) }
        let appearance = PassAppearance(type: type, logoText: local(pass["logoText"]), organization: organization,
            transitType: body["transitType"] as? String ?? "", serialNumber: pass["serialNumber"] as? String ?? "",
            relevantDate: Self.date(pass["relevantDate"]), backgroundColor: pass["backgroundColor"] as? String, foregroundColor: pass["foregroundColor"] as? String,
            labelColor: pass["labelColor"] as? String, groups: groups, artwork: art)
        try appearance.validate()
        let item = ImportedPass(name: title, category: extensionData["category"] as? String ?? fallback[type] ?? "Other",
            section: section, payload: code?.1 ?? "", barcodeFormat: code?.0 ?? .qr, fields: fields, media: media,
            expires: Self.date(pass["expirationDate"]),
            appearance: appearance, manifestChecked: files["manifest.json"] != nil)
        _ = try item.makeCard()
        return item
    }
}
