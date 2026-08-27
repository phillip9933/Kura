import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:kura/models/auto_backup_provider.dart';
import 'package:kura/services/backup_service.dart';

class AutoBackupService {
  static AutoBackupProvider? _provider;
  static Timer? _debounceTimer;

  static void initialize(AutoBackupProvider provider) {
    _provider = provider;
  }

  static void triggerBackup() {
    final provider = _provider;
    if (provider == null || !provider.isConfigured) return;

    _debounceTimer?.cancel();
    _debounceTimer = Timer(const Duration(seconds: 2), () async {
      try {
        final password = await provider.readBackupPassword();
        if (password == null || password.isEmpty) return;
        await BackupService.createAutoBackup(
          password,
          provider.backupUri,
          retentionCount: provider.retentionCount,
        );
      } catch (e) {
        if (kDebugMode) {
          debugPrint('AutoBackupService: backup failed: $e');
        }
      }
    });
  }

  static void lock() => _debounceTimer?.cancel();
}
