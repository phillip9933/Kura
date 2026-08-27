// ignore_for_file: deprecated_member_use

import 'package:url_launcher/url_launcher.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:local_auth/local_auth.dart';
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/pages/section_settings_page.dart';
import 'package:kura/pages/backup_storage_page.dart';
import 'package:kura/pages/barcode_scanning_page.dart';
import 'package:kura/pages/expiry_alerts_page.dart';
import 'package:kura/pages/general_display_page.dart';
import 'package:kura/pages/navigation_layout_page.dart';
import 'package:kura/services/backup_service.dart';
import 'package:kura/models/provider_helper.dart';
import 'package:kura/models/db_helper.dart';
import 'package:kura/models/auto_backup_provider.dart';
import 'package:kura/screens/archive_screen.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  String? _appVersion;
  bool _isAppVersionLoading = true;
  static const _appInfoChannel = MethodChannel('app.kura.wallet/app_info');

  @override
  void initState() {
    super.initState();
    _loadAppVersion();
  }

  Future<void> _loadAppVersion() async {
    try {
      final version = await _appInfoChannel.invokeMethod<String>('getVersion');
      if (!mounted) return;
      setState(() {
        _appVersion = version;
        _isAppVersionLoading = false;
      });
    } on PlatformException catch (error) {
      if (kDebugMode) {
        debugPrint('SettingsPage: unable to load app version: $error');
      }
      if (mounted) setState(() => _isAppVersionLoading = false);
    } on MissingPluginException catch (error) {
      if (kDebugMode) {
        debugPrint('SettingsPage: app version channel is unavailable: $error');
      }
      if (mounted) setState(() => _isAppVersionLoading = false);
    }
  }

  String get _appVersionSubtitle {
    if (_isAppVersionLoading) return 'Loading version…';
    if (_appVersion == null) return 'Version unavailable';
    return 'Kura v${_appVersion!.split('+').first}';
  }

  Future<bool> _authenticateForDestructiveAction() async {
    if (Platform.isLinux) return true;
    final auth = LocalAuthentication();
    final isDeviceSupported = await auth.isDeviceSupported();
    if (!isDeviceSupported) return true;
    return await auth.authenticate(
      localizedReason: 'Authenticate to perform this action',
      options: const AuthenticationOptions(stickyAuth: true),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final startupProvider = Provider.of<StartupSettingsProvider>(context);
    final autoBackupProvider = Provider.of<AutoBackupProvider>(context);
    final isDark = themeProvider.isDarkMode;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
        leading: Container(
          margin: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF0F0F0),
            borderRadius: BorderRadius.circular(12),
          ),
          child: IconButton(
            icon: Icon(
              Icons.arrow_back_ios_new_rounded,
              color: isDark ? Colors.white : Colors.black,
              size: 20,
            ),
            onPressed: () => Navigator.pop(context),
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: _buildSettingsSections(
          context: context,
          themeProvider: themeProvider,
          startupProvider: startupProvider,
          autoBackupProvider: autoBackupProvider,
          isDark: isDark,
        ),
      ),
    );
  }

  List<Widget> _buildSettingsSections({
    required BuildContext context,
    required ThemeProvider themeProvider,
    required StartupSettingsProvider startupProvider,
    required AutoBackupProvider autoBackupProvider,
    required bool isDark,
  }) {
    final divider = Divider(
      color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFE8E8E8),
      height: 1,
    );

    return [
      _LiquidGlassSection(
        title: 'Data & Security',
        icon: Icons.security_outlined,
        children: [
          _LiquidGlassTile(
            icon: Icons.inventory_2_outlined,
            title: 'Archive',
            subtitle: 'Restore or permanently delete archived items',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ArchiveScreen()),
            ),
          ),
          divider,
          _LiquidGlassTile(
            icon: Icons.shield_outlined,
            title: 'Require Biometrics',
            subtitle: 'Require biometrics when the app starts',
            trailing: Switch(
              value: startupProvider.showAuthenticationScreen,
              onChanged: (_) => startupProvider.toggleAuthenticationScreen(),
            ),
          ),
          divider,
          _LiquidGlassTile(
            icon: Icons.backup_outlined,
            title: 'Backup & Storage',
            subtitle: _getAutoBackupSubtitle(autoBackupProvider),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => BackupStoragePage(
                  onCreateBackup: () => _showBackupDialog(themeProvider),
                  onRestoreBackup: () => _showRestoreDialog(themeProvider),
                  onDeleteAllData: () =>
                      _showDeleteAllDataDialog(themeProvider),
                ),
              ),
            ),
          ),
        ],
      ),
      _LiquidGlassSection(
        title: 'Section Management',
        icon: Icons.tab_outlined,
        children: [
          _LiquidGlassTile(
            icon: Icons.credit_card_outlined,
            title: 'Payments Settings',
            subtitle: 'Categories and custom fields',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) =>
                    const SectionSettingsPage(section: WalletSection.payments),
              ),
            ),
          ),
          divider,
          _LiquidGlassTile(
            icon: Icons.confirmation_number_outlined,
            title: 'Passes Settings',
            subtitle: 'Categories and custom fields',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) =>
                    const SectionSettingsPage(section: WalletSection.passes),
              ),
            ),
          ),
          divider,
          _LiquidGlassTile(
            icon: Icons.badge_outlined,
            title: 'Identity Settings',
            subtitle: 'Categories and custom fields',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) =>
                    const SectionSettingsPage(section: WalletSection.identity),
              ),
            ),
          ),
        ],
      ),
      _LiquidGlassSection(
        title: 'App Preferences',
        icon: Icons.palette_outlined,
        children: [
          _LiquidGlassTile(
            icon: Icons.tune_outlined,
            title: 'General Display',
            subtitle: 'Configure theme, currency, and default tab',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const GeneralDisplayPage()),
            ),
          ),
          divider,
          _LiquidGlassTile(
            icon: Icons.notifications_active_outlined,
            title: 'Expiry Alerts',
            subtitle: 'Configure startup expiry alerts and lead time',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ExpiryAlertsPage()),
            ),
          ),
          divider,
          _LiquidGlassTile(
            icon: Icons.visibility_outlined,
            title: 'Navigation & Layout',
            subtitle: 'Configure tabs, navigation, controls, and search',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const NavigationLayoutPage()),
            ),
          ),
          divider,
          _LiquidGlassTile(
            icon: Icons.screen_rotation_outlined,
            title: 'Barcode & Scanning',
            subtitle: 'Configure barcode display and QR import scanning',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const BarcodeScanningPage()),
            ),
          ),
        ],
      ),
      _LiquidGlassSection(
        title: 'About',
        icon: Icons.info_outline_rounded,
        children: [
          _LiquidGlassTile(
            icon: Icons.info_outline_rounded,
            title: 'App Version & Trademark',
            subtitle: '$_appVersionSubtitle - View trademark information',
            onTap: () => _showTrademarkNotice(isDark),
          ),
          divider,
          _LiquidGlassTile(
            icon: Icons.code_rounded,
            title: 'GitHub & Issue Tracker',
            subtitle: 'View the source code or report an issue',
            onTap: () =>
                _launchExternalUrl('https://github.com/phillip9933/Kura'),
          ),
          divider,
          _LiquidGlassTile(
            icon: Icons.coffee_outlined,
            title: 'Buy Me a Coffee',
            subtitle: 'Support Kura’s development',
            onTap: () =>
                _launchExternalUrl('https://buymeacoffee.com/phillip9933'),
          ),
        ],
      ),
      const SizedBox(height: 30),
    ];
  }

  Future<void> _launchExternalUrl(String url) async {
    HapticFeedback.mediumImpact();
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  void _showTrademarkNotice(bool isDark) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
        title: const Text(
          'Trademark Fair Use Notice',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        content: const SingleChildScrollView(
          child: Text(
            'The Visa, Mastercard, RuPay, American Express, and Discover logos displayed in this application are registered trademarks of their respective owners.\n\n'
            'These logos are used solely for identifying the card network. This usage constitutes nominative fair use.\n\n'
            'This application is not affiliated with, endorsed by, or sponsored by any of these companies.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
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

  void _showBackupDialog(ThemeProvider themeProvider) async {
    final authenticated = await _authenticateForDestructiveAction();
    if (!authenticated || !mounted) return;
    final isDark = themeProvider.isDarkMode;
    showDialog(
      context: context,
      builder: (dialogContext) => _LiquidGlassPasswordDialog(
        title: 'Create Backup',
        content: 'Enter a strong password to encrypt your backup file.',
        buttonText: 'Create Backup',
        isDark: isDark,
        onConfirm: (password) async {
          try {
            await BackupService.createBackup(password);
            if (!mounted) return;
            if (dialogContext.mounted) {
              Navigator.pop(dialogContext);
            }
          } catch (_) {
            if (!mounted) return;
            if (dialogContext.mounted) {
              Navigator.pop(dialogContext);
            }
          }
        },
      ),
    );
  }

  void _showRestoreDialog(ThemeProvider themeProvider) async {
    final authenticated = await _authenticateForDestructiveAction();
    if (!authenticated || !mounted) return;
    final isDark = themeProvider.isDarkMode;
    showDialog(
      context: context,
      builder: (dialogContext) => _LiquidGlassPasswordDialog(
        title: 'Restore Backup',
        content:
            'Enter the password for the backup file. This will replace all current data.',
        buttonText: 'Restore',
        isDestructive: true,
        isDark: isDark,
        validatePassword: false,
        onConfirm: (password) async {
          try {
            final walletProvider = context.read<WalletProvider>();
            final passProvider = context.read<PassProvider>();
            final identityProvider = context.read<IdentityProvider>();
            final tProvider = context.read<ThemeProvider>();
            final sProvider = context.read<StartupSettingsProvider>();

            await BackupService.restoreBackup(password, context: dialogContext);

            if (dialogContext.mounted) {
              Navigator.pop(dialogContext);
            }

            if (!mounted) return;

            // Reload all providers to reflect restored data and settings
            walletProvider.fetchWallets();
            passProvider.fetchPasses();
            identityProvider.fetchIdentities();
            await tProvider.init();
            await sProvider.loadStartupSettings();

            if (!mounted) return;
          } catch (_) {
            if (dialogContext.mounted) {
              Navigator.pop(dialogContext);
            }
            if (!mounted) return;
          }
        },
      ),
    );
  }

  void _showDeleteAllDataDialog(ThemeProvider themeProvider) async {
    final authenticated = await _authenticateForDestructiveAction();
    if (!authenticated || !mounted) return;
    final isDark = themeProvider.isDarkMode;
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
        title: const Text('Delete All Data?'),
        content: const Text(
          'This will permanently delete all wallets, passes, and images.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              await _performDeleteAllData();
              if (ctx.mounted) Navigator.pop(ctx);
            },
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            child: const Text('Delete Everything'),
          ),
        ],
      ),
    );
  }

  Future<void> _performDeleteAllData() async {
    final walletProvider = context.read<WalletProvider>();
    final passProvider = context.read<PassProvider>();
    final identityProvider = context.read<IdentityProvider>();

    try {
      // Bulk delete wallets
      final wallets = await DatabaseHelper.instance.getWallets();
      if (wallets.isNotEmpty) {
        final db = await DatabaseHelper.instance.database;
        final batch = db.batch();
        for (var w in wallets) {
          if (w.id != null) {
            batch.delete('wallets', where: 'id = ?', whereArgs: [w.id]);
          }
        }
        await batch.commit(noResult: true);
        // Delete image files
        for (var w in wallets) {
          await DatabaseHelper.deleteImageFile(w.frontImagePath);
          await DatabaseHelper.deleteImageFile(w.backImagePath);
        }
      }

      // Bulk delete passes
      final passes = await PassDatabaseHelper.instance.getAllPasses();
      if (passes.isNotEmpty) {
        final db = await PassDatabaseHelper.instance.database;
        final batch = db.batch();
        for (var p in passes) {
          if (p.id != null) {
            batch.delete('passes', where: 'id = ?', whereArgs: [p.id]);
          }
        }
        await batch.commit(noResult: true);
        for (var p in passes) {
          await DatabaseHelper.deleteImageFile(p.frontImagePath);
          await DatabaseHelper.deleteImageFile(p.backImagePath);
          await DatabaseHelper.deleteImageFile(p.stripImagePath);
          await DatabaseHelper.deleteImageFile(p.thumbnailImagePath);
        }
      }

      // Bulk delete identities
      final identities = await IdentityDatabaseHelper.instance
          .getAllIdentities();
      if (identities.isNotEmpty) {
        final db = await IdentityDatabaseHelper.instance.database;
        final batch = db.batch();
        for (var i in identities) {
          if (i.id != null) {
            batch.delete('identities', where: 'id = ?', whereArgs: [i.id]);
          }
        }
        await batch.commit(noResult: true);
        for (var i in identities) {
          await DatabaseHelper.deleteImageFile(i.frontImagePath);
          await DatabaseHelper.deleteImageFile(i.backImagePath);
        }
      }

      final directory = await getApplicationDocumentsDirectory();
      final dir = Directory(directory.path);
      if (await dir.exists()) {
        final deleteFutures = <Future>[];
        for (var f in dir.listSync()) {
          if (f is File) {
            final basename = f.path.split(Platform.pathSeparator).last;
            final isTimestampImage = RegExp(
              r'^\d{16,}\.(png|jpg)$',
            ).hasMatch(basename);
            if (basename.endsWith('.enc') || isTimestampImage) {
              deleteFutures.add(f.delete());
            }
          }
        }
        if (deleteFutures.isNotEmpty) {
          await Future.wait(deleteFutures);
        }
      }

      if (!mounted) return;

      walletProvider.fetchWallets();
      passProvider.fetchPasses();
      identityProvider.fetchIdentities();
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('All data deleted.')));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Delete failed. Please try again.')),
      );
    }
  }
}

class _LiquidGlassSection extends StatelessWidget {
  final String title;
  final IconData? icon;
  final List<Widget> children;
  const _LiquidGlassSection({
    required this.title,
    this.icon,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Provider.of<ThemeProvider>(context).isDarkMode;
    final color = isDark ? Colors.white38 : Colors.black38;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 8, top: 12),
          child: Row(
            children: [
              if (icon != null) Icon(icon, size: 14, color: color),
              const SizedBox(width: 8),
              Text(
                title.toUpperCase(),
                style: TextStyle(
                  color: color,
                  fontWeight: FontWeight.bold,
                  fontSize: 11,
                  letterSpacing: 1.2,
                ),
              ),
            ],
          ),
        ),
        ClipRRect(
          borderRadius: BorderRadius.circular(20),
          child: Material(
            color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF5F5F5),
            child: Container(
              decoration: BoxDecoration(
                border: Border.all(
                  color: isDark
                      ? const Color(0xFF2A2A2A)
                      : const Color(0xFFE8E8E8),
                  width: 0.5,
                ),
              ),
              child: Column(children: children),
            ),
          ),
        ),
        const SizedBox(height: 16),
      ],
    );
  }
}

class _LiquidGlassTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;
  const _LiquidGlassTile({
    required this.icon,
    required this.title,
    this.subtitle,
    this.trailing,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Provider.of<ThemeProvider>(context).isDarkMode;
    final textColor = isDark ? Colors.white : Colors.black;
    return ListTile(
      onTap: onTap,
      leading: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFEEEEEE),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Icon(icon, color: textColor, size: 20),
      ),
      title: Text(
        title,
        style: TextStyle(
          color: textColor,
          fontWeight: FontWeight.w500,
          fontSize: 14,
        ),
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle!,
              style: TextStyle(
                color: isDark ? Colors.white54 : Colors.black54,
                fontSize: 12,
              ),
            )
          : null,
      trailing:
          trailing ??
          (onTap != null
              ? Icon(
                  Icons.arrow_forward_ios_rounded,
                  size: 14,
                  color: isDark ? Colors.white30 : Colors.black26,
                )
              : null),
    );
  }
}

class _LiquidGlassPasswordDialog extends StatefulWidget {
  final String title;
  final String content;
  final String buttonText;
  final bool isDestructive;
  final bool isDark;
  final bool validatePassword;
  final Future<void> Function(String) onConfirm;
  const _LiquidGlassPasswordDialog({
    required this.title,
    required this.content,
    required this.buttonText,
    this.isDestructive = false,
    required this.isDark,
    this.validatePassword = true,
    required this.onConfirm,
  });

  @override
  State<_LiquidGlassPasswordDialog> createState() =>
      _LiquidGlassPasswordDialogState();
}

class _LiquidGlassPasswordDialogState
    extends State<_LiquidGlassPasswordDialog> {
  late final TextEditingController _passwordController;
  bool _isLoading = false;
  bool _obscure = true;
  String? _passwordError;

  static const int _minPasswordLength = 8;

  @override
  void initState() {
    super.initState();
    _passwordController = TextEditingController();
  }

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }

  void _validateAndConfirm() {
    final password = _passwordController.text;
    if (widget.validatePassword && password.length < _minPasswordLength) {
      setState(() {
        _passwordError =
            'Password must be at least $_minPasswordLength characters';
      });
      return;
    }
    setState(() {
      _passwordError = null;
      _isLoading = true;
    });
    widget.onConfirm(password).then((_) {
      if (mounted) setState(() => _isLoading = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: widget.isDark ? const Color(0xFF0A0A0A) : Colors.white,
      title: Text(
        widget.title,
        style: TextStyle(
          color: widget.isDark ? Colors.white : Colors.black,
          fontWeight: FontWeight.bold,
        ),
      ),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            widget.content,
            style: TextStyle(
              color: widget.isDark ? Colors.white70 : Colors.black87,
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _passwordController,
            obscureText: _obscure,
            style: TextStyle(
              color: widget.isDark ? Colors.white : Colors.black,
            ),
            onChanged: (_) {
              if (_passwordError != null) {
                setState(() => _passwordError = null);
              }
            },
            decoration: InputDecoration(
              labelText: 'Password',
              errorText: _passwordError,
              suffixIcon: IconButton(
                icon: Icon(_obscure ? Icons.visibility : Icons.visibility_off),
                onPressed: () => setState(() => _obscure = !_obscure),
              ),
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _isLoading ? null : _validateAndConfirm,
          style: FilledButton.styleFrom(
            backgroundColor: widget.isDestructive
                ? Colors.red
                : (widget.isDark ? Colors.white : Colors.black),
          ),
          child: _isLoading
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : Text(widget.buttonText),
        ),
      ],
    );
  }
}
