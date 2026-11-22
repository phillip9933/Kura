import SwiftUI
import UIKit

/// A separate window also covers a presented document picker or editor sheet in app-switcher snapshots.
struct PrivacyShield: UIViewRepresentable {
    func makeUIView(context: Context) -> ShieldAnchor { ShieldAnchor() }
    func updateUIView(_ view: ShieldAnchor, context: Context) {}

    final class ShieldAnchor: UIView {
        private var cover: UIWindow?
        override init(frame: CGRect) {
            super.init(frame: frame)
            NotificationCenter.default.addObserver(self, selector: #selector(hideContents(_:)),
                name: UIScene.willDeactivateNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(showContents(_:)),
                name: UIScene.didActivateNotification, object: nil)
        }
        required init?(coder: NSCoder) { nil }

        @objc private func hideContents(_ event: Notification) {
            guard let scene = window?.windowScene, event.object as? UIWindowScene === scene else { return }
            let shield = UIWindow(windowScene: scene)
            shield.windowLevel = UIWindow.Level(rawValue: UIWindow.Level.alert.rawValue + 1)
            let controller = UIViewController()
            controller.view.backgroundColor = .systemBackground
            shield.rootViewController = controller
            shield.isUserInteractionEnabled = false
            shield.accessibilityElementsHidden = true
            shield.isHidden = false
            cover = shield
        }

        @objc private func showContents(_ event: Notification) {
            guard let scene = window?.windowScene, event.object as? UIWindowScene === scene else { return }
            cover?.isHidden = true
            cover = nil
        }
    }
}
