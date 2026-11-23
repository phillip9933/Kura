import SwiftUI

struct WalletNavigation: Codable, Equatable {
    var visible = WalletSection.allCases
    var showSectionBar = true
    var controlsBelow = false
    var swipe = true
    var showSearch = true
    var searchButton = false
    var searchBelow = false
    var showUpcoming = true
    var frontImage: [String: Bool] = [:]
    var gridColumns = 2
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
    var body: some View {
        List {
            if !wallet.cards.contains(where: { $0.archived }) { Text("No archived items") }
            ForEach(WalletSection.allCases) { section in
                let rows = wallet.cards.filter { $0.archived && $0.section == section }
                if !rows.isEmpty {
                    Section(section.rawValue) {
                        ForEach(rows) { card in NavigationLink(card.name, value: WalletRoute.item(card.id)) }
                    }
                }
            }
        }.navigationTitle("Archive")
    }
}
