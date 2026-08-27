import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:kura/models/auto_backup_provider.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:kura/services/saf_service.dart';
import 'package:provider/provider.dart';

class BackupStoragePage extends StatefulWidget {
  const BackupStoragePage({
    super.key,
    required this.onCreateBackup,
    required this.onRestoreBackup,
    required this.onDeleteAllData,
  });

  final VoidCallback onCreateBackup;
  final VoidCallback onRestoreBackup;
  final VoidCallback onDeleteAllData;

  @override
  State<BackupStoragePage> createState() => _BackupStoragePageState();
}

class _BackupStoragePageState extends State<BackupStoragePage> {
  String? _pendingBackupUri;

  @override
  Widget build(BuildContext context) {
    final themeProvider = context.watch<ThemeProvider>();
    final provider = context.watch<AutoBackupProvider>();
    final isDark = themeProvider.isDarkMode;
    final divider = Divider(
      color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFE8E8E8),
      height: 1,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Backup & Storage')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.backup_outlined),
                  title: const Text('Enable Auto-Backup'),
                  subtitle: Text(_getAutoBackupSubtitle(provider)),
                  trailing: Switch(
                    value: provider.isEnabled,
                    onChanged: (value) async {
                      if (value) {
                        await _showEnableAutoBackupDialog(provider);
                      } else {
                        await provider.setEnabled(false);
                      }
                    },
                  ),
                ),
                if (provider.isEnabled) ...[
                  divider,
                  ListTile(
                    leading: const Icon(Icons.folder_outlined),
                    title: const Text('Backup Location'),
                    subtitle: Text(_getShortPath(provider.backupPath)),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => _pickAutoBackupPath(provider),
                  ),
                  divider,
                  ListTile(
                    leading: const Icon(Icons.lock_outline_rounded),
                    title: const Text('Change Backup Password'),
                    subtitle: const Text(
                      'Update the auto-backup encryption password',
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => _showChangeAutoBackupPasswordDialog(provider),
                  ),
                  divider,
                  ListTile(
                    leading: const Icon(Icons.history_rounded),
                    title: const Text('Backup Retention'),
                    subtitle: Text(
                      'Keep the latest ${provider.retentionCount} auto-backups',
                    ),
                    trailing: TextButton(
                      onPressed: () => _showAutoBackupRetentionDialog(provider),
                      child: Text('${provider.retentionCount}'),
                    ),
                  ),
                ],
                divider,
                ListTile(
                  leading: const Icon(Icons.backup_outlined),
                  title: const Text('Create Backup'),
                  subtitle: const Text('Save an encrypted copy of your data'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: widget.onCreateBackup,
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.restore_outlined),
                  title: const Text('Restore Backup'),
                  subtitle: const Text(
                    'Replace current data from a backup file',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: widget.onRestoreBackup,
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.delete_forever_outlined),
                  title: const Text('Delete All Data'),
                  subtitle: const Text(
                    'Permanently erase all data from this device',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: widget.onDeleteAllData,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _getAutoBackupSubtitle(AutoBackupProvider provider) {
    if (!provider.isEnabled) return 'Automatically backup on changes';
    final path = provider.displayPath;
    if (path.isEmpty) return 'Configure backup location';
    return 'Active - ${_getShortPath(path)}';
  }

  String _getShortPath(String path) {
    if (path.isEmpty) return 'Not set';
    final parts = path.split('/');
    if (parts.length <= 3) return path;
    return '.../${parts.sublist(parts.length - 2).join('/')}';
  }

  void _showAutoBackupRetentionDialog(AutoBackupProvider provider) {
    final isDark = context.read<ThemeProvider>().isDarkMode;
    final controller = TextEditingController(
      text: provider.retentionCount.toString(),
    );
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
        title: const Text(
          'Backup Retention',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Only the newest number of Kura auto-backups will be kept. Manually created backups are never affected.',
            ),
            const SizedBox(height: 16),
            TextField(
              controller: controller,
              autofocus: true,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(
                labelText: 'Number of backups to keep',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              final count = int.tryParse(controller.text.trim());
              if (count == null || count < 1) return;
              await provider.setRetentionCount(count);
              if (dialogContext.mounted) Navigator.pop(dialogContext);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  Future<void> _showEnableAutoBackupDialog(AutoBackupProvider provider) async {
    final isDark = context.read<ThemeProvider>().isDarkMode;
    final pathController = TextEditingController();
    final passwordController = TextEditingController();
    var obscure = true;

    await showDialog(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
          title: const Text(
            'Enable Auto-Backup',
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'A backup will be created automatically whenever you add or remove cards, passes, or identity cards.',
                  style: TextStyle(
                    color: isDark ? Colors.white70 : Colors.black87,
                    fontSize: 13,
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'Backup Location',
                  style: TextStyle(
                    color: isDark ? Colors.white : Colors.black,
                    fontWeight: FontWeight.w500,
                    fontSize: 13,
                  ),
                ),
                const SizedBox(height: 8),
                InkWell(
                  onTap: () async {
                    final result = await SafService.pickDirectory();
                    if (result == null) return;
                    final segments = Uri.parse(result).pathSegments;
                    setDialogState(() {
                      pathController.text = segments.isNotEmpty
                          ? segments.last
                          : result;
                    });
                    _pendingBackupUri = result;
                  },
                  child: Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: isDark
                            ? const Color(0xFF2A2A2A)
                            : const Color(0xFFE0E0E0),
                      ),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          Icons.folder_outlined,
                          color: isDark ? Colors.white54 : Colors.black54,
                          size: 20,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            pathController.text.isEmpty
                                ? 'Select directory...'
                                : pathController.text,
                            style: TextStyle(
                              color: pathController.text.isEmpty
                                  ? (isDark ? Colors.white38 : Colors.black38)
                                  : (isDark ? Colors.white : Colors.black),
                              fontSize: 13,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'Backup Password',
                  style: TextStyle(
                    color: isDark ? Colors.white : Colors.black,
                    fontWeight: FontWeight.w500,
                    fontSize: 13,
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: passwordController,
                  obscureText: obscure,
                  style: TextStyle(color: isDark ? Colors.white : Colors.black),
                  decoration: InputDecoration(
                    hintText: 'Enter password',
                    suffixIcon: IconButton(
                      icon: Icon(
                        obscure ? Icons.visibility : Icons.visibility_off,
                      ),
                      onPressed: () => setDialogState(() => obscure = !obscure),
                    ),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () async {
                if (pathController.text.isEmpty ||
                    passwordController.text.length < 8 ||
                    _pendingBackupUri == null) {
                  return;
                }
                await provider.setBackupUri(_pendingBackupUri!);
                await provider.setBackupPath(pathController.text);
                await provider.setBackupPassword(passwordController.text);
                await provider.setEnabled(true);
                _pendingBackupUri = null;
                if (dialogContext.mounted) Navigator.pop(dialogContext);
              },
              child: const Text('Enable'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickAutoBackupPath(AutoBackupProvider provider) async {
    final result = await SafService.pickDirectory();
    if (result == null) return;
    await provider.setBackupUri(result);
    final segments = Uri.parse(result).pathSegments;
    await provider.setBackupPath(segments.isNotEmpty ? segments.last : result);
  }

  void _showChangeAutoBackupPasswordDialog(AutoBackupProvider provider) {
    final isDark = context.read<ThemeProvider>().isDarkMode;
    final passwordController = TextEditingController();
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
        title: const Text(
          'Change Backup Password',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        content: TextField(
          controller: passwordController,
          obscureText: true,
          style: TextStyle(color: isDark ? Colors.white : Colors.black),
          decoration: const InputDecoration(
            hintText: 'Enter new password (min 8 characters)',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              if (passwordController.text.length < 8) return;
              await provider.setBackupPassword(passwordController.text);
              if (dialogContext.mounted) Navigator.pop(dialogContext);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }
}
