import SwiftUI

struct WalletNavigation: Codable, Equatable {
    var visible = WalletSection.allCases
    var showSectionBar = true
    var controlsBelow = true
    var swipe = true
    var showSearch = true
    var searchButton = false
    var searchBelow = false
    var showUpcoming = true
    var frontImage: [String: Bool] = [:]
    var gridColumns = 2
    var sectionColumns: [String: Int]?

    func columns(for section: WalletSection) -> Int { sectionColumns?[section.rawValue] ?? gridColumns }
}

struct NavigationSettings: View {
    @Binding var prefs: WalletPreferences
    var body: some View {
        Form {
            Section("Visible sections") {
                ForEach(WalletSection.allCases) { item in
                    Toggle(item.rawValue, isOn: Binding(get: { prefs.layout.visible.contains(item) }, set: { enabled in
                        var visible = Set(prefs.layout.visible)
                        if enabled { visible.insert(item) } else if visible.count > 1 { visible.remove(item) }
                        prefs.layout.visible = WalletSection.allCases.filter(visible.contains)
                        if !visible.contains(prefs.startSection) { prefs.startSection = prefs.layout.visible[0] }
                    })).disabled(prefs.layout.visible == [item])
                }
            }
            Section("Navigation") {
                Toggle("Show section bar", isOn: $prefs.layout.showSectionBar)
                Toggle("Swipe between sections", isOn: $prefs.layout.swipe)
                Toggle("Controls at bottom", isOn: $prefs.layout.controlsBelow)
                Stepper("Grid columns: \(prefs.layout.gridColumns)", value: $prefs.layout.gridColumns, in: 1...3)
            }
            Section("Search") {
                Toggle("Enable search", isOn: $prefs.layout.showSearch)
                if prefs.layout.showSearch {
                    Toggle("Open search with a button", isOn: $prefs.layout.searchButton)
                    Toggle("Search at bottom", isOn: $prefs.layout.searchBelow)
                }
            }
        }.navigationTitle("Navigation and layout")
    }
}

struct CategoryChange {
    let section: WalletSection
    let old: String
    let new: String
}

struct WalletArchive: View {
    @ObservedObject var wallet: WalletSession
    @State private var query = ""
    @State private var order = "Name"
    @State private var reverse = false
    @State private var removal: [WalletCard] = []
    @State private var confirming = false
    @State private var error: String?
    private var archive: [WalletCard] { wallet.cards.filter(\.archived) }
    private var rows: [WalletCard] {
        let sorted = archive.filter { card in
            query.isEmpty || [card.name, card.category, card.section.rawValue].contains { $0.localizedCaseInsensitiveContains(query) }
        }.sorted { a, b in
            if order == "Section", a.section != b.section { return a.section.rawValue < b.section.rawValue }
            if order == "Expiry", a.expires != b.expires { return (a.expires ?? .distantFuture) < (b.expires ?? .distantFuture) }
            return a.name.localizedStandardCompare(b.name) == .orderedAscending
        }
        return reverse ? Array(sorted.reversed()) : sorted
    }

    var body: some View {
        List {
            if let error { Text(error) }
            if rows.isEmpty { Text(query.isEmpty ? "No archived items" : "No matching items") }
            ForEach(rows) { card in
                NavigationLink(value: WalletRoute.item(card.id)) {
                    VStack(alignment: .leading) {
                        Text(card.name)
                        Text([card.section.rawValue, card.category].filter { !$0.isEmpty }.joined(separator: " · "))
                            .font(.caption)
                    }
                }
            }
        }
        .searchable(text: $query, prompt: "Search archive")
        .navigationTitle("Archive")
        .toolbar {
            Menu {
                Picker("Sort by", selection: $order) {
                    ForEach(["Name", "Section", "Expiry"], id: \.self) { Text($0) }
                }
                Toggle("Reverse order", isOn: $reverse)
                Button("Delete all archived items", role: .destructive) { removal = archive; confirming = true }
                    .disabled(archive.isEmpty || wallet.saving)
            } label: { Label("Archive options", systemImage: "ellipsis.circle") }
        }
        .confirmationDialog("Delete all \(removal.count) archived items?", isPresented: $confirming, titleVisibility: .visible) {
            Button("Delete archived items", role: .destructive) {
                let token = wallet.generation
                Task {
                    do { try await wallet.deleteArchived(removal, generation: token) }
                    catch is CancellationError {} catch { self.error = "The archive changed. Review it and try again." }
                    removal = []
                }
            }
        }
    }
}
