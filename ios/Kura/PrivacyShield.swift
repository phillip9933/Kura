import SwiftUI
import UIKit

/// Use a separate window so a presented picker or editor is covered along with the wallet.
struct PrivacyShield: UIViewRepresentable {
    func makeUIView(context: Context) -> ShieldAnchor { ShieldAnchor() }
    func updateUIView(_ view: ShieldAnchor, context: Context) {}

    final class ShieldAnchor: UIView {
        private var cover: UIWindow?
        private var inactive = false

        override init(frame: CGRect) {
            super.init(frame: frame)
            let events = NotificationCenter.default
            events.addObserver(self, selector: #selector(hideContents(_:)), name: UIScene.willDeactivateNotification, object: nil)
            events.addObserver(self, selector: #selector(showContents(_:)), name: UIScene.didActivateNotification, object: nil)
            events.addObserver(self, selector: #selector(captureChanged(_:)), name: UIScreen.capturedDidChangeNotification, object: nil)
        }
        required init?(coder: NSCoder) { nil }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            inactive = window?.windowScene?.activationState != .foregroundActive
            updateCover()
        }

        @objc private func hideContents(_ event: Notification) {
            guard let scene = window?.windowScene, event.object as? UIWindowScene === scene else { return }
            inactive = true
            updateCover()
        }

        @objc private func showContents(_ event: Notification) {
            guard let scene = window?.windowScene, event.object as? UIWindowScene === scene else { return }
            inactive = false
            updateCover()
        }

        @objc private func captureChanged(_ event: Notification) { updateCover() }

        private func updateCover() {
            guard let scene = window?.windowScene, inactive || scene.screen.isCaptured else {
                cover?.isHidden = true
                cover = nil
                return
            }
            guard cover == nil else { return }
            let shield = UIWindow(windowScene: scene)
            shield.windowLevel = UIWindow.Level(rawValue: UIWindow.Level.alert.rawValue + 1)
            let controller = UIViewController()
            controller.view.backgroundColor = .systemBackground
            let label = UILabel()
            label.text = "Wallet hidden"
            label.font = .preferredFont(forTextStyle: .headline)
            label.adjustsFontForContentSizeCategory = true
            label.translatesAutoresizingMaskIntoConstraints = false
            controller.view.addSubview(label)
            NSLayoutConstraint.activate([label.centerXAnchor.constraint(equalTo: controller.view.centerXAnchor),
                                         label.centerYAnchor.constraint(equalTo: controller.view.centerYAnchor)])
            shield.rootViewController = controller
            shield.isHidden = false
            cover = shield
        }
    }
}
