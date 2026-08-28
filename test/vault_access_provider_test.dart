import 'package:flutter_test/flutter_test.dart';
import 'package:kura/models/vault_access_provider.dart';
import 'package:kura/services/vault_auth_service.dart';

class FakeVaultAuthenticator implements VaultAuthenticator {
  FakeVaultAuthenticator(this.result);

  bool result;
  int calls = 0;

  @override
  Future<bool> authenticate({String reason = ''}) async {
    calls++;
    return result;
  }
}

void main() {
  group('VaultAccessProvider', () {
    test('unlocks only after successful device authentication', () async {
      final authenticator = FakeVaultAuthenticator(true);
      final provider = VaultAccessProvider(
        authenticationService: authenticator,
      );

      expect(provider.isUnlocked, isFalse);
      expect(await provider.unlock(), isTrue);
      expect(provider.state, VaultAccessState.unlocked);
      expect(provider.isReady, isFalse);
      expect(authenticator.calls, 1);
    });

    test('remains locked when authentication is denied', () async {
      final provider = VaultAccessProvider(
        authenticationService: FakeVaultAuthenticator(false),
      );

      expect(await provider.unlock(), isFalse);
      expect(provider.state, VaultAccessState.locked);
      expect(provider.isUnlocked, isFalse);
    });

    test(
      'locks an existing session and requires a new authentication',
      () async {
        final authenticator = FakeVaultAuthenticator(true);
        final provider = VaultAccessProvider(
          authenticationService: authenticator,
        );

        await provider.unlock();
        provider.lock();

        expect(provider.isUnlocked, isFalse);
        expect(await provider.unlock(), isTrue);
        expect(authenticator.calls, 2);
      },
    );

    test('does not become ready until initialization succeeds', () async {
      final provider = VaultAccessProvider(
        authenticationService: FakeVaultAuthenticator(true),
      );

      await provider.unlock();

      expect(provider.isReady, isFalse);
      provider.markReady();
      expect(provider.isReady, isTrue);
    });

    test('clears readiness before showing the biometric prompt', () async {
      final provider = VaultAccessProvider(
        authenticationService: FakeVaultAuthenticator(true),
      );

      await provider.unlock();
      provider.markReady();

      final unlock = provider.unlock();
      expect(provider.isReady, isFalse);
      await unlock;
    });

    test('clears readiness when the vault locks for app resume', () async {
      final provider = VaultAccessProvider(
        authenticationService: FakeVaultAuthenticator(true),
      );

      await provider.unlock();
      provider.markReady();
      provider.lock();

      expect(provider.isUnlocked, isFalse);
      expect(provider.isReady, isFalse);
    });

    test(
      'tracks external operations without changing vault readiness',
      () async {
        final provider = VaultAccessProvider(
          authenticationService: FakeVaultAuthenticator(true),
        );

        await provider.unlock();
        provider.markReady();
        provider.beginExternalOperation();

        expect(provider.hasExternalOperation, isTrue);
        expect(provider.isReady, isTrue);

        provider.endExternalOperation();
        expect(provider.hasExternalOperation, isFalse);
      },
    );

    test('requests automatic unlock only while locked and idle', () {
      final provider = VaultAccessProvider(
        authenticationService: FakeVaultAuthenticator(true),
      );

      provider.requestAutomaticUnlock();
      expect(provider.automaticUnlockRequest, 1);

      provider.beginExternalOperation();
      provider.requestAutomaticUnlock();
      expect(provider.automaticUnlockRequest, 1);
    });
  });
}
