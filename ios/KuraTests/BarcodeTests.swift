import CoreGraphics
import Vision
import XCTest
@testable import Kura

final class BarcodeTests: XCTestCase {
    func testEveryAndroidFormatRendersAndUnknownPassTypesStayUnknown() async {
        let samples: [BarcodeFormat: String] = [
            .qr: " 0000123 ", .aztec: "Kura", .pdf417: "日本-0042", .code128: "0000123",
            .dataMatrix: "A0042", .code39: "TEST0042", .code93: "KURA-42", .ean8: "55123457",
            .ean13: "4006381333931", .upca: "036000291452", .upce: "01234565", .itf: "00001234",
            .codabar: "1234", .itf14: "10012345000017", .gs1128: "(01)09501101530003",
            .isbn: "0-306-40615-2", .telepen: "KURA42", .postnet: "12345",
            .rm4scc: "AB123", .ean2: "12", .ean5: "51234"
        ]
        XCTAssertEqual(Set(BarcodeFormat.allCases), Set(samples.keys))
        for (format, value) in samples {
            let image = await CardQR.shared.image(for: value, format: format)
            XCTAssertNotNil(image, format.rawValue)
        }
        XCTAssertNil(BarcodeFormat(passKitName: "unsupported"))
        XCTAssertEqual(BarcodeFormat(passKitName: "PKBarcodeFormatAztec"), .aztec)
    }

    func testImpossibleBarcodeFailsWithoutFallbackToQR() async {
        for (format, value) in [(BarcodeFormat.ean2, "1"), (.ean5, "123456"), (.ean13, "4006381333930"),
                                (.postnet, "123456"), (.itf, "123"), (.itf14, "10012345000018"),
                                (.qr, String(repeating: "a", count: 8193))] {
            let image = await CardQR.shared.image(for: value, format: format)
            XCTAssertNil(image, format.rawValue)
        }
        XCTAssertNil(ScannedBarcode(payload: "value", format: nil))
        XCTAssertEqual(ScannedBarcode(payload: " 0042 ", format: .code128)?.payload, " 0042 ")
    }

    func testAppleVisionReadsCommonPassFormats() async throws {
        let payload = "KURA-00001842"
        for (format, type) in [(BarcodeFormat.aztec, VNBarcodeSymbology.aztec),
                               (.pdf417, .pdf417), (.code128, .code128), (.dataMatrix, .dataMatrix)] {
            let rendered = await CardQR.shared.image(for: payload, format: format)
            let image = try XCTUnwrap(rendered)
            let found = try BarcodePhoto.observations(in: image, symbologies: [type])
            XCTAssertEqual(found.first?.payloadStringValue, payload, format.rawValue)
            XCTAssertEqual(found.first.flatMap { BarcodeFormat(vision: $0.symbology) }, format, format.rawValue)
        }
    }

    func testPostalAndSupplementalPatternsMatchAndroid() async throws {
        // Reference bar states and runs from OkapiBarcode 0.5.6, the Android app's encoder.
        let samples: [(BarcodeFormat, String, String)] = [
            (.postnet, "12345", "FDDDFFDDFDFDDFFDDFDDFDFDFDDFDFDF"),
            (.rm4scc, "AB123", "ADADADFTATDAFTDFADTAFTDAFF"),
            (.ean2, "12", "10110011001010010011"),
            (.ean5, "51234", "10110110001010011001010011011010111101010011101")
        ]
        for (format, value, expected) in samples {
            let rendered = await CardQR.shared.image(for: value, format: format)
            let image = try XCTUnwrap(rendered)
            let bytes = try XCTUnwrap(image.dataProvider?.data)
            let pixels = try XCTUnwrap(CFDataGetBytePtr(bytes))
            let bars = (0..<image.width).map { x -> ClosedRange<Int>? in
                let marked = (0..<image.height).filter { pixels[$0 * image.bytesPerRow + x] == 0 }
                guard let first = marked.first, let last = marked.last else { return nil }
                return first...last
            }
            let first = try XCTUnwrap(bars.firstIndex(where: { $0 != nil }))
            let last = try XCTUnwrap(bars.lastIndex(where: { $0 != nil }))
            let ink = bars[first...last]
            if format == .ean2 || format == .ean5 {
                var runs: [Int] = []
                var previous = false
                for column in ink {
                    let black = column != nil
                    if black != previous { runs.append(0); previous = black }
                    runs[runs.count - 1] += 1
                }
                let module = try XCTUnwrap(runs.min())
                let bits = runs.enumerated().map { index, length in
                    String(repeating: index.isMultiple(of: 2) ? "1" : "0", count: length / module)
                }.joined()
                XCTAssertEqual(bits, expected)
            } else {
                let top = ink.compactMap { $0?.lowerBound }.min()
                let bottom = ink.compactMap { $0?.upperBound }.max()
                var states = ""
                var previous: ClosedRange<Int>?
                for bar in ink {
                    if let bar, bar != previous {
                        states += bar.lowerBound == top ? (bar.upperBound == bottom ? "F" : "A")
                                                     : (bar.upperBound == bottom ? "D" : "T")
                    }
                    previous = bar
                }
                XCTAssertEqual(states, expected)
            }
        }
    }
}
