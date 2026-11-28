import SwiftUI

extension WalletCard {
    var isImportedPass: Bool { passStyle.map { $0.manualEntry != true } ?? false }
}

private struct PassIdentity: Hashable {
    let identifier: String
    let serial: String

    init?(_ style: PassAppearance?) {
        guard let style, let type = style.passTypeIdentifier,
              !type.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !style.serialNumber.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        identifier = type
        serial = style.serialNumber
    }
}

extension PassImport {
    static func duplicates(_ items: [ImportedPass], existing: [WalletCard]) -> Set<UUID> {
        var seen = Set(existing.compactMap { PassIdentity($0.passStyle) })
        return Set(items.compactMap { item in
            guard let identity = PassIdentity(item.appearance), !seen.insert(identity).inserted else { return nil }
            return item.id
        })
    }
}

enum PassDraftError: LocalizedError {
    case invalidField
    var errorDescription: String? { "Check the date, number or currency in the pass fields." }
}

extension PassAppearance {
    static func manual(category: String) -> Self {
        let types = ["Boarding Pass": "boardingPass", "Transit": "boardingPass", "Event": "eventTicket", "Coupon": "coupon"]
        return Self(type: types[category] ?? "generic", logoText: "", organization: "", transitType: category == "Transit" ? "PKTransitTypeTrain" : "PKTransitTypeAir",
                    serialNumber: "", manualEntry: true, groups: [:], artwork: [:])
    }
}

private enum PassFieldKind: String, CaseIterable {
    case text = "Text", number = "Number", currency = "Currency", date = "Date and time"
}

extension PassField {
    fileprivate var kind: PassFieldKind {
        if currencyCode != nil { return .currency }
        if dateStyle != nil || timeStyle != nil { return .date }
        if numberStyle != nil || numberValue == true { return .number }
        return .text
    }

    fileprivate mutating func changeKind(_ target: PassFieldKind) {
        guard kind != target else { return }
        currencyCode = target == .currency ? (Locale.current.currency?.identifier ?? "USD") : nil
        dateStyle = target == .date ? "PKDateStyleMedium" : nil
        timeStyle = nil
        numberValue = target == .number || target == .currency
        numberStyle = target == .number ? "PKNumberStyleDecimal" : nil
    }

    var validManualValue: Bool {
        if kind == .currency {
            guard let currencyCode, currencyCode.range(of: #"^[A-Z]{3}$"#, options: .regularExpression) != nil else { return false }
        }
        if value.isEmpty { return true }
        switch kind {
        case .text: return true
        case .number, .currency:
            return value.range(of: #"^[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?$"#, options: .regularExpression) != nil
                && Decimal(string: value, locale: Locale(identifier: "en_US_POSIX")) != nil
        case .date:
            return PassImport.date(value) != nil
        }
    }
}

struct PassStyleEditor: View {
    @Binding var style: PassAppearance

    private let layouts = ["generic": "Generic", "boardingPass": "Boarding pass", "coupon": "Coupon", "eventTicket": "Event ticket", "storeCard": "Store card"]
    private let transit = ["PKTransitTypeAir": "Air", "PKTransitTypeBoat": "Boat", "PKTransitTypeBus": "Bus", "PKTransitTypeTrain": "Train", "PKTransitTypeGeneric": "Other"]
    private let groupLabels = ["headerFields": "Header", "primaryFields": "Primary", "secondaryFields": "Secondary", "auxiliaryFields": "Auxiliary", "backFields": "Back"]

    var body: some View {
        Section("Pass layout") {
            TextField("Organization", text: $style.organization)
            TextField("Logo text", text: $style.logoText)
            TextField("Barcode alternative text", text: Binding(get: { style.barcodeAlternative ?? "" },
                                                                set: { style.barcodeAlternative = $0.isEmpty ? nil : $0 }))
            Picker("Layout", selection: $style.type) {
                ForEach(PassAppearance.types, id: \.self) { type in Text(layouts[type] ?? type).tag(type) }
            }
            if style.type == "boardingPass" {
                Picker("Transit type", selection: $style.transitType) {
                    ForEach(transit.keys.sorted(), id: \.self) { key in Text(transit[key] ?? key).tag(key) }
                }
            }
        }
        Section("Pass fields") {
            ForEach(PassAppearance.groupNames, id: \.self) { group in
                DisclosureGroup((groupLabels[group] ?? group) + " fields") {
                    ForEach(style.groups[group] ?? [], id: \.key) { field in
                        StructuredPassFieldEditor(field: binding(for: field, in: group))
                        Button("Remove " + (field.label.isEmpty ? "field" : field.label), role: .destructive) {
                            style.groups[group]?.removeAll { $0.key == field.key }
                        }
                    }
                    Button("Add " + (groupLabels[group] ?? group).lowercased() + " field") {
                        style.groups[group, default: []].append(PassField(key: UUID().uuidString, label: "", value: ""))
                    }
                    .disabled((style.groups[group]?.count ?? 0) >= 100)
                }
            }
        }
    }

    private func binding(for field: PassField, in group: String) -> Binding<PassField> {
        Binding(get: { style.groups[group]?.first { $0.key == field.key } ?? field },
                set: { replacement in
                    guard let index = style.groups[group]?.firstIndex(where: { $0.key == field.key }) else { return }
                    style.groups[group]?[index] = replacement
                })
    }
}

private struct StructuredPassFieldEditor: View {
    @Binding var field: PassField
    private let numbers = ["Decimal", "Percent", "Scientific", "SpellOut"]
    private let dates = ["None", "Short", "Medium", "Long", "Full"]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            TextField("Field label", text: $field.label)
            Picker("Field type", selection: Binding(get: { field.kind }, set: { field.changeKind($0) })) {
                ForEach(PassFieldKind.allCases, id: \.self) { Text($0.rawValue).tag($0) }
            }
            TextField(field.kind == .date ? "Date (ISO 8601)" : "Field value", text: $field.value)
            if field.kind == .number {
                Picker("Number style", selection: option(\.numberStyle, fallback: "PKNumberStyleDecimal")) {
                    ForEach(numbers, id: \.self) { Text($0 == "SpellOut" ? "Spell out" : $0).tag("PKNumberStyle" + $0) }
                }
            } else if field.kind == .currency {
                TextField("Currency code", text: option(\.currencyCode, fallback: "")).textInputAutocapitalization(.characters)
            } else if field.kind == .date {
                DatePicker("Choose date and time", selection: Binding(get: { PassImport.date(field.value) ?? Date() },
                                                                      set: { field.value = $0.formatted(.iso8601) }))
                ForEach(["Date", "Time"], id: \.self) { label in
                    Picker(label + " style", selection: option(label == "Date" ? \.dateStyle : \.timeStyle, fallback: "PKDateStyleNone")) {
                        ForEach(dates, id: \.self) { Text($0).tag("PKDateStyle" + $0) }
                    }
                }
            }
            if !field.validManualValue { Text("Check this field’s value and format.").font(.caption) }
        }
    }

    private func option(_ path: WritableKeyPath<PassField, String?>, fallback: String) -> Binding<String> {
        Binding(get: { field[keyPath: path] ?? fallback }, set: { field[keyPath: path] = $0 })
    }
}
