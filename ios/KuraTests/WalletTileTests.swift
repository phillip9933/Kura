import XCTest
@testable import Kura

final class WalletTileTests: XCTestCase {
    private func instant(_ raw: String) throws -> Date { try XCTUnwrap(ISO8601DateFormatter().date(from: raw)) }

    func testManualDaysLastThroughMidnightInLocalZoneIncludingDST() throws {
        let zone = try XCTUnwrap(TimeZone(identifier: "America/New_York"))
        let card = try WalletCard(name: "Concert", payload: "", section: .passes, fields: ["Event date": "2026-03-08"])
        let lastSecond = try instant("2026-03-09T03:59:59Z")
        let midnight = try instant("2026-03-09T04:00:00Z")
        let end = try XCTUnwrap(WalletUpcoming.date(for: card, now: lastSecond, timeZone: zone))
        XCTAssertGreaterThan(end, lastSecond)
        XCTAssertLessThan(end, midnight)
        XCTAssertNil(WalletUpcoming.date(for: card, now: midnight, timeZone: zone))
    }

    func testRelevantTimeTakesPriorityAndExpiryAndArchiveExcludePasses() throws {
        let now = try instant("2026-11-23T00:00:00Z")
        let zone = try XCTUnwrap(TimeZone(identifier: "Asia/Tokyo"))
        let future = try instant("2026-11-24T02:00:00Z")
        var card = try WalletCard(name: "Boarding pass", payload: "P", section: .passes,
                                  fields: ["Relevant date": "2026-11-24T11:00"], expires: future.addingTimeInterval(86400))
        XCTAssertEqual(WalletUpcoming.date(for: card, now: now, timeZone: zone), future)
        card.fields["Relevant date"] = "2026-11-24T11:00:00+09:00"
        XCTAssertEqual(WalletUpcoming.date(for: card, now: now, timeZone: zone), future)
        card.fields["Relevant date"] = "2026-11-22T00:00:00Z"
        XCTAssertNil(WalletUpcoming.date(for: card, now: now, timeZone: zone))
        card.fields = [:]
        XCTAssertNotNil(WalletUpcoming.date(for: card, now: now, timeZone: zone))
        card.passStyle = PassAppearance(type: "boardingPass", logoText: "", organization: "", transitType: "", serialNumber: "",
                                        relevantDate: future, groups: [:], artwork: [:])
        XCTAssertEqual(WalletUpcoming.date(for: card, now: now), future)
        card.expires = now.addingTimeInterval(-1)
        XCTAssertNil(WalletUpcoming.date(for: card, now: now))
        card.expires = nil
        card.archived = true
        XCTAssertNil(WalletUpcoming.date(for: card, now: now))
        card.archived = false
        card.section = .cards
        XCTAssertNil(WalletUpcoming.date(for: card, now: now))
    }

    func testInvalidOrAbsentScheduleDoesNotCreateUpcomingPass() throws {
        let card = try WalletCard(name: "Undated ticket", payload: "", section: .passes,
                                  fields: ["Event date": "2026-02-30", "Seat": "2028-10-22"])
        XCTAssertNil(WalletUpcoming.date(for: card, now: try instant("2026-01-01T00:00:00Z")))
    }

    func testColorSyntaxAndContrastWithTransparentAndUnreadableText() throws {
        XCTAssertEqual(WalletTileColor.parse("#FF000000"), .black)
        XCTAssertEqual(WalletTileColor.parse("0xFFffffff"), .white)
        XCTAssertEqual(WalletTileColor.parse("RGBA(0, 0, 0, 0.5)"), WalletTileColor(red: 0, green: 0, blue: 0, alpha: 0.5))
        for invalid in ["#gg0000", "rgb(256,0,0)", "rgba(1,2,3,1.2)", "rgb(1,,3)"] {
            XCTAssertNil(WalletTileColor.parse(invalid))
        }
        let pass = try WalletCard(name: "Museum", payload: "", section: .passes, fields: [
            "Background color": "rgb(255,255,255)", "Text color": "#eeeeee", "Label color": "#000080"
        ])
        let style = WalletTileStyle(card: pass)
        XCTAssertEqual(style.background, .white)
        XCTAssertEqual(style.foreground, .black)
        XCTAssertEqual(style.label, WalletTileColor.parse("#000080"))
        let identity = try WalletCard(name: "ID", payload: "", section: .identities, fields: ["Card color": "#80FFFFFF"])
        let backdrop = WalletTileStyle(card: identity).background
        XCTAssertGreaterThan(backdrop.red, WalletTileColor.base.red)
        XCTAssertLessThan(backdrop.red, 1)
        XCTAssertGreaterThanOrEqual(backdrop.contrast(with: WalletTileStyle(card: identity).foreground), 4.5)
    }

    func testMoneyUsesCurrencyMinorUnitsAndBillingDayUsesOrdinal() throws {
        let us = Locale(identifier: "en_US")
        let card = try WalletCard(name: "Prepaid", payload: "not the card number", fields: [
            "Balance": "10.505", "Billing day": "22", "Number": "1234-5678-1234-0091"
        ])
        let dollars = WalletTileDetail.payment(for: card, currency: "USD", locale: us)
        XCTAssertEqual(dollars, [.init(label: "Balance", value: "$10.51"), .init(label: "Billing day", value: "22nd")])
        let yen = WalletTileDetail.payment(for: card, currency: "JPY", locale: us)
        XCTAssertEqual(yen.first?.value, "¥11")
        XCTAssertEqual(WalletTileDetail.maskedNumber(for: card), "•••• 0091")
        var noNumber = card
        noNumber.fields = ["Balance": "1x", "billdate": "32"]
        XCTAssertNil(WalletTileDetail.maskedNumber(for: noNumber))
        XCTAssertEqual(WalletTileDetail.payment(for: noNumber, currency: "USD", locale: us), [.init(label: "Balance", value: "1x")])
    }
}
