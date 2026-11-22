import SwiftUI
import UIKit

@main
struct KuraApp: App {
    @StateObject private var wallet = WalletSession()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {
                if scenePhase != .active {
                    Label("Kura", systemImage: "lock.fill")
                } else if wallet.phase == .unlocked {
                    CardsView(wallet: wallet)
                        .privacySensitive()
                } else {
                    VStack(spacing: 20) {
                        Image(systemName: "lock.fill").font(.largeTitle).accessibilityHidden(true)
                        Text("Kura").font(.largeTitle.bold()).accessibilityAddTraits(.isHeader)
                        if wallet.phase == .opening {
                            ProgressView("Unlocking wallet")
                        } else {
                            if let message = wallet.unlockMessage { Text(message) }
                            Button("Unlock wallet", action: wallet.unlock).buttonStyle(.borderedProminent)
                        }
                    }
                    .padding()
                    .multilineTextAlignment(.center)
                }
            }
            .onChange(of: scenePhase) { phase in
                // Authentication itself can briefly make the scene inactive.
                if phase == .background || (phase == .inactive && wallet.phase == .unlocked) {
                    wallet.lock()
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.protectedDataWillBecomeUnavailableNotification)) { _ in
                wallet.lock()
            }
        }
    }
}
