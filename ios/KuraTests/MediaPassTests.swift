import CoreImage
import ImageIO
import UniformTypeIdentifiers
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

    func testCroppedPhotoHonorsOrientationAndDropsSourceMetadata() async throws {
        let extent = CGRect(x: 0, y: 0, width: 180, height: 100)
        let pixels = CIImage(color: CIColor(red: 0, green: 0.5, blue: 1)).cropped(to: extent)
        let source = try XCTUnwrap(CIContext().createCGImage(pixels, from: extent))
        let file = NSMutableData()
        let writer = try XCTUnwrap(CGImageDestinationCreateWithData(file, UTType.jpeg.identifier as CFString, 1, nil))
        let metadata: [CFString: Any] = [
            kCGImagePropertyOrientation: 6,
            kCGImagePropertyTIFFDictionary: [kCGImagePropertyTIFFArtist: "Synthetic Artist"],
            kCGImagePropertyGPSDictionary: [kCGImagePropertyGPSLatitude: 1.0, kCGImagePropertyGPSLatitudeRef: "N"]
        ]
        CGImageDestinationAddImage(writer, source, metadata as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(writer))

        let oriented = try MediaProcessor.thumbnail(file as Data)
        XCTAssertEqual(oriented.width, 100)
        XCTAssertEqual(oriented.height, 180)
        let crop = PhotoCrop(shape: .square, zoom: 2, autoAlign: false)
        let image = try await MediaProcessor.cropped(PreparedPhoto(original: oriented, aligned: nil), using: crop)
        XCTAssertEqual(image.width, 50)
        XCTAssertEqual(image.height, 50)
        let encoded = try await MediaProcessor.jpeg(image)
        let result = try WalletMedia.imageSource(encoded)
        let properties = try XCTUnwrap(CGImageSourceCopyPropertiesAtIndex(result, 0, nil) as? [CFString: Any])
        XCTAssertNil(properties[kCGImagePropertyGPSDictionary])
        XCTAssertNil((properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any])?[kCGImagePropertyTIFFArtist])
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

    func testPassIdentitySurvivesImportFromDataSlice() throws {
        let json = try document("Entry", additions: ["passTypeIdentifier": "pass.example.club", "serialNumber": "001"])
        let archive = try PassArchive.pack(["pass.json": json])
        let slice = (Data([0xff]) + archive).dropFirst()
        XCTAssertNotEqual(slice.startIndex, 0)
        let pass = try XCTUnwrap(PassImport.parse(slice).first)
        XCTAssertEqual(pass.appearance.passTypeIdentifier, "pass.example.club")
        XCTAssertEqual(pass.serialNumber, "001")
    }

    func testDuplicateReviewUsesIssuerAndSerialWithoutOverwriting() throws {
        func pass(_ issuer: String, _ serial: String) throws -> ImportedPass {
            let bytes = try document("Club", additions: ["passTypeIdentifier": issuer, "serialNumber": serial])
            return try XCTUnwrap(PassImport.parse(PassArchive.pack(["pass.json": bytes])).first)
        }
        let saved = try pass("pass.example.A", "0007").makeCard()
        let unchanged = saved
        let first = try pass("pass.example.B", "0007")
        let second = try pass("pass.example.B", "0007")
        let existing = try pass("pass.example.A", "0007")
        let missing = try pass("", "0007")
        XCTAssertEqual(PassImport.duplicates([first, existing, missing, second], existing: [saved]), [existing.id, second.id])
        XCTAssertNotEqual(try existing.makeCard().id, saved.id)
        XCTAssertEqual(saved, unchanged)
    }

    func testManualPassFieldsValidateAndRoundTripAsReadOnlyIssuerFields() async throws {
        var style = PassAppearance.manual(category: "Transit")
        style.organization = "Example trains"
        style.barcodeAlternative = "Show this at the gate"
        style.groups["headerFields"] = [PassField(key: "p", label: "Price", value: "13.50", numberValue: true, currencyCode: "USD")]
        style.groups["backFields"] = [PassField(key: "a", label: "Platform", value: "0007")]
        let card = try WalletCard(name: "Day ticket", payload: "0007", section: .passes, passStyle: style)
        XCTAssertFalse(card.isImportedPass)
        let bytes = try await PassExport.encode(card)
        let result = try XCTUnwrap(PassImport.parse(bytes).first).makeCard()
        XCTAssertTrue(result.isImportedPass)
        XCTAssertEqual(result.passStyle?.type, "boardingPass")
        XCTAssertEqual(result.passStyle?.transitType, "PKTransitTypeTrain")
        XCTAssertEqual(result.passStyle?.barcodeAlternative, style.barcodeAlternative)
        XCTAssertEqual(result.passStyle?.groups["backFields"]?.first?.value, "0007")
        let price = try XCTUnwrap(result.passStyle?.groups["headerFields"]?.first)
        XCTAssertEqual(price.currencyCode, "USD")
        XCTAssertEqual(price.numberValue, true)
        XCTAssertEqual(Decimal(string: price.value), Decimal(string: "13.50"))
        style.groups["headerFields"]?[0].value = "13 to 15"
        XCTAssertThrowsError(try style.validate())
        style.groups["headerFields"] = [PassField(key: "p", label: "Date", value: "tomorrow", dateStyle: "PKDateStyleMedium")]
        XCTAssertThrowsError(try style.validate())
        style.groups["headerFields"]?[0].value = "2030-04-23T10:00:00Z"
        XCTAssertNoThrow(try style.validate())
    }

    func testIssuerFormattingHintsUseUTF8ByteLimits() throws {
        let boundary = String(repeating: "é", count: 16)
        for key in ["currencyCode", "numberStyle", "dateStyle", "timeStyle"] {
            func parse(_ hint: String) throws -> PassField {
                let field = ["key": "seat", "label": "Seat", "value": "12", key: hint]
                let descriptor = try document("Train", additions: ["storeCard": ["primaryFields": [field]]])
                let pass = try PassImport.parse(PassArchive.pack(["pass.json": descriptor]))
                return try XCTUnwrap(pass.first?.appearance.groups["primaryFields"]?.first)
            }
            XCTAssertEqual(try parse(boundary).passJSON[key] as? String, boundary)
            XCTAssertThrowsError(try parse(boundary + "x")) { error in
                XCTAssertEqual(error as? WalletMediaError, .damagedPass)
            }
        }
    }

    func testPassBackLinksOfferOnlyExplicitWebLinksAndTelephoneNumbers() {
        let text = "Visit https://example.org/track/123456789. Call +1 (415) 555-0123. " +
                   "See https://example.org/track/123456789 or mailto:a@example.org, custom://wallet or file:///tmp/test."
        XCTAssertEqual(PassLinks.destinations(in: text).map(\.absoluteString),
                       ["https://example.org/track/123456789", "tel:+14155550123"])
        XCTAssertTrue(PassLinks.destinations(in: "javascript:alert(1) 12345 user@example.org").isEmpty)
    }

    func testManualExpiryUsesLocalCalendarEndAndColorSurvivesExport() async throws {
        let card = try WalletCard(name: "Tokyo pass", payload: "01", fields: ["Card color": "#4488aa"],
                                  expires: XCTUnwrap(PassImport.date("2030-02-03T01:30:00Z")))
        let zone = try XCTUnwrap(TimeZone(secondsFromGMT: 9 * 3600))
        let bytes = try await PassExport.encode(card, timeZone: zone)
        let exported = try XCTUnwrap(PassImport.parse(bytes).first).makeCard()
        let end = try XCTUnwrap(exported.expires)
        let expected = try XCTUnwrap(PassImport.date("2030-02-03T14:59:59.999Z"))
        XCTAssertEqual(end.timeIntervalSince(expected), 0, accuracy: 0.001)
        XCTAssertEqual(exported.passStyle?.backgroundColor, "#4488aa")
        let again = try await PassExport.encode(exported, timeZone: TimeZone(secondsFromGMT: 0)!)
        XCTAssertEqual(try PassImport.parse(again).first?.expires, exported.expires)
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

    func testScanCardMatchesDisplayedBasicFieldsWithoutGuessing() throws {
        let available = ["Card name": "", "Number": "", "Network": "", "Cardholder": "", "Issuer": "Existing", "Expiry": ""]
        let lines = ["Card name: Sample gold", "カ ー ド 番 号：000123", "Payment network: Sample network",
                     "Name on card: Example User", "有効期限：08/35"]
        let result = MediaProcessor.suggestions(lines: lines, fields: available)
        XCTAssertEqual(result, ["Card name": "Sample gold", "Number": "000123", "Network": "Sample network",
                                "Cardholder": "Example User", "Expiry": "08/35"])
        XCTAssertNil(MediaProcessor.suggestions(lines: ["Card name: Store rewards"], fields: ["Issuer": ""])["Issuer"])
        XCTAssertTrue(MediaProcessor.suggestions(lines: ["000123", "08/35", "EXAMPLE GOLD"], fields: available).isEmpty)
        XCTAssertNil(MediaProcessor.suggestions(lines: ["Card No: 0001", "Card No: 0002"], fields: available)["Number"])
        let month = try XCTUnwrap(RecognizedExpiry.parse("08/35"))
        XCTAssertTrue(month.monthOnly)
        XCTAssertEqual(WalletDateFormat.month.formatter.string(from: month.date), "08/35")
        XCTAssertFalse(try XCTUnwrap(RecognizedExpiry.parse("2035-08-31")).monthOnly)
        XCTAssertTrue(try XCTUnwrap(RecognizedExpiry.parse("2035年08月")).monthOnly)
        XCTAssertNil(RecognizedExpiry.parse("13/35"))
        XCTAssertNil(RecognizedExpiry.parse("2035-02-30"))
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
