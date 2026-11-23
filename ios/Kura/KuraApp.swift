import SwiftUI
import UIKit

@main
struct KuraApp: App {
    @StateObject private var wallet = WalletSession()
    @State private var returningFromBackground = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {
                if wallet.phase == .recoveryRequired {
                    BackupRescueView(wallet: wallet).privacySensitive()
                } else if wallet.phase == .unlocked {
                    CardsView(wallet: wallet).id(wallet.generation)
                        .privacySensitive()
                } else {
                    VStack(spacing: 20) {
                        Image(systemName: "lock.fill").font(.largeTitle).accessibilityHidden(true)
                        Text("Kura").font(.largeTitle.bold()).accessibilityAddTraits(.isHeader)
                        if wallet.phase == .opening {
                            ProgressView("Unlocking wallet")
                        } else {
                            if PhoneUnlock.enabled || (PasscodeUnlock.enabled && wallet.unlockMessage != nil) { Button("Use app authentication") { wallet.unlock(requirePrompt: true) } }
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
                if phase == .background { returningFromBackground = true; wallet.background() }
                if phase == .active {
                    wallet.foreground()
                    if returningFromBackground && PhoneUnlock.enabled && wallet.phase == .locked { wallet.unlock() }
                    returningFromBackground = false
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.protectedDataWillBecomeUnavailableNotification)) { _ in
                wallet.lock()
            }
        }
    }
}
