import Foundation

extension DateFormatter {
    static let walletDate: DateFormatter = {
        let value = DateFormatter()
        value.locale = Locale(identifier: "en_US_POSIX")
        value.calendar = Calendar(identifier: .gregorian)
        value.dateFormat = "yyyy-MM-dd"
        value.isLenient = false
        return value
    }()
}

enum WalletDateFormat: String, Codable, CaseIterable {
    case iso = "yyyy-MM-dd", short = "dd/MM/yy", month = "MM/yy"

    var formatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = .current
        formatter.dateFormat = rawValue
        formatter.isLenient = false
        formatter.twoDigitStartDate = Date(timeIntervalSince1970: 946684800)
        return formatter
    }

    func parse(_ raw: String) -> Date? {
        let format = formatter
        guard let date = format.date(from: raw), format.string(from: date) == raw else { return nil }
        return date
    }
}

extension FieldDefinition {
    func accepts(_ value: String) -> Bool {
        if value.isEmpty { return true }
        if name == "Billing day" { return Int(value).map { (1...31).contains($0) } ?? false }
        if name == "Event date" { return PassImport.date(value) != nil || WalletDateFormat.iso.parse(value) != nil }
        if Self.colorFields.contains(name) { return WalletTileColor.parse(value) != nil }
        switch kind {
        case .date:
            return parsedDate(value) != nil
        case .currency, .number:
            return value.range(of: #"^[+-]?(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)$"#, options: .regularExpression) != nil
        default: return true
        }
    }

    static let colorFields = ["Card color", "Background color", "Text color", "Label color"]

    func parsedDate(_ raw: String) -> Date? {
        WalletDateFormat.iso.parse(raw) ?? (dateFormat ?? .iso).parse(raw)
    }

    func display(_ raw: String, currency: String) -> String {
        if kind == .date, let date = parsedDate(raw) { return (dateFormat ?? .iso).formatter.string(from: date) }
        if kind == .currency, accepts(raw), let amount = Decimal(string: raw, locale: Locale(identifier: "en_US_POSIX")) {
            return amount.formatted(.currency(code: currency))
        }
        return raw
    }

    static func suggested(section: WalletSection, category: String) -> [Self] {
        let base: [String]
        switch section {
        case .cards: base = ["Number", "Issuer", "Network", "Card color"]
        case .passes: base = ["Organization", "Description", "Event date", "Background color", "Text color", "Label color"]
        case .identities: base = ["Document number", "Issuing authority", "Card color"]
        }
        let extra: [String]
        switch category.lowercased() {
        case "credit": extra = ["Cardholder", "Credit limit", "Billing day", "Annual fee waiver"]
        case "debit", "prepaid": extra = ["Cardholder", "Balance"]
        case "gift card": extra = ["Recipient", "Balance", "Purchase date"]
        case "library": extra = ["Borrower name", "Borrower number", "Library branch"]
        case "loyalty", "store card": extra = ["Member name", "Member number", "Tier", "Points"]
        case "membership": extra = ["Member name", "Member number", "Membership level", "Member since"]
        case "gym": extra = ["Member name", "Member number", "Home club", "Plan", "Member since"]
        case "residence card": extra = ["Nationality", "Residence status", "Issuing country", "Issue date"]
        case "passport", "national id": extra = ["Date of birth", "Nationality", "Issuing country", "Issue date"]
        case "driver's license": extra = ["Date of birth", "License class", "Issue date"]
        case "health insurance": extra = ["Insurer", "Policy number", "Group number"]
        case "employee id": extra = ["Employer", "Department", "Job title"]
        case "student id": extra = ["Institution", "Course", "Academic year"]
        case "transit": extra = ["From", "To", "Service number", "Zone", "Ticket class"]
        case "boarding pass": extra = ["Passenger", "From", "To", "Flight number", "Terminal", "Gate", "Seat", "Travel class", "Booking reference"]
        case "event", "event ticket", "concert", "sports": extra = ["Venue", "Section", "Row", "Seat", "Booking reference"]
        case "reservation": extra = ["Location", "Guest name", "Booking reference", "Guests", "Check-out date"]
        case "parking": extra = ["Vehicle registration", "Location", "Space"]
        case "coupon": extra = ["Offer", "Minimum spend", "Redemption code"]
        case "temporary access": extra = ["Visitor name", "Location", "Host", "Valid from"]
        default: extra = []
        }
        return (base + extra).map { name in
            var kind = FieldKind.text
            if ["Date of birth","Issue date","Purchase date","Member since","Check-out date","Valid from"].contains(name) { kind = .date }
            if ["Balance","Credit limit","Minimum spend","Annual fee waiver"].contains(name) { kind = .currency }
            if ["Guests","Points","Billing day"].contains(name) { kind = .number }
            return Self(name: name, kind: kind)
        }
    }
}
