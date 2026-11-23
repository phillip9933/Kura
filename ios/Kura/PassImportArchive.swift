import Foundation
import ZIPFoundation

enum PassArchive {
    static let inputLimit = 12 * 1024 * 1024
    static let expandedLimit = 10 * 1024 * 1024
    static let jsonLimit = 500 * 1024

    struct Budget {
        var bytes = expandedLimit
        var entries = 128
    }

    // A bounded preflight restricts the reader to ordinary, single-disk ZIP files
    // and detects a directory iterator stopping before all declared entries.
    private static func entryCount(in data: Data) throws -> Int {
        let count = data.count
        guard (22...inputLimit).contains(count) else { throw WalletMediaError.damagedPass }
        func u16(_ offset: Int) -> Int {
            Int(data[offset]) | (Int(data[offset + 1]) << 8)
        }
        func u32(_ offset: Int) -> Int { u16(offset) | (u16(offset + 2) << 16) }
        for i in stride(from: count - 22, through: max(0, count - 22 - 65535), by: -1) {
            guard data[i] == 0x50, u32(i) == 0x06054b50, i + 22 + u16(i + 20) == count else { continue }
            let entries = u16(i + 10)
            let directory = u32(i + 16)
            guard u16(i + 4) == 0, u16(i + 6) == 0, u16(i + 8) == entries,
                  (1...128).contains(entries), directory + u32(i + 12) == i,
                  directory < i, i < 20 || u32(i - 20) != 0x07064b50
            else { throw WalletMediaError.damagedPass }

            var cursor = directory
            for _ in 0..<entries {
                guard cursor + 46 <= i, u32(cursor) == 0x02014b50,
                      u16(cursor + 6) < 45, u16(cursor + 8) & 1 == 0, u16(cursor + 34) == 0
                else { throw WalletMediaError.damagedPass }
                let compressed = u32(cursor + 20), expanded = u32(cursor + 24), local = u32(cursor + 42)
                let nameLength = u16(cursor + 28)
                guard nameLength > 0, nameLength <= 512,
                      local + 30 <= directory, u32(local) == 0x04034b50,
                      u16(local + 4) < 45, u16(local + 6) == u16(cursor + 8),
                      u16(local + 8) == u16(cursor + 10), [0, 8].contains(u16(local + 8)),
                      expanded <= expandedLimit, compressed <= inputLimit,
                      local + 30 + u16(local + 26) + u16(local + 28) + compressed <= directory
                else { throw WalletMediaError.damagedPass }
                cursor += 46 + nameLength + u16(cursor + 30) + u16(cursor + 32)
            }
            guard cursor == i else { throw WalletMediaError.damagedPass }
            return entries
        }
        throw WalletMediaError.damagedPass
    }

    static func unpack(_ input: Data, budget: inout Budget) throws -> [String: Data] {
        guard input.count <= inputLimit else { throw WalletMediaError.size }
        let data = input.startIndex == 0 ? input : Data(input)
        let expected = try entryCount(in: data)
        guard expected <= budget.entries else { throw WalletMediaError.size }
        budget.entries -= expected
        let archive = try Archive(data: data, accessMode: .read)
        var files: [String: Data] = [:]
        var seen: Set<String> = []
        for entry in archive {
            try Task.checkCancellation()
            let path = entry.path.hasSuffix("/") ? String(entry.path.dropLast()) : entry.path
            let components = path.components(separatedBy: "/")
            guard !path.isEmpty, path.utf8.count <= 512, !path.contains("\\"), !path.contains(":"),
                  path.rangeOfCharacter(from: .controlCharacters) == nil,
                  components.allSatisfy({ !$0.isEmpty && $0 != "." && $0 != ".." }),
                  seen.insert(path).inserted, entry.type != .symlink else { throw WalletMediaError.damagedPass }
            if entry.type == .directory {
                guard entry.uncompressedSize == 0 else { throw WalletMediaError.damagedPass }
                continue
            }
            let limit = ["pass.json", "pass.strings", "manifest.json"].contains(components.last ?? "")
                ? min(jsonLimit, budget.bytes) : budget.bytes
            guard entry.uncompressedSize <= UInt64(limit) else { throw WalletMediaError.size }
            var content = Data()
            let crc = try archive.extract(entry) { chunk in
                try Task.checkCancellation()
                guard chunk.count <= limit - content.count else { throw WalletMediaError.size }
                content.append(chunk)
            }
            guard crc == entry.checksum, content.count == Int(entry.uncompressedSize)
            else { throw WalletMediaError.damagedPass }
            budget.bytes -= content.count
            files[path] = content
        }
        guard seen.count == expected, !files.isEmpty else { throw WalletMediaError.damagedPass }
        return files
    }

    static func pack(_ files: [String: Data]) throws -> Data {
        let archive = try Archive(accessMode: .create)
        guard files.count <= 128, files.values.reduce(0, { $0 + $1.count }) <= expandedLimit
        else { throw WalletMediaError.size }
        for path in files.keys.sorted() {
            guard let data = files[path] else { throw WalletMediaError.invalidFile }
            try archive.addEntry(with: path, type: .file, uncompressedSize: Int64(data.count),
                                 compressionMethod: .deflate) { offset, size in
                try Task.checkCancellation()
                let start = Int(offset)
                return data.subdata(in: start..<min(start + size, data.count))
            }
        }
        guard let result = archive.data, result.count <= inputLimit else { throw WalletMediaError.size }
        return result
    }
}
