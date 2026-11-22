import Foundation

struct WalletCard: Codable, Equatable, Identifiable {
    let id: UUID
    let name: String
    let payload: String

    init(id: UUID = UUID(), name: String, payload: String) throws {
        let title = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty, title.utf8.count <= 160 else { throw WalletError.invalidName }
        guard !payload.trimmingCharacters(in: .whitespaces).isEmpty,
              payload.utf8.count <= 512,
              payload.utf8.allSatisfy({ (32...126).contains($0) })
        else { throw WalletError.invalidPayload }
        self.id = id
        self.name = title
        self.payload = payload
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        try self.init(id: values.decode(UUID.self, forKey: .id),
                      name: values.decode(String.self, forKey: .name),
                      payload: values.decode(String.self, forKey: .payload))
    }
}

enum WalletError: LocalizedError {
    case invalidName, invalidPayload, vaultUnavailable, tooLarge, keyUnavailable

    var errorDescription: String? {
        switch self {
        case .invalidName: return "Enter a card name of up to 160 UTF-8 bytes."
        case .invalidPayload: return "Enter up to 512 printable ASCII characters for the QR code."
        case .vaultUnavailable: return "Kura could not read this wallet. Your saved file has been kept."
        case .tooLarge: return "This wallet exceeds the supported file size."
        case .keyUnavailable: return "Kura could not access the wallet key. Check that a device passcode is set and try again."
        }
    }
}
