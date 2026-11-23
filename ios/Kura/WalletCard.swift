import Foundation

enum WalletSection: String, CaseIterable, Codable, Identifiable {
    case cards = "Cards", passes = "Passes", identities = "Identity"
    var id: Self { self }
    var icon: String {
        switch self {
        case .cards: return "creditcard"
        case .passes: return "ticket"
        case .identities: return "person.text.rectangle"
        }
    }
    var categories: [String] {
        switch self {
        case .cards: return ["Credit", "Debit", "Prepaid", "Gift Card", "Loyalty", "Membership", "Library"]
        case .passes: return ["Boarding Pass", "Transit", "Event", "Reservation", "Parking", "Coupon", "Temporary Access", "Other"]
        case .identities: return ["Passport", "Driver's License", "Residence Card", "National ID", "Health Insurance", "Employee ID", "Student ID"]
        }
    }
}

enum WalletSort: String, CaseIterable, Codable {
    case custom = "Custom order", ascending = "Name A–Z", descending = "Name Z–A", newest = "Newest added", oldest = "Oldest added"
}

struct WalletCard: Codable, Equatable, Identifiable {
    var id: UUID
    var name: String
    var payload: String
    var section: WalletSection
    var format: BarcodeFormat
    var category: String
    var notes: String
    var fields: [String: String]
    var expires: Date?
    var expiresByMonth: Bool?
    var created: Date
    var favorite: Bool
    var archived: Bool
    var media: WalletMedia
    var passStyle: PassAppearance?

    init(id: UUID = UUID(), name: String, payload: String, section: WalletSection = .cards,
         format: BarcodeFormat = .qr, category: String = "", notes: String = "",
         fields: [String: String] = [:], expires: Date? = nil, expiresByMonth: Bool? = nil, created: Date = Date(),
         favorite: Bool = false, archived: Bool = false, media: WalletMedia = WalletMedia(), passStyle: PassAppearance? = nil) throws {
        self.id = id
        self.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        self.payload = payload
        self.section = section
        self.format = format
        self.category = category
        self.notes = notes
        self.fields = fields
        self.expires = expires
        self.expiresByMonth = expiresByMonth
        self.created = created
        self.favorite = favorite
        self.archived = archived
        self.media = media
        self.passStyle = passStyle
        try validate()
    }

    func validate() throws {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, name.utf8.count <= 160
        else { throw WalletError.invalidName }
        guard payload.utf8.count <= 8192,
              payload.isEmpty || !payload.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { throw WalletError.invalidPayload }
        guard category.utf8.count <= 160, notes.utf8.count <= 32_768, fields.count <= 128,
              fields.allSatisfy({ !$0.key.isEmpty && $0.key.utf8.count <= 160 && $0.value.utf8.count <= 8192 })
        else { throw WalletError.invalidFields }
        try media.validate()
        try passStyle?.validate()
    }

    init(from decoder: Decoder) throws {
        let v = try decoder.container(keyedBy: CodingKeys.self)
        try self.init(id: v.decode(UUID.self, forKey: .id), name: v.decode(String.self, forKey: .name),
            payload: v.decode(String.self, forKey: .payload), section: v.decodeIfPresent(WalletSection.self, forKey: .section) ?? .cards,
            format: v.decodeIfPresent(BarcodeFormat.self, forKey: .format) ?? .qr,
            category: v.decodeIfPresent(String.self, forKey: .category) ?? "", notes: v.decodeIfPresent(String.self, forKey: .notes) ?? "",
            fields: v.decodeIfPresent([String: String].self, forKey: .fields) ?? [:],
            expires: v.decodeIfPresent(Date.self, forKey: .expires), expiresByMonth: v.decodeIfPresent(Bool.self, forKey: .expiresByMonth),
            created: v.decodeIfPresent(Date.self, forKey: .created) ?? .distantPast,
            favorite: v.decodeIfPresent(Bool.self, forKey: .favorite) ?? false, archived: v.decodeIfPresent(Bool.self, forKey: .archived) ?? false,
            media: v.decodeIfPresent(WalletMedia.self, forKey: .media) ?? WalletMedia(),
            passStyle: v.decodeIfPresent(PassAppearance.self, forKey: .passStyle))
    }
}

enum WalletError: LocalizedError {
    case invalidName, invalidPayload, invalidFields, vaultUnavailable, tooLarge, keyUnavailable, recordChanged

    var errorDescription: String? {
        switch self {
        case .invalidName: return "Enter a name of up to 160 UTF-8 bytes."
        case .invalidPayload: return "Enter a barcode value of up to 8192 UTF-8 bytes, or leave it empty."
        case .invalidFields: return "A field is empty, duplicated, or too long. Review the item details."
        case .vaultUnavailable: return "Kura could not read this wallet. Your saved file has been kept."
        case .tooLarge: return "This wallet exceeds the supported file size."
        case .keyUnavailable: return "Kura could not access the wallet key. Check your device passcode and try again."
        case .recordChanged: return "The item changed. Open it again and retry."
        }
    }
}
