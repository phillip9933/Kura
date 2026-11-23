import CryptoKit
import SwiftUI
import XCTest
@testable import Kura

final class MediaPassTests: XCTestCase {
    private func document(_ title: String, additions: [String: Any] = [:]) throws -> Data {
        var json: [String: Any] = [
            "formatVersion": 1, "organizationName": "Example club", "description": title,
            "storeCard": ["primaryFields": [["key": "member", "label": "Member", "value": "0007"]]],
            "barcodes": [["format": "PKBarcodeFormatQR", "message": " 0007 ", "messageEncoding": "utf-8"]]
        ]
        json.merge(additions) { _, new in new }
        return try JSONSerialization.data(withJSONObject: json)
    }

    func testPassBundleUsesOneBudgetAndLocalizesFieldsWithoutChangingBarcode() throws {
        let pass = try document("Club")
        let files = ["pass.json": pass, "ja.lproj/pass.strings": Data(#""Club" = "クラブ"; "Member" = "会員";"#.utf8)]
        let manifest = try JSONSerialization.data(withJSONObject: files.mapValues(PassImport.manifestDigest))
        let nested = try PassArchive.pack(files.merging(["manifest.json": manifest]) { _, b in b })
        let bundle = try PassArchive.pack(["second.pkpass": nested, "first.pkpass": nested])
        let results = try PassImport.parse(bundle, languages: ["ja-JP"])
        XCTAssertEqual(results.count, 2)
        let item = try XCTUnwrap(results.first)
        XCTAssertTrue(item.manifestChecked)
        XCTAssertEqual(item.name, "クラブ")
        XCTAssertEqual(item.payload, " 0007 ")
        XCTAssertEqual(item.barcodeFormat, .qr)
        XCTAssertEqual(item.appearance.groups["primaryFields"]?.first?.label, "会員")
        XCTAssertEqual(item.appearance.groups["primaryFields"]?.first?.value, "0007")
        XCTAssertEqual(try item.makeCard().section, .cards)
    }

    func testRejectsIncompleteManifestAmbiguousBundleAndMalformedArchive() throws {
        let pass = try document("Test")
        let manifest = try JSONSerialization.data(withJSONObject: ["pass.json": PassImport.manifestDigest(pass)])
        let incomplete = try PassArchive.pack(["pass.json": pass, "manifest.json": manifest, "extra.txt": Data([1])])
        XCTAssertThrowsError(try PassImport.parse(incomplete))
        let inner = try PassArchive.pack(["pass.json": pass])
        XCTAssertThrowsError(try PassImport.parse(PassArchive.pack(["pass.json": pass, "inner.pkpass": inner])))
        XCTAssertThrowsError(try PassImport.parse(Data(inner.dropLast())))
        XCTAssertThrowsError(try PassImport.parse(PassArchive.pack(["../pass.json": pass])))
    }

    func testRejectsNestedAndExpandedContentOverBudget() throws {
        var archive = try PassArchive.pack(["pass.json": document("Test")])
        for _ in 0..<4 { archive = try PassArchive.pack(["nested.pkpasses": archive]) }
        XCTAssertThrowsError(try PassImport.parse(archive))
        let data = Data(repeating: 1, count: 1024)
        let input = try PassArchive.pack(["content": data])
        var budget = PassArchive.Budget()
        budget.bytes = 512
        XCTAssertThrowsError(try PassArchive.unpack(input, budget: &budget))
    }

    func testExportedPassReturnsOriginalUserFieldsAndBarcode() async throws {
        let source = try WalletCard(name: "Test entry", payload: "00001", section: .identities,
                                    category: "Student ID", fields: ["Campus": "North"])
        let encoded = try await PassExport.encode(source)
        let rows = try PassImport.parse(encoded)
        let imported = try XCTUnwrap(rows.first)
        let restored = try imported.makeCard()
        XCTAssertTrue(imported.manifestChecked)
        XCTAssertEqual(restored.section, .identities)
        XCTAssertEqual(restored.name, source.name)
        XCTAssertEqual(restored.payload, source.payload)
        XCTAssertEqual(restored.category, "Student ID")
        XCTAssertEqual(restored.fields, source.fields)
    }

    func testCropKeepsPreviewBoundsAndFullPhotoProportions() {
        let bounds = CGRect(x: 0, y: 0, width: 1600, height: 900)
        for shape in CropShape.allCases {
            for position in [CGFloat(-1), 0, 1] {
                let crop = PhotoCrop(shape: shape, zoom: 2.5, x: position, y: position)
                XCTAssertTrue(bounds.contains(crop.rect(in: bounds)))
                XCTAssertGreaterThan(crop.rect(in: bounds).height, 0)
            }
        }
        XCTAssertEqual(PhotoCrop(shape: .original).rect(in: bounds), bounds)
    }

    func testAngledCropNeverExposesCornersOutsideSource() {
        let bounds = CGRect(x: 0, y: 0, width: 1600, height: 901)
        for shape in CropShape.allCases {
            for angle in [CGFloat(-45), -17, 23, 45] {
                for x in [CGFloat(-1), 1] {
                    for y in [CGFloat(-1), 1] {
                        let crop = PhotoCrop(shape: shape, x: x, y: y, angleDegrees: angle)
                        let rect = crop.rect(in: bounds)
                        let inverse = CGAffineTransform(rotationAngle: -angle * .pi / 180)
                        for cx in [rect.minX, rect.maxX] {
                            for cy in [rect.minY, rect.maxY] {
                                let point = CGPoint(x: cx, y: cy).applying(inverse)
                                XCTAssertTrue(bounds.insetBy(dx: -0.001, dy: -0.001).contains(point))
                            }
                        }
                    }
                }
            }
        }
    }

    func testRelevantAndExpirationDatesAreIndependent() throws {
        let json = try document("Reserved seat", additions: [
            "relevantDate": "2030-09-21T19:00:00+09:00", "expirationDate": "2030-09-22T03:00:00.125Z"
        ])
        let item = try XCTUnwrap(PassImport.parse(PassArchive.pack(["pass.json": json])).first)
        let start = try XCTUnwrap(item.appearance.relevantDate)
        let expiry = try XCTUnwrap(item.expires)
        XCTAssertEqual(expiry.timeIntervalSince(start), 17 * 3600 + 0.125, accuracy: 0.001)
    }

    func testFieldRecognitionRequiresUniqueLabelledValueAndPreservesZeros() {
        let fields = ["Member number": "", "Member name": "Already filled", "Expiry": "", "Email": ""]
        let lines = ["会 員 番 号：00013", "Name: Example", "Expires: 2030-01", "Expires: 2031-09", "untagged"]
        XCTAssertEqual(MediaProcessor.suggestions(lines: lines, fields: fields), ["Member number": "00013"])
    }

    func testAttachmentsAndImagesValidateBeforeReplacingDraft() throws {
        var media = WalletMedia()
        let attachment = try WalletAttachment(name: "Synthetic.txt", bytes: Data("test".utf8))
        try media.add(attachment)
        XCTAssertThrowsError(try media.add(attachment))
        XCTAssertThrowsError(try WalletAttachment(name: "../private.txt", bytes: Data([1])))
        XCTAssertThrowsError(try media.replace(.front, with: Data([1, 2, 3])))
        XCTAssertNil(media.front)
        XCTAssertEqual(media.attachments.count, 1)
        let saved = try JSONEncoder().encode(media)
        XCTAssertEqual(try JSONDecoder().decode(WalletMedia.self, from: saved), media)
        media.removeAttachment(attachment.id)
        XCTAssertTrue(media.attachments.isEmpty)
    }
}
