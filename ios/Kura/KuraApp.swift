import SwiftUI
import UIKit

@main
struct KuraApp: App {
    @StateObject private var wallet = WalletSession()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {
                if wallet.phase == .recoveryRequired {
                    BackupRescueView(wallet: wallet).privacySensitive()
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
                            if PhoneUnlock.enabled { Button("Use Face ID or passcode") { wallet.unlock(requirePrompt: true) } }
                            if let message = wallet.unlockMessage { Text(message) }
                            Button("Unlock wallet") { wallet.unlock() }.buttonStyle(.borderedProminent)
                        }
                    }
                    .padding()
                    .multilineTextAlignment(.center)
                }
            }
            .background(PrivacyShield())
            .preferredColorScheme(wallet.snapshot.preferences.appearance == "Dark" ? .dark :
                                  wallet.snapshot.preferences.appearance == "Light" ? .light : nil)
            .onAppear {
                if scenePhase == .active && PhoneUnlock.enabled { wallet.unlock() }
            }
            .onChange(of: scenePhase) { phase in
                if phase == .background { wallet.background() }
                if phase == .active {
                    wallet.foreground()
                    if PhoneUnlock.enabled && wallet.phase == .locked { wallet.unlock() }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.protectedDataWillBecomeUnavailableNotification)) { _ in
                wallet.lock()
            }
        }
    }
}
