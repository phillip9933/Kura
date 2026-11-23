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
        private var inactive = false
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
            events.addObserver(self, selector: #selector(deactivate(_:)), name: UIScene.willDeactivateNotification, object: nil)
            events.addObserver(self, selector: #selector(activate(_:)), name: UIScene.didActivateNotification, object: nil)
            events.addObserver(self, selector: #selector(captureChanged(_:)), name: UIScreen.capturedDidChangeNotification, object: nil)
            if #available(iOS 18.4, *) {
                registerForTraitChanges([UITraitSceneCaptureState.self], action: #selector(refresh))
            }
        }
        required init?(coder: NSCoder) { nil }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            inactive = window?.windowScene?.activationState != .foregroundActive
            refresh()
        }

        func update(from phase: ScenePhase) {
            if lastPhase != phase {
                lastPhase = phase
                inactive = phase != .active
            }
            refresh()
        }

        @objc private func deactivate(_ event: Notification) {
            guard let scene = window?.windowScene, event.object as? UIWindowScene === scene else { return }
            inactive = true
            refresh()
        }

        @objc private func activate(_ event: Notification) {
            guard let scene = window?.windowScene, event.object as? UIWindowScene === scene else { return }
            inactive = false
            refresh()
        }

        @objc private func captureChanged(_ event: Notification) { refresh() }

        func removeCover() { cover.removeFromSuperview() }

        @objc private func refresh() {
            guard let window else { removeCover(); return }
            let captured: Bool
            if #available(iOS 18.4, *) { captured = traitCollection.sceneCaptureState == .active }
            else { captured = window.screen.isCaptured }
            guard inactive || captured else { removeCover(); return }
            label.accessibilityIdentifier = captured ? "privacy.capture" : "privacy.inactive"
            cover.isUserInteractionEnabled = captured
            cover.frame = window.bounds
            if cover.superview !== window { window.addSubview(cover) }
            else { window.bringSubviewToFront(cover) }
        }
    }
}
