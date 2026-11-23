import SwiftUI

struct WalletEditor: View {
    @ObservedObject var wallet: WalletSession
    let section: WalletSection
    let original: WalletCard?
    @Environment(\.dismiss) private var dismiss
    private let token: UUID
    @State private var name: String
    @State private var category: String
    @State private var format: BarcodeFormat
    @State private var payload: String
    @State private var notes: String
    @State private var fields: [String: String]
    @State private var media: WalletMedia
    @State private var hasExpiry: Bool
    @State private var expiry: Date
    @State private var error: String?
    @State private var scanning = false
    @State private var extraLabel = ""
    @State private var customLabels: [String] = []

    init(wallet: WalletSession, section: WalletSection, original: WalletCard?) {
        self.wallet = wallet
        self.section = section
        self.original = original
        token = wallet.generation
        _name = State(initialValue: original?.name ?? "")
        _category = State(initialValue: original?.category ?? wallet.snapshot.preferences.categories(for: section).first ?? "")
        _format = State(initialValue: original?.format ?? .qr)
        _payload = State(initialValue: original?.payload ?? "")
        _notes = State(initialValue: original?.notes ?? "")
        _fields = State(initialValue: original?.fields ?? [:])
        _media = State(initialValue: original?.media ?? WalletMedia())
        _hasExpiry = State(initialValue: original?.expires != nil)
        _expiry = State(initialValue: original?.expires ?? Date())
    }

    private var definitions: [FieldDefinition] {
        let configured = wallet.snapshot.preferences.definitions(for: section)
        let standard = FieldDefinition.suggested(section: section, category: category)
        var result = standard
        for field in configured {
            if let index = result.firstIndex(where: { $0.name == field.name }) { result[index] = field }
            else { result.append(field) }
        }
        for label in (Array(fields.keys).sorted() + customLabels) where !result.contains(where: { $0.name == label }) {
            result.append(FieldDefinition(name: label, kind: .text))
        }
        return result
    }

    var body: some View {
        Form {
            Section("Item") {
                TextField("Name", text: $name)
                Picker("Category", selection: $category) {
                    Text("Uncategorized").tag("")
                    let categories = wallet.snapshot.preferences.categories(for: section)
                    ForEach(categories + (!category.isEmpty && !categories.contains(category) ? [category] : []), id: \.self) { Text($0).tag($0) }
                }
                Toggle("Expiration date", isOn: $hasExpiry)
                if hasExpiry { DatePicker("Expires", selection: $expiry, displayedComponents: .date) }
            }
            Section("Barcode") {
                Picker("Format", selection: $format) {
                    ForEach(BarcodeFormat.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }
                TextField("Exact barcode value", text: $payload, axis: .vertical).textInputAutocapitalization(.never)
                Button("Scan barcode") { scanning = true }
            }
            Section("Details") {
                ForEach(definitions) { field in
                    WalletFieldEditor(field: field, value: Binding(
                        get: { fields[field.name] ?? "" }, set: { fields[field.name] = $0 }))
                }
                HStack {
                    TextField("New field name", text: $extraLabel)
                    Button("Add") {
                        let value = extraLabel.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !value.isEmpty, value.utf8.count <= 160,
                              !definitions.contains(where: { $0.name.caseInsensitiveCompare(value) == .orderedSame })
                        else { error = WalletError.invalidFields.localizedDescription; return }
                        customLabels.append(value)
                        extraLabel = ""
                    }.disabled(extraLabel.isEmpty)
                }
                TextField("Notes", text: $notes, axis: .vertical)
            }
            MediaEditor(media: $media, ocrFields: Dictionary(uniqueKeysWithValues: definitions.map { ($0.name, fields[$0.name] ?? "") }), applyOCR: { suggestions in
                for field in definitions where (fields[field.name] ?? "").isEmpty {
                    if let value = suggestions[field.name] { fields[field.name] = value }
                }
            }, imagesEditable: original?.passStyle == nil)
            if let error { Text(error) }
        }
        .autocorrectionDisabled().disabled(wallet.saving)
        .navigationTitle(original == nil ? "Add item" : "Edit item")
        .navigationBarBackButtonHidden(wallet.saving)
        .toolbar {
            Button("Save") { Task { await save() } }.disabled(wallet.saving || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .fullScreenCover(isPresented: $scanning) {
            BarcodeScannerView { text, detected in
                guard wallet.generation == token else { return }
                payload = text; format = detected; scanning = false
            }
        }
    }

    private func save() async {
        do {
            for field in definitions where !field.accepts(fields[field.name] ?? "") {
                error = "Check the value for \(field.name)."; return
            }
            let item = try WalletCard(id: original?.id ?? UUID(), name: name, payload: payload, section: section, format: format,
                category: category, notes: notes, fields: fields.filter { !$0.value.isEmpty }, expires: hasExpiry ? expiry : nil,
                created: original?.created ?? Date(), favorite: original?.favorite ?? false, archived: original?.archived ?? false,
                media: media, passStyle: original?.passStyle)
            try await wallet.save(item, generation: token, replacing: original)
            dismiss()
        } catch is CancellationError {} catch { self.error = (error as? WalletError)?.localizedDescription ?? "Save failed. Your draft is still here." }
    }
}

private struct WalletFieldEditor: View {
    let field: FieldDefinition
    @Binding var value: String
    var body: some View {
        if field.kind == .date {
            VStack(alignment: .leading) {
                TextField("\(field.name) (YYYY-MM-DD)", text: $value)
                if !value.isEmpty {
                    DatePicker(field.name, selection: Binding(get: { DateFormatter.walletDate.date(from: value) ?? Date() },
                        set: { value = DateFormatter.walletDate.string(from: $0) }), displayedComponents: .date)
                }
            }
        } else {
            TextField(field.name, text: $value).keyboardType(keyboard)
        }
    }
    private var keyboard: UIKeyboardType {
        switch field.kind {
        case .number, .currency: return .decimalPad
        case .email: return .emailAddress
        case .phone: return .phonePad
        case .url: return .URL
        default: return .default
        }
    }
}
