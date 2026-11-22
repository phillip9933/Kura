import SwiftUI

enum WalletRoute: Hashable {
    case item(UUID), edit(UUID), add(WalletSection), settings, importPass, receive, reorder(WalletSection)
}

struct CardsView: View {
    @ObservedObject var wallet: WalletSession
    @State private var path: [WalletRoute] = []
    @State private var section: WalletSection
    @State private var query = ""
    @State private var archived = false
    @State private var favorites = false
    @State private var category: String?
    @State private var message: String?
    @State private var checkedExpiry = false
    private let generation: UUID

    init(wallet: WalletSession) {
        self.wallet = wallet
        generation = wallet.generation
        _section = State(initialValue: wallet.snapshot.preferences.startSection)
    }

    private var visible: [WalletCard] {
        wallet.snapshot.visible(section: section, archived: archived, favoritesOnly: favorites, category: category, query: query)
    }

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                Picker("Wallet section", selection: $section) {
                    ForEach(WalletSection.allCases) { Text($0.rawValue).tag($0) }
                }.pickerStyle(.segmented).padding(.horizontal)
                if archived || favorites || category != nil {
                    Button([archived ? "Archived" : nil, favorites ? "Favorites" : nil, category, "Clear filters"]
                        .compactMap { $0 }.joined(separator: " · ")) { clearFilters() }
                        .padding(.vertical, 6)
                }
                if visible.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: section.icon).font(.largeTitle).accessibilityHidden(true)
                        Text(query.isEmpty && !archived && !favorites && category == nil ? "No items yet" : "No matching items").font(.headline)
                        Button("Add item") { path.append(.add(section)) }
                    }.frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if wallet.snapshot.preferences.useGrid {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 145))], alignment: .leading) {
                            ForEach(visible) { card in
                                NavigationLink(value: WalletRoute.item(card.id)) {
                                    row(card).frame(maxWidth: .infinity, minHeight: 85, alignment: .leading)
                                        .padding().background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                                }.buttonStyle(.plain)
                            }
                        }.padding()
                    }
                } else {
                    List(visible) { card in
                        NavigationLink(value: WalletRoute.item(card.id)) { row(card) }
                    }
                }
            }
            .navigationTitle(section.rawValue)
            .onAppear {
                guard !checkedExpiry else { return }
                checkedExpiry = true
                let prefs = wallet.snapshot.preferences
                guard prefs.expiryAlert, let threshold = Calendar.current.date(byAdding: .day, value: prefs.expiryDays, to: Date()) else { return }
                let expiring = wallet.cards.filter { !$0.archived && ($0.expires ?? .distantFuture) <= threshold }
                if !expiring.isEmpty {
                    message = "\(expiring.count) item(s) expired or expiring soon.\n" + expiring.prefix(5).map(\.name).joined(separator: "\n")
                }
            }
            .searchable(text: $query, prompt: "Search this section")
            .onChange(of: section) { _ in clearFilters(); query = "" }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) { Button("Lock", action: wallet.lock) }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Menu {
                        Button("Add item") { path.append(.add(section)) }
                        Button("Import passes") { path.append(.importPass) }
                        Button("Receive secure transfer") { path.append(.receive) }
                    } label: { Label("Add", systemImage: "plus") }
                    Menu {
                        Toggle("Show archived", isOn: $archived)
                        Toggle("Favorites only", isOn: $favorites)
                        Picker("Category", selection: $category) {
                            Text("All categories").tag(nil as String?)
                            let names = Set(wallet.snapshot.preferences.categories(for: section) + wallet.cards.filter { $0.section == section }.map(\.category))
                            ForEach(names.filter { !$0.isEmpty }.sorted(), id: \.self) { Text($0).tag(Optional($0)) }
                        }
                        Menu("Sort") {
                            ForEach(WalletSort.allCases, id: \.self) { sort in
                                Button { setSort(sort) } label: {
                                    if (wallet.snapshot.preferences.sorting[section.rawValue] ?? .custom) == sort {
                                        Label(sort.rawValue, systemImage: "checkmark")
                                    } else { Text(sort.rawValue) }
                                }
                            }
                            Button("Reorder items") { path.append(.reorder(section)) }
                        }
                        Button("Settings") { path.append(.settings) }
                    } label: { Label("Options", systemImage: "ellipsis.circle") }
                }
            }
            .navigationDestination(for: WalletRoute.self) { route in
                switch route {
                case .add(let target): WalletEditor(wallet: wallet, section: target, original: nil)
                case .edit(let id):
                    if let card = wallet.cards.first(where: { $0.id == id }) { WalletEditor(wallet: wallet, section: card.section, original: card) }
                case .item(let id): WalletDetail(wallet: wallet, id: id) { path.append(.edit(id)) }
                case .settings: WalletSettings(wallet: wallet)
                case .reorder(let target): WalletOrderView(wallet: wallet, section: target)
                case .receive: BackupTransferReceiveView(wallet: wallet)
                case .importPass: PassImportView { passes in
                    let cards = try passes.map { try $0.makeCard() }
                    try await wallet.importCards(cards, generation: generation)
                    path.removeAll()
                    section = .passes
                }
                }
            }
            .alert("Wallet", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("OK") { message = nil }
            } message: { Text(message ?? "") }
        }
    }

    private func row(_ card: WalletCard) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(card.name).font(.headline)
                if card.favorite { Image(systemName: "star.fill").accessibilityLabel("Favorite") }
            }
            if !card.category.isEmpty { Text(card.category).font(.subheadline).foregroundStyle(.secondary) }
            if wallet.snapshot.preferences.showExpiry, let end = card.expires,
               let threshold = Calendar.current.date(byAdding: .day, value: wallet.snapshot.preferences.expiryDays, to: Date()), end <= threshold {
                Text("Expires \(end.formatted(date: .abbreviated, time: .omitted))").font(.caption)
            }
        }
    }

    private func clearFilters() { archived = false; favorites = false; category = nil }
    private func setSort(_ mode: WalletSort) {
        var prefs = wallet.snapshot.preferences
        prefs.sorting[section.rawValue] = mode
        Task {
            do { try await wallet.preferences(prefs, generation: generation) }
            catch is CancellationError {}
            catch { message = "Kura could not save the sort order." }
        }
    }
}

private struct WalletOrderView: View {
    @ObservedObject var wallet: WalletSession
    @Environment(\.dismiss) private var dismiss
    @State private var items: [WalletCard]
    @State private var error: String?
    private let token: UUID
    private let section: WalletSection
    init(wallet: WalletSession, section: WalletSection) {
        self.wallet = wallet
        self.section = section
        token = wallet.generation
        _items = State(initialValue: wallet.cards.filter { $0.section == section && !$0.archived })
    }
    var body: some View {
        List {
            if let error { Text(error) }
            ForEach(items) { Text($0.name) }.onMove { items.move(fromOffsets: $0, toOffset: $1) }
        }.environment(\.editMode, .constant(.active)).navigationTitle("Reorder")
        .toolbar {
            Button("Save") {
                Task {
                    do { try await wallet.reorder(items.map(\.id), section: section, generation: token); dismiss() }
                    catch is CancellationError {}
                    catch { self.error = "Kura could not save this order." }
                }
            }.disabled(wallet.saving)
        }
    }
}
