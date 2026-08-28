import 'package:flutter/foundation.dart';
import 'package:kura/services/vault_auth_service.dart';

enum VaultAccessState { locked, authenticating, unlocked }

class VaultAccessProvider with ChangeNotifier {
  VaultAccessProvider({VaultAuthenticator? authenticationService})
    : _authenticationService = authenticationService ?? VaultAuthService();

  final VaultAuthenticator _authenticationService;
  VaultAccessState _state = VaultAccessState.locked;
  bool _initializing = false;
  bool _isReady = false;
  int _externalOperationCount = 0;
  int _automaticUnlockRequest = 0;

  VaultAccessState get state => _state;
  bool get isUnlocked => _state == VaultAccessState.unlocked;
  bool get isAuthenticating => _state == VaultAccessState.authenticating;
  bool get isReady => _isReady;
  bool get hasExternalOperation => _externalOperationCount > 0;
  int get automaticUnlockRequest => _automaticUnlockRequest;

  Future<bool> unlock({String? reason}) async {
    if (_state == VaultAccessState.authenticating) return false;
    _state = VaultAccessState.authenticating;
    _isReady = false;
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
    if (_state == VaultAccessState.locked && !_isReady) return;
    _state = VaultAccessState.locked;
    _isReady = false;
    notifyListeners();
  }

  bool beginInitialization() {
    if (_initializing) return false;
    _initializing = true;
    return true;
  }

  void finishInitialization() => _initializing = false;

  void markReady() {
    if (!isUnlocked) return;
    _isReady = true;
    notifyListeners();
  }

  void beginExternalOperation() {
    _externalOperationCount++;
    notifyListeners();
  }

  void endExternalOperation() {
    if (_externalOperationCount == 0) return;
    _externalOperationCount--;
    notifyListeners();
  }

  void requestAutomaticUnlock() {
    if (_state != VaultAccessState.locked ||
        hasExternalOperation ||
        _initializing) {
      return;
    }
    _automaticUnlockRequest++;
    notifyListeners();
  }
}
