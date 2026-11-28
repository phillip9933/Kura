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
        try unlockSimulatorWallet(app)
        XCUIDevice.shared.orientation = .portrait
        defer { XCUIDevice.shared.orientation = .portrait }
        let name = "Library " + String(UUID().uuidString.prefix(8))
        verifyHomeNavigation(in: app)
        verifyDiscardingSettings(in: app)
        let addMenu = app.buttons["wallet.addMenu"]
        addMenu.tap()
        for option in ["Add item", "Scan card", "Import passes", "Import encrypted item", "Receive secure transfer"] {
            XCTAssertTrue(app.buttons[option].waitForExistence(timeout: 5), "Missing Add menu option: " + option)
        }
        app.buttons["Scan card"].tap()
        XCTAssertTrue(app.buttons["Take a front photo"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Choose a front photo"].exists)
        XCTAssertFalse(app.navigationBars.buttons["Save"].isEnabled)
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Cards"].waitForExistence(timeout: 5))
        addMenu.tap()
        XCTAssertTrue(app.buttons["Scan card"].waitForExistence(timeout: 5))
        app.buttons["Add item"].tap()
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
        setWalletAppearance("Dark", saveOnExit: true, in: app)
        app.terminate()
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"]
        app.launch()
        assertLocked(app)
        try unlockSimulatorWallet(app)
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
    private func verifyHomeNavigation(in app: XCUIApplication) {
        XCTAssertFalse(app.buttons["Lock"].exists)
        let add = app.buttons["wallet.addMenu"]
        XCTAssertTrue(add.waitForExistence(timeout: 5))
        XCTAssertTrue(add.isHittable)
        XCTAssertGreaterThan(add.frame.midY, app.frame.midY)
        for name in ["Cards", "Passes", "Identity"] {
            let section = app.buttons["wallet.section." + name]
            XCTAssertTrue(section.waitForExistence(timeout: 5), name)
            XCTAssertTrue(section.isHittable, name)
            XCTAssertEqual(section.label, name)
            XCTAssertGreaterThan(section.frame.midY, app.frame.minY + 0.75 * app.frame.height, name)
            XCTAssertLessThanOrEqual(add.frame.maxY, section.frame.minY, name)
        }
        for name in ["Passes", "Identity", "Cards"] {
            app.buttons["wallet.section." + name].tap()
            XCTAssertTrue(app.navigationBars[name].waitForExistence(timeout: 5))
            XCTAssertTrue(app.buttons["wallet.section." + name].isSelected)
        }

        let categories = app.buttons["wallet.categories"]
        XCTAssertTrue(categories.waitForExistence(timeout: 5))
        let sectionButton = app.buttons["wallet.section.Cards"]
        XCTAssertGreaterThanOrEqual(categories.frame.minY - add.frame.maxY, 16)
        XCTAssertGreaterThanOrEqual(sectionButton.frame.minY - categories.frame.maxY, 8)
        for name in ["Library", "All categories"] {
            categories.tap()
            let option = app.buttons[name]
            if !option.waitForExistence(timeout: 1) {
                // Some OS versions present the picker choices in a Category submenu.
                let picker = app.buttons["Category"]
                XCTAssertTrue(picker.waitForExistence(timeout: 5))
                picker.tap()
            }
            XCTAssertTrue(option.waitForExistence(timeout: 5))
            option.tap()
            let selection = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "label CONTAINS %@ OR value CONTAINS %@", name, name),
                object: categories
            )
            XCTAssertEqual(XCTWaiter.wait(for: [selection], timeout: 5), .completed)
        }
    }

    @MainActor
    private func verifyDiscardingSettings(in app: XCUIApplication) {
        let settings = app.buttons["wallet.settings"]
        settings.tap()
        let grid = app.switches["Grid view"]
        reveal(grid, in: app)
        let original = grid.value as? String
        XCTAssertNotNil(original)
        grid.tap()

        let back = app.buttons["wallet.settings.back"]
        XCTAssertTrue(back.waitForExistence(timeout: 5))
        back.tap()
        let keep = app.buttons["Keep Editing"]
        XCTAssertTrue(keep.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Save your changes to apply them."].exists)
        capture("settings-unsaved", app)
        keep.tap()
        XCTAssertNotEqual(grid.value as? String, original)

        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.5))
        let center = app.coordinate(withNormalizedOffset: CGVector(dx: 0.55, dy: 0.5))
        edge.press(forDuration: 0.1, thenDragTo: center)
        let discard = app.buttons["Discard Changes"]
        XCTAssertTrue(discard.waitForExistence(timeout: 5))
        discard.tap()
        XCTAssertTrue(app.navigationBars["Cards"].waitForExistence(timeout: 5))
        settings.tap()
        reveal(grid, in: app)
        XCTAssertEqual(grid.value as? String, original)
        app.buttons["wallet.settings.back"].tap()
        XCTAssertTrue(app.navigationBars["Cards"].waitForExistence(timeout: 5))
    }

    @MainActor
    private func setWalletAppearance(_ choice: String, saveOnExit: Bool = false, in app: XCUIApplication) {
        let settings = app.descendants(matching: .any).matching(identifier: "wallet.settings").firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        let appearance = app.descendants(matching: .any).matching(identifier: "wallet.appearance").firstMatch
        reveal(appearance, in: app)
        appearance.tap()
        let option = app.descendants(matching: .any).matching(identifier: "wallet.appearance." + choice).firstMatch
        XCTAssertTrue(option.waitForExistence(timeout: 5))
        option.tap()
        if saveOnExit {
            let back = app.buttons["wallet.settings.back"]
            XCTAssertTrue(back.waitForExistence(timeout: 5))
            back.tap()
            let save = app.buttons["Save and Leave"]
            XCTAssertTrue(save.waitForExistence(timeout: 5))
            save.tap()
        } else {
            app.buttons["wallet.settings.save"].tap()
        }
        XCTAssertTrue(app.navigationBars["Cards"].waitForExistence(timeout: 8))
    }

    @MainActor
    private func unlockSimulatorWallet(_ app: XCUIApplication) throws {
        let unlock = app.buttons["Unlock wallet"]
        XCTAssertTrue(unlock.waitForExistence(timeout: 5))
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let coreAuth = XCUIApplication(bundleIdentifier: "com.apple.CoreAuthUI")
        let hosts = [app, springboard, coreAuth]
        unlock.tap()
        var enteredPasscode = false
        let deadline = Date().addingTimeInterval(35)
        let cards = app.navigationBars["Cards"]
        let opening = app.descendants(matching: .any).matching(identifier: "Unlocking wallet").firstMatch
        while Date() < deadline {
            if cards.exists { return }
            if approveFaceID(in: hosts, coreAuth: coreAuth) { continue }
            if !enteredPasscode && enterSimulatorPasscodeIfRequested(in: coreAuth) {
                enteredPasscode = true
                continue
            }
            if app.staticTexts["Set a device passcode in Settings to use Kura."].exists {
                throw XCTSkip("This simulator reports LAError.passcodeNotSet. The unlocked journey requires device-owner authentication and a passcode-protected Keychain.")
            }
            // Face ID usually starts matching without an interactive sheet. Scope sensor
            // events to the application's actual unlock request or the foreground OS service.
            if coreAuth.state == .runningForeground || opening.exists {
                notify_post("com.apple.BiometricKit_Sim.pearl.match")
                notify_post("com.apple.BiometricKit_Sim.fingerTouch.match")
            }
            if cards.waitForExistence(timeout: 0.6) { return }
        }
        let shielded = app.staticTexts["Wallet hidden"].exists
        let background = app.descendants(matching: .any)["privacy.background"].exists
        let captured = app.descendants(matching: .any)["privacy.capture"].exists
        let bar = app.navigationBars.firstMatch
        capture("auth-prompt-failure", app)
        XCTFail("Wallet did not open. Shield: \(shielded) (background: \(background), captured: \(captured)), lock: \(unlock.exists), opening: \(opening.exists), navigation: \(bar.exists ? bar.label : "absent"), Kura state: \(app.state.rawValue), CoreAuthUI state: \(coreAuth.state.rawValue), SpringBoard alerts: \(springboard.alerts.count).")
    }

    @MainActor
    private func enterSimulatorPasscodeIfRequested(in coreAuth: XCUIApplication) -> Bool {
        // Some simulator runtimes present the native device-owner passcode field instead of Face ID.
        guard coreAuth.state == .runningForeground else { return false }
        let title = NSPredicate(format: "label CONTAINS[c] %@ AND label CONTAINS[c] %@", "iPhone Passcode", "Kura")
        guard coreAuth.staticTexts.matching(title).firstMatch.exists else { return false }
        let passcode = coreAuth.secureTextFields.firstMatch
        guard passcode.exists, passcode.isHittable else { return false }
        passcode.tap()
        passcode.typeText("482619\n")
        return true
    }

    @MainActor
    private func approveFaceID(in hosts: [XCUIApplication], coreAuth: XCUIApplication) -> Bool {
        let faceID = NSPredicate(format: "label CONTAINS[c] %@", "Face ID")
        let allow = NSPredicate(format: "label IN %@", ["Allow", "OK", "Continue"])
        for host in hosts where host.state == .runningForeground || host.state == .runningBackground {
            let prompt = host.alerts.firstMatch
            if prompt.exists && (prompt.label.localizedCaseInsensitiveContains("Face ID") ||
                                 prompt.staticTexts.matching(faceID).firstMatch.exists) {
                let approval = prompt.buttons.matching(allow).firstMatch
                if approval.exists && approval.isHittable { approval.tap(); return true }
            }
            // CoreAuthUI can host consent in a remote view without an AXAlert container.
            if host === coreAuth, host.state == .runningForeground,
               host.staticTexts.matching(faceID).firstMatch.exists {
                let approval = host.buttons.matching(allow).firstMatch
                if approval.exists && approval.isHittable { approval.tap(); return true }
            }
        }
        return false
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
        let available = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == YES"), object: unlock)
        XCTAssertEqual(XCTWaiter.wait(for: [available], timeout: 5), .completed, file: file, line: line)
        XCTAssertTrue(app.staticTexts["Kura"].exists, file: file, line: line)
        XCTAssertFalse(app.navigationBars["Cards"].exists, file: file, line: line)
        XCTAssertFalse(app.buttons["Add item"].exists, file: file, line: line)
    }
}
