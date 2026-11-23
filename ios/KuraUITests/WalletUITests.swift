import XCTest

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
