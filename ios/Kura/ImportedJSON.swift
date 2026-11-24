import Foundation

enum ImportedJSON {
    private static let maximumDepth = 32

    // Bound container depth before Foundation parses unknown fields as well as known ones.
    // The wallet and pass formats use UTF-8. A literal zero byte is invalid in UTF-8 JSON
    // and could otherwise let Foundation interpret these bytes as UTF-16 or UTF-32.
    static func hasSupportedDepth(_ data: Data) -> Bool {
        data.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            var depth = 0
            var quoted = false
            var escaped = false
            for byte in bytes {
                guard byte != 0 else { return false }
                if quoted {
                    if escaped { escaped = false }
                    else if byte == 0x5c { escaped = true }
                    else if byte == 0x22 { quoted = false }
                } else {
                    switch byte {
                    case 0x22: quoted = true
                    case 0x5b, 0x7b:
                        depth += 1
                        guard depth <= maximumDepth else { return false }
                    case 0x5d, 0x7d:
                        depth -= 1
                        guard depth >= 0 else { return false }
                    default: break
                    }
                }
            }
            return depth == 0 && !quoted
        }
    }
}
