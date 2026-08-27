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
  });
}
