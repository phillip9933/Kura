import SwiftUI

struct WalletEditor: View {
    @ObservedObject var wallet: WalletSession
    let section: WalletSection
    let original: WalletCard?
    let scanCard: Bool
    @State private var captureRequest: MediaCaptureRequest?
    @Environment(\.dismiss) private var dismiss
    private let token: UUID
    @State private var name: String
    @State private var category: String
    @State private var format: BarcodeFormat
    @State private var payload: String
    @State private var notes: String
    @State private var fields: [String: String]
    @State private var pass: PassAppearance
    @State private var media: WalletMedia
    @State private var hasExpiry: Bool
    @State private var expiryByMonth: Bool
    @State private var expiry: Date
    @State private var error: String?
    @State private var submitting = false
    @State private var scanning = false
    @State private var extraLabel = ""
    @State private var customLabels: [String] = []

    init(wallet: WalletSession, section: WalletSection, original: WalletCard?, scanCard: Bool = false) {
        self.wallet = wallet
        self.section = section
        self.original = original
        self.scanCard = scanCard
        token = wallet.generation
        _name = State(initialValue: original?.name ?? "")
        let categories = wallet.snapshot.preferences.categories(for: section)
        let category = original?.category ?? (section == .passes && categories.contains("Other") ? "Other" : categories.first ?? "")
        _category = State(initialValue: category)
        var style = original?.passStyle ?? .manual(category: category)
        if style.organization.isEmpty { style.organization = original?.fields["Organization"] ?? "" }
        _pass = State(initialValue: style)
        _format = State(initialValue: original?.format ?? .qr)
        _payload = State(initialValue: original?.payload ?? "")
        _notes = State(initialValue: original?.notes ?? "")
        _fields = State(initialValue: original?.fields ?? [:])
        _media = State(initialValue: original?.media ?? WalletMedia())
        _hasExpiry = State(initialValue: original?.expires != nil)
        _expiryByMonth = State(initialValue: original?.expiresByMonth ?? (original == nil && section == .cards))
        _expiry = State(initialValue: original?.expires ?? Date())
    }

    private var definitions: [FieldDefinition] {
        let configured = wallet.snapshot.preferences.definitions(for: section)
        let standard = FieldDefinition.suggested(section: section, category: category).filter {
            !(section == .passes && original?.isImportedPass != true && $0.name == "Organization")
        }
        var result = standard
        for field in configured {
            if let index = result.firstIndex(where: { $0.name == field.name }) { result[index] = field }
            else { result.append(field) }
        }
        for label in (Array(fields.keys).sorted() + customLabels) where !(section == .passes && original?.isImportedPass != true && label == "Organization") && !result.contains(where: { $0.name == label }) {
            result.append(FieldDefinition(name: label, kind: .text))
        }
        return result
    }

    var body: some View {
        Form {
            if scanCard && media.front == nil {
                Section("Scan card") {
                    Button("Take a front photo") { captureRequest = MediaCaptureRequest(slot: .front, source: .camera) }
                    Button("Choose a front photo") { captureRequest = MediaCaptureRequest(slot: .front, source: .photo) }
                }
            }
            Section("Item") {
                TextField("Name", text: $name)
                Picker("Category", selection: $category) {
                    Text("Uncategorized").tag("")
                    let categories = wallet.snapshot.preferences.categories(for: section)
                    ForEach(categories + (!category.isEmpty && !categories.contains(category) ? [category] : []), id: \.self) { Text($0).tag($0) }
                }
                Toggle("Expiration date", isOn: $hasExpiry)
                if hasExpiry {
                    Toggle("Month and year only", isOn: $expiryByMonth)
                    if expiryByMonth {
                        MonthSelection(date: $expiry)
                    } else { DatePicker("Expires", selection: $expiry, displayedComponents: .date) }
                }
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
            if section == .passes && original?.isImportedPass != true {
                PassStyleEditor(style: $pass)
            }
            MediaEditor(media: $media, ocrFields: recognitionFields, applyOCR: applySuggestions,
                        imagesEditable: original?.isImportedPass != true, requestedCapture: $captureRequest,
                        validateOCR: validateSuggestions)
            if let error { Text(error) }
        }
        .autocorrectionDisabled().disabled(wallet.saving || submitting)
        .navigationTitle(original == nil ? "Add item" : "Edit item")
        .navigationBarBackButtonHidden(wallet.saving || submitting)
        .toolbar {
            Button("Save") { Task { await save() } }.disabled(wallet.saving || submitting || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .fullScreenCover(isPresented: $scanning) {
            BarcodeScannerView { text, detected in
                guard wallet.generation == token else { return }
                payload = text; format = detected; scanning = false
            }
        }
    }

    private var recognitionFields: [String: String] {
        var result = Dictionary(uniqueKeysWithValues: definitions.map { ($0.name, fields[$0.name] ?? "") })
        if section == .cards {
            result["Card name"] = name
            result["Expiry"] = hasExpiry ? WalletDateFormat.iso.formatter.string(from: expiry) : ""
        }
        return result
    }

    private func validateSuggestions(_ values: [String: String]) -> String? {
        for (label, value) in values {
            guard recognitionFields[label]?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == true else { continue }
            if value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "Enter a value for " + label + " or turn it off." }
            if label == "Expiry" && section == .cards {
                if RecognizedExpiry.parse(value) == nil { return "Enter a valid expiry such as MM/YY or YYYY-MM-DD, or turn it off." }
            } else if label == "Card name" && section == .cards {
                if value.utf8.count > 160 { return "Shorten the card name or turn it off." }
            } else if let field = definitions.first(where: { $0.name == label }), !field.accepts(value) {
                return "Check the value for " + label + " or turn it off."
            }
        }
        return nil
    }

    private func applySuggestions(_ values: [String: String]) {
        for (label, value) in values {
            guard recognitionFields[label]?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == true else { continue }
            if label == "Card name" && section == .cards { name = value }
            else if label == "Expiry" && section == .cards, let found = RecognizedExpiry.parse(value) {
                hasExpiry = true
                expiryByMonth = found.monthOnly
                expiry = found.date
            } else if definitions.contains(where: { $0.name == label }) { fields[label] = value }
        }
    }

    @MainActor
    private func save() async {
        guard !submitting, wallet.generation == token else { return }
        submitting = true
        defer { submitting = false }
        do {
            if !payload.isEmpty, (payload != original?.payload || format != original?.format),
               await CardQR.shared.image(for: payload, format: format) == nil {
                error = "This value cannot be displayed as " + format.rawValue + ". Check the value or choose another format."
                return
            }
            for field in definitions where fields[field.name] != original?.fields[field.name] && !field.accepts(fields[field.name] ?? "") {
                error = "Check the value for \(field.name)."; return
            }
            var style = original?.passStyle
            if section == .passes && original?.isImportedPass != true {
                style = pass
                style?.backgroundColor = fields["Background color"]
                style?.foregroundColor = fields["Text color"]
                style?.labelColor = fields["Label color"]
                style?.relevantDate = fields["Event date"].flatMap { PassImport.date($0) ?? WalletDateFormat.iso.parse($0) }
                fields["Organization"] = pass.organization
            }
            let item = try WalletCard(id: original?.id ?? UUID(), name: name, payload: payload, section: section, format: format,
                category: category, notes: notes, fields: fields.filter { !$0.value.isEmpty }, expires: hasExpiry ? (expiryByMonth ? MonthSelection.lastDay(of: expiry) : expiry) : nil,
                expiresByMonth: hasExpiry && expiryByMonth ? true : nil,
                created: original?.created ?? Date(), favorite: original?.favorite ?? false, archived: original?.archived ?? false,
                media: media, passStyle: style)
            try await wallet.save(item, generation: token, replacing: original)
            dismiss()
        } catch is CancellationError {} catch { self.error = (error as? LocalizedError)?.errorDescription ?? "Save failed. Your draft is still here." }
    }
}

private struct WalletFieldEditor: View {
    let field: FieldDefinition
    @Binding var value: String
    var body: some View {
        if FieldDefinition.colorFields.contains(field.name) {
            ColorPicker(field.name, selection: Binding(get: { WalletTileColor.parse(value)?.color ?? .primary }, set: { color in
                var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
                if UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a) {
                    value = String(format: "#%02X%02X%02X", Int((r * 255).rounded()), Int((g * 255).rounded()), Int((b * 255).rounded()))
                }
            }), supportsOpacity: false)
            if !value.isEmpty { Button("Reset " + field.name.lowercased()) { value = "" } }
        } else if field.kind == .date || field.name == "Event date" {
            let event = field.name == "Event date"
            Toggle(field.name, isOn: Binding(get: { !value.isEmpty }, set: { value = $0 ? storedDate(Date(), event: event) : "" }))
            if !value.isEmpty {
                let selection = Binding<Date>(get: {
                    PassImport.date(value) ?? field.parsedDate(value) ?? Date()
                }, set: { value = storedDate($0, event: event) })
                if field.dateFormat == .month { MonthSelection(date: selection) }
                else { DatePicker(field.name, selection: selection, displayedComponents: event ? [.date, .hourAndMinute] : .date) }
            }
        } else { TextField(field.name, text: $value).keyboardType(keyboard) }
    }

    private func storedDate(_ date: Date, event: Bool) -> String {
        if event { return ISO8601DateFormatter().string(from: date) }
        return WalletDateFormat.iso.formatter.string(from: date)
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


struct MonthSelection: View {
    @Binding var date: Date
    private let calendar = Calendar(identifier: .gregorian)

    var body: some View {
        Picker("Month", selection: component(.month)) {
            ForEach(1...12, id: \.self) { Text(calendar.monthSymbols[$0 - 1]).tag($0) }
        }
        let year = calendar.component(.year, from: date)
        Stepper("Year: \(String(year))", value: component(.year), in: min(year, 1900)...max(year, 2200))
    }

    private func component(_ part: Calendar.Component) -> Binding<Int> {
        Binding(get: { calendar.component(part, from: date) }, set: { number in
            var components = calendar.dateComponents([.year, .month], from: date)
            components.setValue(number, for: part)
            if let updated = calendar.date(from: components) { date = updated }
        })
    }

    static func lastDay(of date: Date) -> Date {
        let calendar = Calendar(identifier: .gregorian)
        guard let end = calendar.dateInterval(of: .month, for: date)?.end else { return date }
        return calendar.date(byAdding: .day, value: -1, to: end) ?? date
    }
}
