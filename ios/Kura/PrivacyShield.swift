import SwiftUI
import UIKit

/// Cover the app's existing window, including presented sheets, without creating another scene window.
struct PrivacyShield: UIViewRepresentable {
    let phase: ScenePhase

    func makeUIView(context: Context) -> ShieldAnchor { ShieldAnchor() }
    func updateUIView(_ anchor: ShieldAnchor, context: Context) { anchor.update(from: phase) }
    static func dismantleUIView(_ anchor: ShieldAnchor, coordinator: ()) { anchor.removeCover() }

    final class ShieldAnchor: UIView {
        private let cover = UIView()
        private let label = UILabel()
        private var backgrounded = false
        private var lastPhase: ScenePhase?

        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
            cover.backgroundColor = .systemBackground
            cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            cover.accessibilityViewIsModal = true
            label.text = "Wallet hidden"
            label.font = .preferredFont(forTextStyle: .headline)
            label.adjustsFontForContentSizeCategory = true
            label.translatesAutoresizingMaskIntoConstraints = false
            cover.addSubview(label)
            NSLayoutConstraint.activate([label.centerXAnchor.constraint(equalTo: cover.centerXAnchor),
                                         label.centerYAnchor.constraint(equalTo: cover.centerYAnchor)])

            let events = NotificationCenter.default
            events.addObserver(self, selector: #selector(enterBackground(_:)), name: UIScene.didEnterBackgroundNotification, object: nil)
            events.addObserver(self, selector: #selector(enterForeground(_:)), name: UIScene.willEnterForegroundNotification, object: nil)
            events.addObserver(self, selector: #selector(captureChanged(_:)), name: UIScreen.capturedDidChangeNotification, object: nil)
            if #available(iOS 18.4, *) {
                registerForTraitChanges([UITraitSceneCaptureState.self], action: #selector(refresh))
            }
        }
        required init?(coder: NSCoder) { nil }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            backgrounded = window?.windowScene?.activationState == .background
            refresh()
        }

        func update(from phase: ScenePhase) {
            if lastPhase != phase {
                lastPhase = phase
                backgrounded = phase == .background
            }
            refresh()
        }

        // UIKit captures the switcher image after the background transition. Foreground
        // inactivity also occurs while the device authentication prompt owns interaction.
        @objc private func enterBackground(_ event: Notification) {
            guard let scene = window?.windowScene, event.object as? UIWindowScene === scene else { return }
            backgrounded = true
            refresh()
        }

        @objc private func enterForeground(_ event: Notification) {
            guard let scene = window?.windowScene, event.object as? UIWindowScene === scene else { return }
            backgrounded = false
            refresh()
        }

        @objc private func captureChanged(_ event: Notification) { refresh() }

        func removeCover() { cover.removeFromSuperview() }

        @objc private func refresh() {
            guard let window else { removeCover(); return }
            let captured: Bool
            if #available(iOS 18.4, *) { captured = traitCollection.sceneCaptureState == .active }
            else { captured = window.screen.isCaptured }
            guard backgrounded || captured else { removeCover(); return }
            label.accessibilityIdentifier = captured ? "privacy.capture" : "privacy.background"
            cover.isUserInteractionEnabled = captured
            cover.frame = window.bounds
            if cover.superview !== window { window.addSubview(cover) }
            else { window.bringSubviewToFront(cover) }
        }
    }
}
