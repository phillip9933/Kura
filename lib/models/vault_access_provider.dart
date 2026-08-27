import 'package:flutter/foundation.dart';
import 'package:kura/services/vault_auth_service.dart';

enum VaultAccessState { locked, authenticating, unlocked }

class VaultAccessProvider with ChangeNotifier {
  VaultAccessProvider({VaultAuthenticator? authenticationService})
    : _authenticationService = authenticationService ?? VaultAuthService();

  final VaultAuthenticator _authenticationService;
  VaultAccessState _state = VaultAccessState.locked;
  bool _initializing = false;

  VaultAccessState get state => _state;
  bool get isUnlocked => _state == VaultAccessState.unlocked;

  Future<bool> unlock({String? reason}) async {
    if (_state == VaultAccessState.authenticating) return false;
    _state = VaultAccessState.authenticating;
    notifyListeners();
    final authenticated = await _authenticationService.authenticate(
      reason: reason ?? 'Authenticate to unlock your vault',
    );
    _state = authenticated
        ? VaultAccessState.unlocked
        : VaultAccessState.locked;
    notifyListeners();
    return authenticated;
  }

  Future<bool> authenticateSensitiveAction(String reason) =>
      _authenticationService.authenticate(reason: reason);

  void lock() {
    if (_state == VaultAccessState.locked) return;
    _state = VaultAccessState.locked;
    notifyListeners();
  }

  bool beginInitialization() {
    if (_initializing) return false;
    _initializing = true;
    return true;
  }

  void finishInitialization() => _initializing = false;
}
