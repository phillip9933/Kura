import SwiftUI

struct WalletSettings: View {
    @ObservedObject var wallet: WalletSession
    @Environment(\.dismiss) private var dismiss
    @State private var prefs: WalletPreferences
    @State private var confirmPhone = false
    @State private var confirmErase = false
    @State private var renames: [CategoryChange] = []
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
                NavigationLink("Archive") { WalletArchive(wallet: wallet) }
                NavigationLink("Backup and recovery") { BackupView(wallet: wallet) }
                Button(PhoneUnlock.enabled ? "Require app authentication" : "Use phone unlock only") {
                    if PhoneUnlock.enabled { changeAuth(false) } else { confirmPhone = true }
                }.disabled(authenticating)
                if !PhoneUnlock.enabled {
                    Toggle("Use biometrics when available", isOn: Binding(get: { !PasscodeUnlock.enabled }, set: { useBiometrics in
                        Task {
                            authenticating = true
                            defer { authenticating = false }
                            do { try await wallet.changePasscodeOnly(!useBiometrics, generation: token) }
                            catch is CancellationError {} catch { self.error = "The authentication preference was not changed." }
                        }
                    })).disabled(authenticating)
                }
                Picker("Lock after leaving Kura", selection: $prefs.lockDelay) {
                    ForEach([0, 15, 30, 60, 120, 300], id: \.self) { value in
                        Text(value == 0 ? "Immediately" : "\(value) seconds").tag(value)
                    }
                }
            }
            Section("Display and navigation") {
                NavigationLink("Navigation and layout") { NavigationSettings(prefs: $prefs) }
                NavigationLink {
                    AppearanceSettings(selection: $prefs.appearance)
                } label: {
                    LabeledContent("Appearance", value: prefs.appearance)
                }.accessibilityIdentifier("wallet.appearance")
                Picker("Start section", selection: $prefs.startSection) {
                    ForEach(prefs.layout.visible) { Text($0.rawValue).tag($0) }
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
                    NavigationLink(section.rawValue) { SectionSettings(section: section, prefs: $prefs, renames: $renames) }
                }
            }
            Section("About") {
                LabeledContent("Kura for iPhone", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")
                Link("Source and issue tracker", destination: URL(string: "https://github.com/phillip9933/Kura")!)
                NavigationLink("Open-source notices") { LicenseView() }
            }
            Section {
                Button("Erase local wallet", role: .destructive) { confirmErase = true }
            }
            if let error { Text(error) }
        }.navigationTitle("Settings").disabled(wallet.saving)
        .toolbar {
            Button("Save") {
                Task {
                    do {
                        prefs.currency = prefs.currency.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                        try prefs.validate()
                        try await wallet.preferences(prefs, generation: token, renames: renames)
                        dismiss()
                    } catch is CancellationError {} catch { self.error = "Check your settings and try again." }
                }
            }.accessibilityIdentifier("wallet.settings.save")
        }
        .alert("Erase this wallet and its local recovery copies?", isPresented: $confirmErase) {
            Button("Cancel", role: .cancel) {}
            Button("Erase wallet", role: .destructive) { Task {
                do { try await wallet.eraseWallet(generation: token) }
                catch is CancellationError {} catch { self.error = "Kura could not finish erasing the wallet." }
            } }
        } message: { Text("You must authenticate to continue. Backups saved to Files will remain available.") }
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

private struct AppearanceSettings: View {
    @Binding var selection: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List(["System", "Light", "Dark"], id: \.self) { mode in
            Button {
                selection = mode
                dismiss()
            } label: {
                HStack {
                    Text(mode)
                    Spacer()
                    if selection == mode { Image(systemName: "checkmark").accessibilityHidden(true) }
                }
            }
            .accessibilityIdentifier("wallet.appearance." + mode)
            .accessibilityAddTraits(selection == mode ? .isSelected : [])
        }.navigationTitle("Appearance")
    }
}

private struct SectionSettings: View {
    let section: WalletSection
    @Binding var prefs: WalletPreferences
    @Binding var renames: [CategoryChange]
    @State private var renameFrom: String?
    @State private var renamed = ""
    @State private var category = ""
    @State private var newField = ""
    @State private var kind = FieldKind.text
    @State private var dateFormat = WalletDateFormat.iso
    @State private var message: String?
    private var key: String { section.rawValue }
    var body: some View {
        Form {
            Section("Display") {
                Stepper("Grid columns: \(prefs.layout.columns(for: section))", value: Binding(get: { prefs.layout.columns(for: section) }, set: { count in
                    var columns = prefs.layout.sectionColumns ?? [:]
                    columns[key] = count; prefs.layout.sectionColumns = columns
                }), in: 1...3)
                Toggle("Show front image in grid", isOn: Binding(get: { prefs.layout.frontImage[key] ?? true },
                    set: { prefs.layout.frontImage[key] = $0 }))
                if section == .passes { Toggle("Offer upcoming passes", isOn: $prefs.layout.showUpcoming) }
            }
            Section("Categories") {
                ForEach(prefs.categories(for: section), id: \.self) { name in
                    Button(name) { renameFrom = name; renamed = name }
                }
                    .onDelete { offsets in
                        var names = prefs.categories(for: section); names.remove(atOffsets: offsets); prefs.categories[key] = names
                    }
                    .onMove { positions, target in
                        var names = prefs.categories(for: section); names.move(fromOffsets: positions, toOffset: target); prefs.categories[key] = names
                    }
                Button("Sort categories A–Z") { prefs.categories[key] = prefs.categories(for: section).sorted { $0.localizedStandardCompare($1) == .orderedAscending } }
                Button("Restore default categories") { prefs.categories.removeValue(forKey: key) }
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
                    if field.kind == .date {
                        dateFormats(Binding(get: { field.dateFormat ?? .iso }, set: { format in
                            var fields = prefs.definitions(for: section)
                            if let index = fields.firstIndex(where: { $0.id == field.id }) {
                                fields[index].dateFormat = format; prefs.definitions[key] = fields
                            }
                        }))
                    }
                }
                .onDelete { offsets in
                    var fields = prefs.definitions(for: section); fields.remove(atOffsets: offsets); prefs.definitions[key] = fields
                }
                TextField("New field name", text: $newField)
                Picker("Field type", selection: $kind) {
                    ForEach(FieldKind.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }
                if kind == .date { dateFormats($dateFormat) }
                Button("Add field") {
                    let name = clean(newField)
                    guard valid(name, among: prefs.definitions(for: section).map(\.name)) else { return }
                    prefs.definitions[key] = prefs.definitions(for: section) + [FieldDefinition(name: name, kind: kind, dateFormat: kind == .date ? dateFormat : nil)]
                    newField = ""
                }.disabled(newField.isEmpty)
            } header: { Text("Custom fields") } footer: {
                Text("Removing a category or field here keeps existing item values.")
            }
            if let message { Text(message) }
        }.navigationTitle(section.rawValue).toolbar { EditButton() }
        .alert("Rename category", isPresented: Binding(get: { renameFrom != nil }, set: { if !$0 { renameFrom = nil } })) {
            TextField("Category name", text: $renamed)
            Button("Cancel", role: .cancel) { renameFrom = nil }
            Button("Rename") {
                guard let old = renameFrom else { return }
                let replacement = clean(renamed)
                let names = prefs.categories(for: section)
                guard valid(replacement, among: names.filter { $0 != old }) else { return }
                prefs.categories[key] = names.map { $0 == old ? replacement : $0 }
                renames.append(CategoryChange(section: section, old: old, new: replacement))
                renameFrom = nil
            }
        }
    }

    private func dateFormats(_ selected: Binding<WalletDateFormat>) -> some View {
        Picker("Date format", selection: selected) {
            ForEach(WalletDateFormat.allCases, id: \.self) { Text($0.rawValue.uppercased()).tag($0) }
        }
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
            Text(verbatim: ["Notices", "LICENSE-ZIPFoundation", "LICENSE-ZXingCpp"].compactMap { name in
                Bundle.main.url(forResource: name, withExtension: "txt").flatMap { try? String(contentsOf: $0, encoding: .utf8) }
            }.joined(separator: "\n\n"))
                .frame(maxWidth: .infinity, alignment: .leading).padding()
        }.navigationTitle("Notices")
    }
}
