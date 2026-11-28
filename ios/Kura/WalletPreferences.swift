import Foundation

enum FieldKind: String, Codable, CaseIterable {
    case text = "Text", number = "Number", currency = "Currency", date = "Date", email = "Email", phone = "Phone", url = "Link"
}

struct FieldDefinition: Codable, Equatable, Identifiable {
    var name: String
    var kind: FieldKind
    var dateFormat: WalletDateFormat?
    var id: String { name }
}

struct WalletPreferences: Codable, Equatable {
    private var navigation: WalletNavigation?
    var layout: WalletNavigation {
        get { navigation ?? WalletNavigation() }
        set { navigation = newValue }
    }
    init() {}

    var startSection = WalletSection.cards
    var appearance = "System"
    var favoritesFirst = false
    var useGrid = false
    var expiryAlert = true
    var showExpiry = true
    var expiryDays = 30
    var currency = "USD"
    var flipped = false
    var brightness = false
    var lockDelay = 0
    var categories: [String: [String]] = [:]
    var definitions: [String: [FieldDefinition]] = [:]
    var sorting: [String: WalletSort] = [:]

    func hasSameSettings(as other: Self) -> Bool {
        // Older wallets omit the default navigation settings.
        var lhs = self
        var rhs = other
        lhs.navigation = layout
        rhs.navigation = other.layout
        return lhs == rhs
    }

    func categories(for section: WalletSection) -> [String] { categories[section.rawValue] ?? section.categories }
    func definitions(for section: WalletSection) -> [FieldDefinition] { definitions[section.rawValue] ?? [] }

    func validate() throws {
        guard ["System", "Light", "Dark"].contains(appearance), (0...365).contains(expiryDays),
              (0...300).contains(lockDelay), currency.utf8.count == 3, currency.utf8.allSatisfy({ (65...90).contains($0) }),
              Set(Array(categories.keys) + Array(definitions.keys) + Array(sorting.keys)).isSubset(of: Set(WalletSection.allCases.map(\.rawValue)))
        else { throw WalletError.invalidFields }
        guard !layout.visible.isEmpty, Set(layout.visible).count == layout.visible.count,
              layout.visible.contains(startSection), (1...3).contains(layout.gridColumns)
        else { throw WalletError.invalidFields }
        guard (layout.sectionColumns ?? [:]).allSatisfy({ WalletSection(rawValue: $0.key) != nil && (1...3).contains($0.value) }) else { throw WalletError.invalidFields }
        for section in WalletSection.allCases {
            for names in [categories(for: section), definitions(for: section).map(\.name)] {
                guard names.count <= 128, Set(names.map { $0.lowercased() }).count == names.count,
                      names.allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.utf8.count <= 160 })
                else { throw WalletError.invalidFields }
            }
        }
    }
}

struct WalletSnapshot: Codable, Equatable {
    var cards: [WalletCard] = []
    var preferences = WalletPreferences()

    func validate() throws {
        guard cards.count <= 10_000, Set(cards.map(\.id)).count == cards.count else { throw WalletError.vaultUnavailable }
        try preferences.validate()
        for card in cards { try card.validate() }
    }

    func visible(section: WalletSection, archived: Bool, favoritesOnly: Bool, category: String?, query: String) -> [WalletCard] {
        let sort = preferences.sorting[section.rawValue] ?? .custom
        let filtered = cards.filter { item in
            item.section == section && item.archived == archived && (!favoritesOnly || item.favorite)
                && (category == nil || item.category == category)
                && (query.isEmpty || ([item.name, item.category, item.notes, item.payload] + Array(item.fields.values))
                    .contains { $0.localizedCaseInsensitiveContains(query) })
        }
        return filtered.sorted { a, b in
            if preferences.favoritesFirst, a.favorite != b.favorite { return a.favorite }
            switch sort {
            case .custom: return false
            case .newest: return a.created > b.created
            case .oldest: return a.created < b.created
            case .ascending: return a.name.localizedStandardCompare(b.name) == .orderedAscending
            case .descending: return a.name.localizedStandardCompare(b.name) == .orderedDescending
            }
        }
    }
}
