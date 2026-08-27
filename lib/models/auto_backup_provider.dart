import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AutoBackupProvider with ChangeNotifier {
  bool _isEnabled = false;
  String _backupPath = '';
  bool _hasBackupPassword = false;
  String _backupUri = '';
  int _retentionCount = 5;

  bool get isEnabled => _isEnabled;
  String get backupPath => _backupPath;
  bool get hasBackupPassword => _hasBackupPassword;
  String get backupUri => _backupUri;
  int get retentionCount => _retentionCount;

  static const String _keyEnabled = 'autoBackupEnabled';
  static const String _keyPath = 'autoBackupPath';
  static const String _keyPassword = 'autoBackupPassword';
  static const String _securePasswordKey = 'auto_backup_password';
  // Android uses a Keystore-protected key via flutter_secure_storage.
  static const _secureStorage = FlutterSecureStorage();

  static const String _keyUri = 'autoBackupUri';
  static const String _keyRetentionCount = 'autoBackupRetentionCount';

  Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    _isEnabled = prefs.getBool(_keyEnabled) ?? false;
    _backupPath = prefs.getString(_keyPath) ?? '';
    final securePassword = await _secureStorage.read(key: _securePasswordKey);
    if (securePassword != null && securePassword.isNotEmpty) {
      _hasBackupPassword = true;
      await prefs.remove(_keyPassword);
    } else {
      final legacyPassword = prefs.getString(_keyPassword);
      if (legacyPassword != null && legacyPassword.isNotEmpty) {
        await _secureStorage.write(
          key: _securePasswordKey,
          value: legacyPassword,
        );
        await prefs.remove(_keyPassword);
        _hasBackupPassword = true;
      }
    }
    _backupUri = prefs.getString(_keyUri) ?? '';
    _retentionCount = prefs.getInt(_keyRetentionCount) ?? 5;
    notifyListeners();
  }

  Future<void> setEnabled(bool value) async {
    _isEnabled = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyEnabled, value);
    notifyListeners();
  }

  Future<void> setBackupPath(String path) async {
    _backupPath = path;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyPath, path);
    notifyListeners();
  }

  Future<void> setBackupUri(String uri) async {
    _backupUri = uri;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyUri, uri);
    notifyListeners();
  }

  Future<void> setBackupPassword(String password) async {
    final prefs = await SharedPreferences.getInstance();
    if (password.isEmpty) {
      await _secureStorage.delete(key: _securePasswordKey);
      _hasBackupPassword = false;
    } else {
      await _secureStorage.write(key: _securePasswordKey, value: password);
      _hasBackupPassword = true;
    }
    await prefs.remove(_keyPassword);
    notifyListeners();
  }

  Future<String?> readBackupPassword() =>
      _secureStorage.read(key: _securePasswordKey);

  Future<void> setRetentionCount(int value) async {
    if (value < 1) return;
    _retentionCount = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keyRetentionCount, value);
    notifyListeners();
  }

  bool get isConfigured =>
      _isEnabled && _backupUri.isNotEmpty && _hasBackupPassword;

  String get displayPath {
    if (_backupUri.isEmpty) return _backupPath;
    try {
      final uri = Uri.parse(_backupUri);
      final segments = uri.pathSegments;
      if (segments.length >= 2) {
        return segments.last;
      }
    } catch (_) {}
    return _backupPath;
  }
}
