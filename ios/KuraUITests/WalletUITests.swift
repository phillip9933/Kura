import UIKit
import XCTest
#if targetEnvironment(simulator)
import notify
#endif

/// Run on the suite's clean simulator with default authentication preferences.
final class WalletUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testLaunchAndForegroundExposeOnlyTheLockedWallet() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        assertLocked(app)
        XCUIDevice.shared.press(.home)
        app.activate()
        assertLocked(app)
    }

    @MainActor
    func testUnlockControlAtLargeTextAndRelaunchDuringAuthentication() throws {
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"
        ]
        app.launch()
        assertLocked(app)
        capture("lock-large-text", app)
        if #available(iOS 17.0, *) {
            try app.performAccessibilityAudit(for: [.dynamicType, .textClipped])
        }
        app.buttons["Unlock wallet"].tap()
        // A simulator can show an OS prompt or reject authentication when no passcode is set.
        // Terminating without granting either method must never reopen a previous session.
        app.terminate()
        app.launch()
        assertLocked(app)
    }

    #if targetEnvironment(simulator)
    @MainActor
    func testCreateFavoriteAndReopenAfterRealSimulatorAuthentication() throws {
        // These simulator notifications correspond to Features > Face ID in Simulator.app.
        let enrollment = "com.apple.BiometricKit.enrollmentChanged"
        var token: Int32 = 0
        XCTAssertEqual(notify_register_check(enrollment, &token), 0)
        defer { notify_cancel(token) }
        var previous: UInt64 = 0
        XCTAssertEqual(notify_get_state(token, &previous), 0)
        defer {
            notify_set_state(token, previous)
            notify_post(enrollment)
        }
        XCTAssertEqual(notify_set_state(token, 1), 0)
        XCTAssertEqual(notify_post(enrollment), 0)

        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        try unlockWithBiometrics(app)
        XCUIDevice.shared.orientation = .portrait
        defer { XCUIDevice.shared.orientation = .portrait }
        let name = "Library " + String(UUID().uuidString.prefix(8))
        let addMenu = app.descendants(matching: .any).matching(identifier: "wallet.addMenu").firstMatch
        XCTAssertTrue(addMenu.waitForExistence(timeout: 5))
        addMenu.tap()
        let scanCard = app.buttons["Scan card"]
        XCTAssertTrue(scanCard.waitForExistence(timeout: 5))
        scanCard.tap()
        XCTAssertTrue(app.buttons["Take a front photo"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Choose a front photo"].exists)
        XCTAssertFalse(app.navigationBars.buttons["Save"].isEnabled)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Cards"].waitForExistence(timeout: 5))
        let addItem = app.buttons["Add item"]
        if !addItem.exists {
            addMenu.tap()
        }
        XCTAssertTrue(addItem.waitForExistence(timeout: 5))
        addItem.tap()
        let field = app.textFields["Name"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        field.typeText(name)
        app.navigationBars.buttons["Save"].tap()

        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 8))
        capture("wallet-portrait", app)
        row.tap()
        XCTAssertTrue(app.navigationBars[name].waitForExistence(timeout: 5))
        XCUIDevice.shared.orientation = .landscapeLeft
        waitForLandscape(app, title: name)
        capture("item-landscape", app)
        XCUIDevice.shared.orientation = .portrait
        let favorite = app.buttons["Add to favorites"]
        reveal(favorite, in: app)
        favorite.tap()
        XCTAssertTrue(app.buttons["Remove from favorites"].waitForExistence(timeout: 5))

        app.navigationBars.buttons.element(boundBy: 0).tap()
        setWalletAppearance("Dark", in: app)
        app.terminate()
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"]
        app.launch()
        assertLocked(app)
        try unlockWithBiometrics(app)
        XCTAssertTrue(row.waitForExistence(timeout: 8))
        XCTAssertTrue(row.isHittable)
        capture("wallet-large-text", app, verifyDarkBackground: true)
        row.tap()
        reveal(app.buttons["Remove from favorites"], in: app)

        // Remove only the record created by this test.
        let delete = app.buttons["Delete item"]
        reveal(delete, in: app)
        delete.tap()
        let confirmation = app.sheets.buttons["Delete item"]
        if confirmation.waitForExistence(timeout: 2) { confirmation.tap() }
        else { app.alerts.buttons["Delete item"].tap() }
        XCTAssertTrue(app.navigationBars["Cards"].waitForExistence(timeout: 5))
        XCTAssertFalse(row.exists)
        setWalletAppearance("System", in: app)
    }

    @MainActor
    private func setWalletAppearance(_ choice: String, in app: XCUIApplication) {
        let options = app.descendants(matching: .any).matching(identifier: "Options").firstMatch
        XCTAssertTrue(options.waitForExistence(timeout: 5))
        options.tap()
        let settings = app.buttons["Settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        let picker = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Appearance")).firstMatch
        reveal(picker, in: app)
        picker.tap()
        let option = app.descendants(matching: .any).matching(identifier: choice).firstMatch
        XCTAssertTrue(option.waitForExistence(timeout: 5))
        option.tap()
        app.navigationBars.buttons["Save"].tap()
        XCTAssertTrue(app.navigationBars["Cards"].waitForExistence(timeout: 8))
    }

    @MainActor
    private func unlockWithBiometrics(_ app: XCUIApplication) throws {
        let unlock = app.buttons["Unlock wallet"]
        XCTAssertTrue(unlock.waitForExistence(timeout: 5))
        unlock.tap()
        let timer = DispatchSource.makeTimerSource(queue: .global(qos: .userInitiated))
        timer.schedule(deadline: .now() + .milliseconds(250), repeating: .milliseconds(500))
        timer.setEventHandler {
            notify_post("com.apple.BiometricKit_Sim.pearl.match")
            notify_post("com.apple.BiometricKit_Sim.fingerTouch.match")
        }
        timer.resume()
        defer { timer.cancel() }

        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let deadline = Date().addingTimeInterval(25)
        let cards = app.navigationBars["Cards"]
        while Date() < deadline {
            if cards.exists { return }
            let facePermission = springboard.alerts.containing(
                NSPredicate(format: "label CONTAINS[c] %@", "Face ID")
            ).firstMatch
            if facePermission.exists {
                let approval = facePermission.buttons.matching(NSPredicate(format: "label IN %@", ["Allow", "OK"])).firstMatch
                if approval.exists { approval.tap() }
            }
            if app.staticTexts["Set a device passcode in Settings to use Kura."].exists {
                throw XCTSkip("This simulator reports LAError.passcodeNotSet. The unlocked journey requires device-owner authentication and a passcode-protected Keychain.")
            }
            if cards.waitForExistence(timeout: 0.5) { return }
        }
        let shield = app.staticTexts["Wallet hidden"].exists
        let locked = app.buttons["Unlock wallet"].exists
        let navigation = app.navigationBars.firstMatch
        let bar = navigation.exists ? navigation.label : "absent"
        XCTFail("Wallet did not open. Shield visible: \(shield), lock visible: \(locked), navigation bar: \(bar), system alerts: \(springboard.alerts.count).")
    }

    @MainActor
    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<8 {
            if element.exists && element.isHittable { return }
            app.swipeUp()
        }
        XCTAssertTrue(element.isHittable)
    }
    #endif

    @MainActor
    private func waitForLandscape(_ app: XCUIApplication, title: String) {
        let bar = app.navigationBars[title]
        let row = app.buttons["Add front image"]
        var previous = CGRect.null
        var stableSince = Date()
        let deadline = Date().addingTimeInterval(6)
        while Date() < deadline {
            let bounds = app.frame
            if bounds.width > bounds.height, bar.frame.width >= bounds.width * 0.85,
               row.exists, row.isHittable, row.frame.intersects(bounds) {
                if row.frame == previous {
                    if Date().timeIntervalSince(stableSince) >= 0.65 { return }
                } else {
                    previous = row.frame
                    stableSince = Date()
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.15))
        }
        XCTFail("The detail toolbar and front-image row did not settle in landscape.")
    }

    @MainActor
    private func capture(_ name: String, _ app: XCUIApplication, verifyDarkBackground: Bool = false) {
        var image = XCUIScreen.main.screenshot().image
        let landscape = app.frame.width > app.frame.height
        // On some simulators a full-device screenshot is returned in sensor coordinates.
        if landscape && image.size.height > image.size.width, let pixels = image.cgImage {
            image = UIImage(cgImage: pixels, scale: image.scale, orientation: .left)
        }
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        let data = UIGraphicsImageRenderer(size: image.size, format: format).pngData { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
        let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.png")
        attachment.name = "Kura-QA-" + name + ".png"
        attachment.lifetime = .keepAlways
        add(attachment)
        if verifyDarkBackground, let png = UIImage(data: data)?.cgImage {
            // Measure the same opaque full-screen PNG that CI exports. Wallet rows and the
            // scroll background occupy most of this synthetic, single-item screen.
            var samples = [UInt8](repeating: 255, count: 32 * 32)
            let sampled = samples.withUnsafeMutableBytes { pixels -> Bool in
                guard let context = CGContext(data: pixels.baseAddress, width: 32, height: 32, bitsPerComponent: 8,
                                              bytesPerRow: 32, space: CGColorSpaceCreateDeviceGray(),
                                              bitmapInfo: CGImageAlphaInfo.none.rawValue) else { return false }
                context.interpolationQuality = .high
                context.draw(png, in: CGRect(x: 0, y: 0, width: 32, height: 32))
                return true
            }
            XCTAssertTrue(sampled)
            let ratio = Double(samples.filter { $0 < 80 }.count) / Double(samples.count)
            XCTAssertGreaterThan(ratio, 0.65, "Expected dark wallet pixels in " + name)
        } else if verifyDarkBackground {
            XCTFail("Could not read the dark wallet screenshot")
        }
    }

    @MainActor
    private func assertLocked(_ app: XCUIApplication, file: StaticString = #filePath, line: UInt = #line) {
        let unlock = app.buttons["Unlock wallet"]
        XCTAssertTrue(unlock.waitForExistence(timeout: 10), file: file, line: line)
        XCTAssertTrue(unlock.isHittable, file: file, line: line)
        XCTAssertTrue(app.staticTexts["Kura"].exists, file: file, line: line)
        XCTAssertFalse(app.navigationBars["Cards"].exists, file: file, line: line)
        XCTAssertFalse(app.buttons["Add item"].exists, file: file, line: line)
    }
}
