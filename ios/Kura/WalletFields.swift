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

extension FieldDefinition {
    func accepts(_ value: String) -> Bool {
        if value.isEmpty { return true }
        switch kind {
        case .date:
            guard let date = DateFormatter.walletDate.date(from: value) else { return false }
            return DateFormatter.walletDate.string(from: date) == value
        case .currency, .number:
            return value.range(of: #"^[+-]?(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)$"#, options: .regularExpression) != nil
        default: return true
        }
    }

    static func suggested(section: WalletSection, category: String) -> [Self] {
        let base: [String]
        switch section {
        case .cards: base = ["Number", "Issuer", "Network"]
        case .passes: base = ["Organization", "Description"]
        case .identities: base = ["Document number", "Issuing authority"]
        }
        let extra: [String]
        switch category.lowercased() {
        case "credit": extra = ["Cardholder", "Credit limit", "Billing day", "Annual fee waiver"]
        case "debit", "prepaid": extra = ["Cardholder", "Balance"]
        case "gift card": extra = ["Recipient", "Balance", "Purchase date"]
        case "library": extra = ["Borrower name", "Borrower number", "Library branch"]
        case "loyalty", "membership": extra = ["Member name", "Member number", "Tier", "Points", "Member since"]
        case "passport", "national id", "residence card": extra = ["Date of birth", "Nationality", "Issuing country", "Issue date"]
        case "driver's license": extra = ["Date of birth", "License class", "Issue date"]
        case "health insurance": extra = ["Insurer", "Policy number", "Group number"]
        case "employee id": extra = ["Employer", "Department", "Job title"]
        case "student id": extra = ["Institution", "Course", "Academic year"]
        case "boarding pass", "transit": extra = ["Passenger", "From", "To", "Flight number", "Terminal", "Gate", "Seat", "Booking reference"]
        case "event": extra = ["Venue", "Section", "Row", "Seat", "Booking reference"]
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
