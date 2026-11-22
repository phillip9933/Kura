import SwiftUI

struct WalletSettings: View {
    @ObservedObject var wallet: WalletSession
    @Environment(\.dismiss) private var dismiss
    @State private var prefs: WalletPreferences
    @State private var confirmPhone = false
    @State private var error: String?
    @State private var authenticating = false
    private let token: UUID

    init(wallet: WalletSession) {
        self.wallet = wallet
        token = wallet.generation
        _prefs = State(initialValue: wallet.snapshot.preferences)
    }

    var body: some View {
        Form {
            Section("Data and security") {
                NavigationLink("Backup and recovery") { BackupView(wallet: wallet) }
                Button(PhoneUnlock.enabled ? "Require app authentication" : "Use phone unlock only") {
                    if PhoneUnlock.enabled { changeAuth(false) } else { confirmPhone = true }
                }.disabled(authenticating)
                Picker("Lock after leaving Kura", selection: $prefs.lockDelay) {
                    ForEach([0, 15, 30, 60, 120, 300], id: \.self) { value in
                        Text(value == 0 ? "Immediately" : "\(value) seconds").tag(value)
                    }
                }
            }
            Section("Display and navigation") {
                Picker("Appearance", selection: $prefs.appearance) {
                    ForEach(["System", "Light", "Dark"], id: \.self) { Text($0) }
                }
                Picker("Start section", selection: $prefs.startSection) {
                    ForEach(WalletSection.allCases) { Text($0.rawValue).tag($0) }
                }
                Toggle("Grid view", isOn: $prefs.useGrid)
                Toggle("Favorites first", isOn: $prefs.favoritesFirst)
                Toggle("Show expiring items on unlock", isOn: $prefs.expiryAlert)
                Toggle("Show expiry indicators", isOn: $prefs.showExpiry)
                Stepper("Expiry window: \(prefs.expiryDays) days", value: $prefs.expiryDays, in: 0...365)
                TextField("Currency code", text: $prefs.currency).textInputAutocapitalization(.characters)
            }
            Section("Barcode display") {
                Toggle("Maximum brightness", isOn: $prefs.brightness)
                Toggle("Rotate by 180°", isOn: $prefs.flipped)
            }
            Section("Categories and fields") {
                ForEach(WalletSection.allCases) { section in
                    NavigationLink(section.rawValue) { SectionSettings(section: section, prefs: $prefs) }
                }
            }
            Section("About") {
                LabeledContent("Kura for iPhone", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")
                NavigationLink("Open-source notices") { LicenseView() }
            }
            if let error { Text(error) }
        }.navigationTitle("Settings").disabled(wallet.saving)
        .toolbar {
            Button("Save") {
                Task {
                    do {
                        prefs.currency = prefs.currency.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                        try prefs.validate()
                        try await wallet.preferences(prefs, generation: token)
                        dismiss()
                    } catch is CancellationError {} catch { self.error = "Check your settings and try again." }
                }
            }
        }
        .confirmationDialog("Use phone unlock only?", isPresented: $confirmPhone, titleVisibility: .visible) {
            Button("Use phone unlock") { changeAuth(true) }
        } message: { Text("Anyone with access to this unlocked iPhone will be able to open your wallet.") }
    }

    private func changeAuth(_ enabled: Bool) {
        authenticating = true
        Task {
            defer { authenticating = false }
            do { try await wallet.changePhoneUnlock(enabled, generation: token) }
            catch is CancellationError {} catch { self.error = "The unlock method was not changed." }
        }
    }
}

private struct SectionSettings: View {
    let section: WalletSection
    @Binding var prefs: WalletPreferences
    @State private var category = ""
    @State private var newField = ""
    @State private var kind = FieldKind.text
    @State private var message: String?
    private var key: String { section.rawValue }
    var body: some View {
        Form {
            Section("Categories") {
                ForEach(prefs.categories(for: section), id: \.self) { name in Text(name) }
                    .onDelete { offsets in
                        var names = prefs.categories(for: section); names.remove(atOffsets: offsets); prefs.categories[key] = names
                    }
                    .onMove { positions, target in
                        var names = prefs.categories(for: section); names.move(fromOffsets: positions, toOffset: target); prefs.categories[key] = names
                    }
                TextField("Category name", text: $category)
                Button("Add category") {
                    let name = clean(category)
                    guard valid(name, among: prefs.categories(for: section)) else { return }
                    prefs.categories[key] = prefs.categories(for: section) + [name]
                    category = ""
                }.disabled(category.isEmpty)
            }
            Section {
                ForEach(prefs.definitions(for: section)) { field in
                    Picker(field.name, selection: Binding(get: { field.kind }, set: { type in
                        prefs.definitions[key] = prefs.definitions(for: section).map { $0.name == field.name ? FieldDefinition(name: $0.name, kind: type) : $0 }
                    })) {
                        ForEach(FieldKind.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                }
                .onDelete { offsets in
                    var fields = prefs.definitions(for: section); fields.remove(atOffsets: offsets); prefs.definitions[key] = fields
                }
                TextField("New field name", text: $newField)
                Picker("Field type", selection: $kind) {
                    ForEach(FieldKind.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }
                Button("Add field") {
                    let name = clean(newField)
                    guard valid(name, among: prefs.definitions(for: section).map(\.name)) else { return }
                    prefs.definitions[key] = prefs.definitions(for: section) + [FieldDefinition(name: name, kind: kind)]
                    newField = ""
                }.disabled(newField.isEmpty)
            } header: { Text("Custom fields") } footer: {
                Text("Removing a category or field here keeps existing item values.")
            }
            if let message { Text(message) }
        }.navigationTitle(section.rawValue).toolbar { EditButton() }
    }

    private func clean(_ name: String) -> String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private func valid(_ name: String, among names: [String]) -> Bool {
        guard !name.isEmpty, name.utf8.count <= 160, names.count < 128,
              !names.contains(where: { $0.caseInsensitiveCompare(name) == .orderedSame }) else {
            message = "Enter a unique name of up to 160 UTF-8 bytes."; return false
        }
        message = nil
        return true
    }
}

private struct LicenseView: View {
    var body: some View {
        ScrollView {
            Text(verbatim: (try? Bundle.main.url(forResource: "Notices", withExtension: "txt")
                .map { try String(contentsOf: $0, encoding: .utf8) }) ?? "Notices unavailable.")
                .frame(maxWidth: .infinity, alignment: .leading).padding()
        }.navigationTitle("Notices")
    }
}
