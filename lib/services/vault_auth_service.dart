import 'package:local_auth/local_auth.dart';

abstract interface class VaultAuthenticator {
  Future<bool> authenticate({String reason});
}

/// Fail-closed wrapper around the platform device-credential prompt.
class VaultAuthService implements VaultAuthenticator {
  VaultAuthService({LocalAuthentication? localAuthentication})
    : _localAuthentication = localAuthentication ?? LocalAuthentication();

  final LocalAuthentication _localAuthentication;

  @override
  Future<bool> authenticate({
    String reason = 'Authenticate to unlock your vault',
  }) async {
    try {
      if (!await _localAuthentication.isDeviceSupported()) return false;
      return await _localAuthentication.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          biometricOnly: false,
          stickyAuth: true,
          sensitiveTransaction: true,
        ),
      );
    } catch (_) {
      return false;
    }
  }
}
