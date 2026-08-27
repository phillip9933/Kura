import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:kura/services/clipboard_service.dart';
import 'package:receive_sharing_intent/receive_sharing_intent.dart';
import 'package:barcode_scan2/barcode_scan2.dart';
import 'package:provider/provider.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/pages/add_card_screen.dart';
import 'package:kura/pages/settings_page.dart';
import 'package:kura/widgets/glass_credit_card.dart';
import 'package:kura/screens/barcode_card_details_screen.dart';
import 'package:kura/services/encryption_service.dart';
import '../models/db_helper.dart';
import '../models/provider_helper.dart';
import '../models/theme_provider.dart';
import '../pages/walletdetails.dart';
import 'package:kura/widgets/identity_card_widget.dart';
import 'package:kura/screens/identity_card_details_screen.dart';
import 'package:kura/screens/reorder_items_screen.dart';
import 'package:kura/services/auto_backup_service.dart';
import 'package:kura/widgets/pass_grid_card.dart';
import 'package:kura/widgets/encrypted_image_display.dart';
import 'package:kura/models/pass_types.dart';
import 'package:kura/services/pkpass_service.dart';

enum _ExpiryStatus { expired, expiringSoon }

class _ExpiryAlertItem {
  const _ExpiryAlertItem({
    required this.title,
    required this.type,
    required this.expiry,
    required this.status,
  });

  final String title;
  final String type;
  final String expiry;
  final _ExpiryStatus status;
}

/// Smooth route builder Ã¢â‚¬â€ used across the app for premium transitions
class SmoothPageRoute<T> extends PageRouteBuilder<T> {
  final Widget page;

  SmoothPageRoute({required this.page})
    : super(
        pageBuilder: (context, animation, secondaryAnimation) => page,
        transitionsBuilder: (context, animation, secondaryAnimation, child) {
          return child;
        },
        transitionDuration: Duration.zero,
        reverseTransitionDuration: Duration.zero,
      );
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _selectedIndex = 0;
  String _selectedFilter = 'all';
  String _selectedPassFilter = 'all';
  String _selectedIdentityFilter = 'all';

  late final TextEditingController _searchController;
  late final PageController _tabPageController;
  String _searchQuery = "";
  Timer? _debounce;

  // Chunked transfer import state
  final List<String> _transferChunks = [];
  int _expectedTotalChunks = 0;

  StreamSubscription? _intentDataStreamSubscription;
  bool _hasCheckedStartupExpiryAlerts = false;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    _tabPageController = PageController(initialPage: _selectedIndex);

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await Future.wait([
        context.read<WalletProvider>().fetchWallets(),
        context.read<PassProvider>().fetchPasses(),
        context.read<IdentityProvider>().fetchIdentities(),
      ]);
      if (!mounted) return;

      // Initialize selected index from startup settings
      final startupProvider = context.read<StartupSettingsProvider>();
      final initialIndex = startupProvider.defaultScreenIndex;
      setState(() => _selectedIndex = initialIndex);
      final visibleTabs = <int>[
        if (startupProvider.showPaymentsTab) 0,
        if (startupProvider.showPassesTab) 1,
        if (startupProvider.showIdentityTab) 2,
      ];
      _tabPageController.jumpToPage(visibleTabs.indexOf(initialIndex));

      await _showStartupExpiryAlerts();
      if (!mounted) return;
      _initSharingIntent();
    });

    _searchController.addListener(() {
      if (_debounce?.isActive ?? false) _debounce!.cancel();
      _debounce = Timer(const Duration(milliseconds: 300), () {
        if (mounted && _searchQuery != _searchController.text) {
          setState(() {
            _searchQuery = _searchController.text;
          });
        }
      });
    });
  }

  void _initSharingIntent() {
    if (Platform.isLinux || Platform.isWindows || Platform.isMacOS) return;

    // For sharing images while app is in memory
    _intentDataStreamSubscription = ReceiveSharingIntent.instance
        .getMediaStream()
        .listen((value) {
          _handleSharedMedia(value);
        }, onError: (_) {});

    // For sharing media when app was closed/opened via intent.
    ReceiveSharingIntent.instance.getInitialMedia().then((value) async {
      await _handleSharedMedia(value);
      await ReceiveSharingIntent.instance.reset();
    });
  }

  Future<void> _showStartupExpiryAlerts() async {
    if (_hasCheckedStartupExpiryAlerts) return;
    _hasCheckedStartupExpiryAlerts = true;

    final settings = context.read<StartupSettingsProvider>();
    if (!settings.isExpiryNotificationEnabled) return;

    final possibleAlerts = <_ExpiryAlertItem?>[
      ...context.read<WalletProvider>().wallets.map<_ExpiryAlertItem?>(
        (wallet) => _expiryAlertItem(
          title: wallet.name,
          type: 'Payment',
          expiry: wallet.expiry,
          leadMonths: settings.expiryNotificationLeadMonths,
        ),
      ),
      ...context.read<PassProvider>().passes.map<_ExpiryAlertItem?>(
        (pass) => _expiryAlertItem(
          title: pass.organizationName,
          type: 'Pass',
          expiry: pass.expiryDate,
          leadMonths: settings.expiryNotificationLeadMonths,
        ),
      ),
      ...context.read<IdentityProvider>().identities.map<_ExpiryAlertItem?>(
        (card) => _expiryAlertItem(
          title: card.name,
          type: 'Identity',
          expiry: card.expiryDate,
          leadMonths: settings.expiryNotificationLeadMonths,
        ),
      ),
    ];
    final alerts = possibleAlerts.whereType<_ExpiryAlertItem>().toList();
    if (alerts.isEmpty || !mounted) return;

    final expired = alerts
        .where((item) => item.status == _ExpiryStatus.expired)
        .toList();
    final expiringSoon = alerts
        .where((item) => item.status == _ExpiryStatus.expiringSoon)
        .toList();
    final isDark = context.read<ThemeProvider>().isDarkMode;

    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
        title: const Text(
          'Expiry Alerts',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 360),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (expired.isNotEmpty) ...[
                  const Text(
                    'EXPIRED',
                    style: TextStyle(
                      color: Colors.red,
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 1,
                    ),
                  ),
                  const SizedBox(height: 8),
                  ...expired.map(_buildExpiryAlertItem),
                ],
                if (expired.isNotEmpty && expiringSoon.isNotEmpty)
                  const SizedBox(height: 16),
                if (expiringSoon.isNotEmpty) ...[
                  const Text(
                    'EXPIRING SOON',
                    style: TextStyle(
                      color: Colors.orange,
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 1,
                    ),
                  ),
                  const SizedBox(height: 8),
                  ...expiringSoon.map(_buildExpiryAlertItem),
                ],
              ],
            ),
          ),
        ),
        actions: [
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Done'),
          ),
        ],
      ),
    );
  }

  _ExpiryAlertItem? _expiryAlertItem({
    required String title,
    required String type,
    required String? expiry,
    required int leadMonths,
  }) {
    final status = _startupExpiryStatus(expiry, leadMonths);
    if (status == null) return null;
    return _ExpiryAlertItem(
      title: title.trim().isEmpty ? type : title,
      type: type,
      expiry: expiry!.trim(),
      status: status,
    );
  }

  _ExpiryStatus? _startupExpiryStatus(String? value, int leadMonths) {
    if (value == null || value.trim().isEmpty) return null;
    final match = RegExp(r'^(\d{2})/(\d{2})$').firstMatch(value.trim());
    if (match == null) return null;
    final month = int.tryParse(match.group(1)!);
    final year = int.tryParse(match.group(2)!);
    if (month == null || year == null || month < 1 || month > 12) return null;

    final now = DateTime.now();
    final currentMonth = now.year * 12 + now.month;
    final expiryMonth = (2000 + year) * 12 + month;
    if (expiryMonth < currentMonth) return _ExpiryStatus.expired;
    if (expiryMonth <= currentMonth + leadMonths) {
      return _ExpiryStatus.expiringSoon;
    }
    return null;
  }

  Widget _buildExpiryAlertItem(_ExpiryAlertItem item) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Text('${item.title} (${item.type}) — ${item.expiry}'),
    );
  }

  Future<void> _handleSharedMedia(List<SharedMediaFile> files) async {
    if (files.isEmpty || !mounted) return;
    final file = files.first;
    if (file.path.isEmpty) return;

    if (_isPkpassFile(file)) {
      await _importPassFileFromPath(file.path);
      return;
    }

    Navigator.push(
      context,
      SmoothPageRoute(
        page: AddCardScreen(
          initialTabIndex: 1,
          initialSharedImagePath: file.path,
        ),
      ),
    );
  }

  bool _isPkpassFile(SharedMediaFile file) {
    final path = file.path.toLowerCase();
    final mimeType = file.mimeType?.toLowerCase();
    return path.endsWith('.pkpass') ||
        (path.endsWith('.zip') &&
            (mimeType == 'application/vnd.apple.pkpass' ||
                mimeType == 'application/zip')) ||
        mimeType == 'application/vnd.apple.pkpass';
  }

  @override
  void dispose() {
    _intentDataStreamSubscription?.cancel();
    _debounce?.cancel();
    _searchController.dispose();
    _tabPageController.dispose();
    super.dispose();
  }

  void _onItemTapped(int index) {
    HapticFeedback.selectionClick();
    setState(() {
      _selectedIndex = index;
    });
  }

  Future<void> _showAddOptions(int initialTabIndex) async {
    HapticFeedback.mediumImpact();
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.qr_code_scanner_rounded),
                title: const Text('Scan Barcode'),
                subtitle: const Text('Scan a barcode to import shared data'),
                onTap: () {
                  Navigator.pop(sheetContext);
                  _scanAndImport();
                },
              ),
              ListTile(
                leading: const Icon(Icons.file_upload_outlined),
                title: const Text('Import File'),
                subtitle: const Text('Import a .pkpass or .zip pass file'),
                onTap: () {
                  Navigator.pop(sheetContext);
                  _importPassFile();
                },
              ),
              ListTile(
                leading: const Icon(Icons.edit_outlined),
                title: const Text('Manual Input'),
                subtitle: const Text('Create an item in the current section'),
                onTap: () {
                  Navigator.pop(sheetContext);
                  _openManualInput(initialTabIndex);
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _openManualInput(int initialTabIndex) async {
    final result = await Navigator.push<bool>(
      context,
      SmoothPageRoute(page: AddCardScreen(initialTabIndex: initialTabIndex)),
    );
    if (result == true && mounted) {
      await Future.wait([
        context.read<WalletProvider>().fetchWallets(),
        context.read<PassProvider>().fetchPasses(),
        context.read<IdentityProvider>().fetchIdentities(),
      ]);
    }
  }

  Future<void> _importPassFile() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['pkpass', 'zip'],
      );
      final path = result?.files.single.path;
      if (path == null) return;
      await _importPassFileFromPath(path);
    } catch (_) {
      _showImportError('Failed to import pass. Please try again.');
    }
  }

  Future<void> _importPassFileFromPath(String path) async {
    try {
      final pass = await PkpassService.instance.parsePkpass(path);
      if (pass == null) {
        _showImportError('Failed to parse .pkpass file.');
        return;
      }

      if (!mounted) return;
      final confirm = await _showImportConfirmation(
        pass.organizationName,
        'Pass',
      );
      if (confirm != true) return;

      await PassDatabaseHelper.instance.insertPass(pass);
      AutoBackupService.triggerBackup();
      if (!mounted) return;
      await context.read<PassProvider>().fetchPasses();
      _selectPassesTab();
      _showSuccessSnackBar('Pass imported successfully!');
    } catch (_) {
      _showImportError('Failed to import pass. Please try again.');
    }
  }

  void _selectPassesTab() {
    final settings = context.read<StartupSettingsProvider>();
    if (!settings.showPassesTab) return;

    final visibleTabs = <int>[
      if (settings.showPaymentsTab) 0,
      if (settings.showPassesTab) 1,
      if (settings.showIdentityTab) 2,
    ];
    final passesPageIndex = visibleTabs.indexOf(1);
    if (passesPageIndex < 0) return;

    if (_tabPageController.hasClients) {
      _tabPageController.animateToPage(
        passesPageIndex,
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOutCubic,
      );
    } else {
      setState(() => _selectedIndex = 1);
    }
  }

  Future<void> _scanAndImport() async {
    try {
      final scanResult = await BarcodeScanner.scan();
      if (scanResult.type != ResultType.Barcode) return;

      final rawData = scanResult.rawContent;

      // V2/V3: Chunked password-based transfer (V2=PBKDF2, V3=Argon2id)
      if (rawData.startsWith('v2:') || rawData.startsWith('v3:')) {
        _handleChunkScan(rawData);
        return;
      }

      // Legacy v1 single-QR transfer
      final decryptedJson = await EncryptionService.instance
          .decryptFromTransfer(rawData);

      if (decryptedJson == null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Invalid or corrupted sharing code.')),
          );
        }
        return;
      }

      final payload = jsonDecode(decryptedJson) as Map<String, dynamic>;
      final type = payload['type'] as String?;
      final data = payload['data'] as Map<String, dynamic>?;

      if (type == null || data == null || !_isValidImportType(type)) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Invalid sharing code format.')),
          );
        }
        return;
      }

      if (type == 'pass') {
        if (!_isValidPassData(data)) {
          _showImportError('Invalid pass data.');
          return;
        }
        final newPass = Pass.fromMap(data);
        if (mounted) {
          final confirm = await _showImportConfirmation(
            newPass.organizationName,
            'Pass',
          );
          if (confirm == true) {
            await PassDatabaseHelper.instance.insertPass(newPass);
            AutoBackupService.triggerBackup();
            if (mounted) {
              context.read<PassProvider>().fetchPasses();
              _showSuccessSnackBar('Pass imported successfully!');
            }
          }
        }
      } else if (type == 'wallet') {
        if (!_isValidWalletData(data)) {
          _showImportError('Invalid card data.');
          return;
        }
        final newWallet = Wallet.fromMap(data);
        if (mounted) {
          final confirm = await _showImportConfirmation(
            newWallet.name,
            'Payment Card',
          );
          if (confirm == true) {
            await DatabaseHelper.instance.insertWallet(newWallet);
            AutoBackupService.triggerBackup();
            if (mounted) {
              context.read<WalletProvider>().fetchWallets();
              _showSuccessSnackBar('Payment card imported successfully!');
            }
          }
        }
      } else if (type == 'identity') {
        if (!_isValidIdentityData(data)) {
          _showImportError('Invalid identity data.');
          return;
        }
        final newIdentity = IdentityCard.fromMap(data);
        if (mounted) {
          final confirm = await _showImportConfirmation(
            newIdentity.name,
            'Identity Card',
          );
          if (confirm == true) {
            await IdentityDatabaseHelper.instance.insertIdentity(newIdentity);
            AutoBackupService.triggerBackup();
            if (mounted) {
              context.read<IdentityProvider>().fetchIdentities();
              _showSuccessSnackBar('Identity card imported successfully!');
            }
          }
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Failed to import. The sharing code may be corrupted.',
            ),
          ),
        );
      }
    }
  }

  void _handleChunkScan(String rawData) {
    try {
      final parts = rawData.split(':');
      if (parts.length != 6) {
        _showImportError('Invalid chunk format.');
        return;
      }

      final chunkIndex = int.parse(parts[1]);
      final totalChunks = int.parse(parts[2]);

      if (chunkIndex < 0 || chunkIndex >= totalChunks) {
        _showImportError('Invalid chunk index.');
        return;
      }

      if (_transferChunks.isEmpty) {
        _expectedTotalChunks = totalChunks;
      } else if (_expectedTotalChunks != totalChunks) {
        _showImportError('Chunk mismatch. Please restart scanning.');
        _transferChunks.clear();
        _expectedTotalChunks = 0;
        return;
      }

      if (!_transferChunks.asMap().containsKey(chunkIndex)) {
        _transferChunks.add(rawData);
      }

      if (mounted) {
        ScaffoldMessenger.of(context).hideCurrentSnackBar();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Scanned chunk ${_transferChunks.length} of $totalChunks',
            ),
            behavior: SnackBarBehavior.floating,
            duration: const Duration(seconds: 1),
          ),
        );
      }

      if (_transferChunks.length == totalChunks) {
        _promptTransferPassword();
      }
    } catch (_) {
      _showImportError('Failed to parse chunk.');
    }
  }

  void _promptTransferPassword() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) {
        final controller = TextEditingController();
        bool obscure = true;
        return StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
            title: const Text(
              'Enter Transfer Password',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Enter the password that was used to encrypt this transfer.',
                  style: TextStyle(
                    color: isDark ? Colors.white70 : Colors.black87,
                    fontSize: 13,
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: controller,
                  obscureText: obscure,
                  decoration: InputDecoration(
                    labelText: 'Password',
                    border: const OutlineInputBorder(),
                    suffixIcon: IconButton(
                      icon: Icon(
                        obscure ? Icons.visibility : Icons.visibility_off,
                      ),
                      onPressed: () => setDialogState(() => obscure = !obscure),
                    ),
                  ),
                  onSubmitted: (_) =>
                      _decryptAndImportChunks(ctx, controller.text),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () {
                  _transferChunks.clear();
                  _expectedTotalChunks = 0;
                  Navigator.pop(ctx);
                },
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () => _decryptAndImportChunks(ctx, controller.text),
                child: const Text('Import'),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _decryptAndImportChunks(
    BuildContext ctx,
    String password,
  ) async {
    if (password.isEmpty) return;
    Navigator.pop(ctx);

    try {
      final joinedData = _transferChunks.join('\n');
      _transferChunks.clear();
      _expectedTotalChunks = 0;

      final decryptedJson = await EncryptionService.instance
          .decryptFromTransfer(joinedData, password: password);

      if (decryptedJson == null) {
        _showImportError(
          'Decryption failed. Wrong password or corrupted data.',
        );
        return;
      }

      final payload = jsonDecode(decryptedJson) as Map<String, dynamic>;
      final type = payload['type'] as String?;
      final data = payload['data'] as Map<String, dynamic>?;

      if (type == null || data == null || !_isValidImportType(type)) {
        _showImportError('Invalid sharing code format.');
        return;
      }

      if (type == 'pass') {
        if (!_isValidPassData(data)) {
          _showImportError('Invalid pass data.');
          return;
        }
        final newPass = Pass.fromMap(data);
        if (mounted) {
          final confirm = await _showImportConfirmation(
            newPass.organizationName,
            'Pass',
          );
          if (confirm == true) {
            await PassDatabaseHelper.instance.insertPass(newPass);
            AutoBackupService.triggerBackup();
            if (mounted) {
              context.read<PassProvider>().fetchPasses();
              _showSuccessSnackBar('Pass imported successfully!');
            }
          }
        }
      } else if (type == 'wallet') {
        if (!_isValidWalletData(data)) {
          _showImportError('Invalid card data.');
          return;
        }
        final newWallet = Wallet.fromMap(data);
        if (mounted) {
          final confirm = await _showImportConfirmation(
            newWallet.name,
            'Payment Card',
          );
          if (confirm == true) {
            await DatabaseHelper.instance.insertWallet(newWallet);
            AutoBackupService.triggerBackup();
            if (mounted) {
              context.read<WalletProvider>().fetchWallets();
              _showSuccessSnackBar('Payment card imported successfully!');
            }
          }
        }
      } else if (type == 'identity') {
        if (!_isValidIdentityData(data)) {
          _showImportError('Invalid identity data.');
          return;
        }
        final newIdentity = IdentityCard.fromMap(data);
        if (mounted) {
          final confirm = await _showImportConfirmation(
            newIdentity.name,
            'Identity Card',
          );
          if (confirm == true) {
            await IdentityDatabaseHelper.instance.insertIdentity(newIdentity);
            AutoBackupService.triggerBackup();
            if (mounted) {
              context.read<IdentityProvider>().fetchIdentities();
              _showSuccessSnackBar('Identity card imported successfully!');
            }
          }
        }
      }
    } catch (_) {
      _showImportError('Failed to import. Wrong password or corrupted data.');
    }
  }

  bool _isValidImportType(String type) {
    return type == 'pass' || type == 'wallet' || type == 'identity';
  }

  bool _isValidWalletData(Map<String, dynamic> data) {
    return data.containsKey('name') &&
        data.containsKey('number') &&
        data.containsKey('expiry') &&
        data['name'] is String &&
        data['number'] is String &&
        data['expiry'] is String &&
        (data['name'] as String).isNotEmpty &&
        (data['number'] as String).isNotEmpty;
  }

  bool _isValidPassData(Map<String, dynamic> data) {
    return data.containsKey('type') &&
        data.containsKey('organizationName') &&
        data.containsKey('barcodeValue') &&
        data['type'] is String &&
        data['organizationName'] is String &&
        data['barcodeValue'] is String &&
        (data['organizationName'] as String).isNotEmpty;
  }

  bool _isValidIdentityData(Map<String, dynamic> data) {
    return data.containsKey('name') &&
        data.containsKey('value') &&
        data['name'] is String &&
        data['value'] is String &&
        (data['name'] as String).isNotEmpty &&
        (data['value'] as String).isNotEmpty;
  }

  void _showImportError(String message) {
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<bool?> _showImportConfirmation(String name, String typeLabel) {
    return showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Theme.of(context).brightness == Brightness.dark
            ? const Color(0xFF0A0A0A)
            : Colors.white,
        title: Text('Import Shared $typeLabel'),
        content: Text('Do you want to import "$name"?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Import'),
          ),
        ],
      ),
    );
  }

  void _showSuccessSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  void _showPassDeleteConfirmationDialog({
    required int id,
    required String name,
  }) {
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);
    final isDark = themeProvider.isDarkMode;

    showDialog(
      context: context,
      barrierColor: isDark ? Colors.black54 : Colors.black26,
      builder: (BuildContext ctx) {
        return AlertDialog(
          backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
          title: Text(
            'Delete Pass?',
            style: Theme.of(
              context,
            ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
          ),
          content: Text(
            'Are you sure you want to delete "$name"? This action cannot be undone.',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: isDark ? Colors.white70 : Colors.black87,
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: Text(
                'Cancel',
                style: TextStyle(
                  color: isDark ? Colors.white60 : Colors.black54,
                ),
              ),
            ),
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.error,
              ),
              onPressed: () {
                HapticFeedback.mediumImpact();
                context.read<PassProvider>().deletePass(id);
                Navigator.of(ctx).pop();
                ScaffoldMessenger.of(
                  context,
                ).showSnackBar(const SnackBar(content: Text('Pass Deleted!')));
              },
              child: const Text('Delete'),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final startupProvider = Provider.of<StartupSettingsProvider>(context);
    final isDark = themeProvider.isDarkMode;

    final visibleTabs = <int>[
      if (startupProvider.showPaymentsTab) 0,
      if (startupProvider.showPassesTab) 1,
      if (startupProvider.showIdentityTab) 2,
    ];
    final effectiveIndex = startupProvider.isTabVisible(_selectedIndex)
        ? _selectedIndex
        : startupProvider.firstVisibleTabIndex;
    if (effectiveIndex != _selectedIndex) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _selectedIndex != effectiveIndex) {
          setState(() => _selectedIndex = effectiveIndex);
          _tabPageController.jumpToPage(visibleTabs.indexOf(effectiveIndex));
        }
      });
    }
    final selectedNavigationIndex = visibleTabs.indexOf(effectiveIndex);
    final showBottomSearch = _isSearchBarAtBottom(startupProvider);
    final showBottomControlRow =
        startupProvider.controlRowPosition == ControlRowPosition.bottom;
    final showBottomNavigation =
        startupProvider.showBottomNavigationBar &&
        startupProvider.hasMultipleVisibleTabs;

    return Scaffold(
      appBar: null,
      resizeToAvoidBottomInset: true,
      floatingActionButton: Padding(
        padding: EdgeInsets.only(
          bottom: (showBottomSearch ? 68 : 0) + (showBottomControlRow ? 56 : 0),
        ),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: isDark
                    ? Colors.white.withValues(alpha: 0.12)
                    : Colors.black.withValues(alpha: 0.2),
                blurRadius: 30,
                offset: const Offset(0, 12),
                spreadRadius: -2,
              ),
            ],
          ),
          child: FloatingActionButton(
            onPressed: () => _showAddOptions(effectiveIndex),
            child: const Icon(Icons.add_rounded),
          ),
        ),
      ),
      body: Stack(
        children: [
          PageView(
            controller: _tabPageController,
            physics: startupProvider.gestureNavigationEnabled
                ? const PageScrollPhysics(parent: ClampingScrollPhysics())
                : const NeverScrollableScrollPhysics(),
            onPageChanged: (page) => _onItemTapped(visibleTabs[page]),
            children: [
              if (startupProvider.showPaymentsTab)
                SafeArea(
                  top: true,
                  bottom: false,
                  child: _buildPaymentsTab(context),
                ),
              if (startupProvider.showPassesTab)
                SafeArea(
                  top: true,
                  bottom: false,
                  child: _buildPassesTab(context),
                ),
              if (startupProvider.showIdentityTab)
                SafeArea(
                  top: true,
                  bottom: false,
                  child: _buildIdentitiesTab(context),
                ),
            ],
          ),
          if (showBottomSearch)
            SafeArea(
              top: false,
              child: Align(
                alignment: Alignment.bottomCenter,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                  child: _buildSearchField(
                    isDark,
                    _searchHintForSection(effectiveIndex),
                  ),
                ),
              ),
            ),
          if (showBottomControlRow)
            SafeArea(
              top: false,
              child: Align(
                alignment: Alignment.bottomCenter,
                child: Padding(
                  padding: EdgeInsets.fromLTRB(
                    16,
                    8,
                    16,
                    showBottomSearch ? 68 : 8,
                  ),
                  child: _buildBottomControlRow(
                    effectiveIndex,
                    isDark,
                    startupProvider,
                  ),
                ),
              ),
            ),
        ],
      ),
      bottomNavigationBar: !showBottomNavigation
          ? null
          : Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  decoration: BoxDecoration(
                    color: isDark ? Colors.black : Colors.white,
                    border: Border(
                      top: BorderSide(
                        color: isDark
                            ? Colors.white.withValues(alpha: 0.078)
                            : Colors.black.withValues(alpha: 0.051),
                      ),
                    ),
                  ),
                  child: NavigationBar(
                    selectedIndex: selectedNavigationIndex,
                    onDestinationSelected: (index) {
                      _tabPageController.animateToPage(
                        index,
                        duration: const Duration(milliseconds: 250),
                        curve: Curves.easeOutCubic,
                      );
                    },
                    animationDuration: Duration.zero,
                    elevation: 0,
                    destinations: <Widget>[
                      if (startupProvider.showPaymentsTab)
                        const NavigationDestination(
                          icon: Icon(Icons.credit_card_outlined),
                          selectedIcon: Icon(Icons.credit_card),
                          label: 'Payments',
                        ),
                      if (startupProvider.showPassesTab)
                        const NavigationDestination(
                          icon: Icon(Icons.confirmation_number_outlined),
                          selectedIcon: Icon(Icons.confirmation_number),
                          label: 'Passes',
                        ),
                      if (startupProvider.showIdentityTab)
                        const NavigationDestination(
                          icon: Icon(Icons.badge_outlined),
                          selectedIcon: Icon(Icons.badge),
                          label: 'Identity',
                        ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildPaymentsTab(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);
    final isDark = themeProvider.isDarkMode;
    final settings = context.watch<StartupSettingsProvider>();

    if (!settings.isPassSearchEnabled && _searchQuery.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && !settings.isPassSearchEnabled) {
          _searchController.clear();
        }
      });
    }

    return Consumer<WalletProvider>(
      builder: (context, provider, child) {
        final wallets = provider.wallets;

        // 1. First, filter by the search query.
        final List<Wallet> searchedWallets =
            !settings.isPassSearchEnabled || _searchQuery.isEmpty
            ? wallets
            : wallets.where((wallet) {
                final query = _searchQuery.toLowerCase();
                final nameMatch = wallet.name.toLowerCase().contains(query);
                final maskedNumber = wallet.number.length >= 4
                    ? 'Ã¢â‚¬Â¢Ã¢â‚¬Â¢Ã¢â‚¬Â¢Ã¢â‚¬Â¢${wallet.number.substring(wallet.number.length - 4)}'
                    : wallet.number;
                final numberMatch =
                    maskedNumber.contains(query) ||
                    wallet.number.contains(query);
                final networkMatch =
                    wallet.network?.toLowerCase().contains(query) ?? false;
                final issuerMatch =
                    wallet.issuer?.toLowerCase().contains(query) ?? false;
                final typeMatch =
                    wallet.cardtype?.toLowerCase().contains(query) ?? false;
                return nameMatch ||
                    numberMatch ||
                    networkMatch ||
                    issuerMatch ||
                    typeMatch;
              }).toList();

        // 2. Then, filter the result by the network button.
        final paymentCategories = settings.categoriesFor(
          WalletSection.payments,
        );
        final activePaymentFilter = paymentCategories.contains(_selectedFilter)
            ? _selectedFilter
            : 'all';
        final List<Wallet> filteredWallets = searchedWallets.where((wallet) {
          if (activePaymentFilter == 'all') return true;
          return _paymentCategoryMatches(
            network: wallet.network,
            category: activePaymentFilter,
          );
        }).toList();

        return CustomScrollView(
          physics: const BouncingScrollPhysics(
            parent: AlwaysScrollableScrollPhysics(),
          ),
          slivers: [
            if (_isSearchBarAtTop(settings))
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                  child: _buildSearchField(isDark, 'Search cards...'),
                ),
              ),
            if (settings.controlRowPosition == ControlRowPosition.top)
              SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.fromLTRB(
                    16,
                    _isSearchBarAtTop(settings) ? 0 : 16,
                    16,
                    8,
                  ),
                  child: _buildPaymentsActionsRow(
                    isDark: isDark,
                    settings: settings,
                    searchHint: 'Search cards...',
                    isGridView: true,
                    onViewToggle: () {
                      HapticFeedback.selectionClick();
                      final columns = settings.gridColumnsFor(
                        WalletSection.payments,
                      );
                      settings.setGridColumns(
                        WalletSection.payments,
                        columns == 3 ? 1 : columns + 1,
                      );
                    },
                  ),
                ),
              ),
            SliverToBoxAdapter(
              child: SizedBox(
                height: settings.controlRowPosition == ControlRowPosition.bottom
                    ? 68
                    : 12,
              ),
            ),
            // Cards list
            if (filteredWallets.isEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.all(48.0),
                  child: Center(
                    child: Text(
                      wallets.isEmpty
                          ? "No credit or debit cards yet.\nTap the '+' to add one."
                          : 'No cards found.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: isDark ? Colors.white54 : Colors.black45,
                      ),
                    ),
                  ),
                ),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                sliver: SliverGrid(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: settings.gridColumnsFor(
                      WalletSection.payments,
                    ),
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 1.586,
                  ),
                  delegate: SliverChildBuilderDelegate((context, index) {
                    final wallet = filteredWallets[index];
                    return _buildWalletGridTile(
                      wallet: wallet,
                      displayMode: settings.gridModeFor(WalletSection.payments),
                      onLongPress: () => _showWalletContextMenu(wallet),
                      onTap: () async {
                        final provider = context.read<WalletProvider>();
                        final fullWallet = await provider.getWalletDetails(
                          wallet.id!,
                        );
                        if (fullWallet != null && context.mounted) {
                          Navigator.push(
                            context,
                            SmoothPageRoute(
                              page: WalletDetailScreen(wallet: fullWallet),
                            ),
                          );
                        }
                      },
                    );
                  }, childCount: filteredWallets.length),
                ),
              ),
            // Bottom padding
            const SliverToBoxAdapter(child: SizedBox(height: 80)),
          ],
        );
      },
    );
  }

  Widget _buildPassSearchField(bool isDark) {
    return _buildSearchField(isDark, 'Search passes...');
  }

  Widget _buildBottomControlRow(
    int section,
    bool isDark,
    StartupSettingsProvider settings,
  ) {
    switch (section) {
      case 0:
        return _buildPaymentsActionsRow(
          isDark: isDark,
          settings: settings,
          searchHint: 'Search cards...',
          isGridView: true,
          onViewToggle: () {
            HapticFeedback.selectionClick();
            final columns = settings.gridColumnsFor(WalletSection.payments);
            settings.setGridColumns(
              WalletSection.payments,
              columns == 3 ? 1 : columns + 1,
            );
          },
        );
      case 1:
        final categories = settings.categoriesFor(WalletSection.passes);
        return _buildPassesActionsRow(
          isDark: isDark,
          settings: settings,
          categories: categories,
          value: categories.contains(_selectedPassFilter)
              ? _selectedPassFilter
              : 'all',
        );
      case 2:
        final categories = settings.categoriesFor(WalletSection.identity);
        return _buildUnifiedActionsRow(
          isDark: isDark,
          settings: settings,
          searchHint: 'Search identities...',
          categorySelector: _buildIdentityCategorySelector(
            isDark: isDark,
            categories: categories,
            value: categories.contains(_selectedIdentityFilter)
                ? _selectedIdentityFilter
                : 'all',
          ),
          isGridView: true,
          onViewToggle: () {
            HapticFeedback.selectionClick();
            final columns = settings.gridColumnsFor(WalletSection.identity);
            settings.setGridColumns(
              WalletSection.identity,
              columns == 3 ? 1 : columns + 1,
            );
          },
        );
      default:
        return const SizedBox.shrink();
    }
  }

  Widget _buildPassesActionsRow({
    required bool isDark,
    required StartupSettingsProvider settings,
    required List<String> categories,
    required String value,
  }) {
    return _buildUnifiedActionsRow(
      isDark: isDark,
      settings: settings,
      searchHint: 'Search passes...',
      categorySelector: Container(
        height: 40,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        decoration: BoxDecoration(
          color: isDark
              ? Colors.white.withValues(alpha: 0.05)
              : Colors.black.withValues(alpha: 0.03),
          borderRadius: BorderRadius.circular(12),
        ),
        child: DropdownButtonHideUnderline(
          child: DropdownButton<String>(
            value: value,
            isExpanded: true,
            icon: Icon(
              Icons.keyboard_arrow_down,
              color: isDark ? Colors.white54 : Colors.black54,
            ),
            dropdownColor: isDark ? const Color(0xFF1E1E1E) : Colors.white,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: isDark ? Colors.white : Colors.black87,
            ),
            onChanged: (newValue) {
              if (newValue == null) return;
              HapticFeedback.selectionClick();
              setState(() => _selectedPassFilter = newValue);
            },
            items: [
              const DropdownMenuItem(
                value: 'all',
                child: Text('All Categories'),
              ),
              ...categories.map(
                (category) =>
                    DropdownMenuItem(value: category, child: Text(category)),
              ),
            ],
          ),
        ),
      ),
      isGridView: true,
      onViewToggle: () {
        HapticFeedback.selectionClick();
        final columns = settings.gridColumnsFor(WalletSection.passes);
        settings.setGridColumns(
          WalletSection.passes,
          columns == 3 ? 1 : columns + 1,
        );
      },
    );
  }

  String _searchHintForSection(int section) => switch (section) {
    0 => 'Search cards...',
    1 => 'Search passes...',
    2 => 'Search identities...',
    _ => 'Search...',
  };

  bool _isSearchBarAtTop(StartupSettingsProvider settings) {
    return settings.isPassSearchEnabled &&
        settings.passSearchStyle == PassSearchStyle.alwaysOn &&
        settings.searchBarPosition == SearchBarPosition.top;
  }

  bool _isSearchBarAtBottom(StartupSettingsProvider settings) {
    return settings.isPassSearchEnabled &&
        settings.passSearchStyle == PassSearchStyle.alwaysOn &&
        settings.searchBarPosition == SearchBarPosition.bottom;
  }

  Widget _buildSearchField(bool isDark, String hintText) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: isDark
            ? Colors.white.withValues(alpha: 0.059)
            : Colors.black.withValues(alpha: 0.031),
        border: Border.all(
          color: isDark
              ? Colors.white.withValues(alpha: 0.102)
              : Colors.black.withValues(alpha: 0.059),
        ),
      ),
      child: TextField(
        controller: _searchController,
        style: TextStyle(color: isDark ? Colors.white : Colors.black),
        decoration: InputDecoration(
          filled: false,
          border: InputBorder.none,
          enabledBorder: InputBorder.none,
          focusedBorder: InputBorder.none,
          hintText: hintText,
          hintStyle: TextStyle(color: isDark ? Colors.white38 : Colors.black38),
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 12,
          ),
          suffixIcon: _searchQuery.isNotEmpty
              ? IconButton(
                  icon: Icon(
                    Icons.clear_rounded,
                    color: isDark ? Colors.white54 : Colors.black45,
                  ),
                  onPressed: _searchController.clear,
                )
              : null,
        ),
      ),
    );
  }

  Widget _buildPassControlButton({
    required bool isDark,
    required IconData icon,
    required String tooltip,
    required VoidCallback onPressed,
  }) {
    return Container(
      height: 40,
      width: 40,
      decoration: BoxDecoration(
        color: isDark
            ? Colors.white.withValues(alpha: 0.05)
            : Colors.black.withValues(alpha: 0.03),
        borderRadius: BorderRadius.circular(12),
      ),
      child: IconButton(
        icon: Icon(icon, color: isDark ? Colors.white70 : Colors.black87),
        tooltip: tooltip,
        onPressed: onPressed,
      ),
    );
  }

  Future<void> _showPassSearchDialog() async {
    await _showSearchDialog('Search Passes', 'Search passes...');
  }

  Future<void> _showSearchDialog(String title, String hintText) async {
    final isDark = context.read<ThemeProvider>().isDarkMode;
    final focusNode = FocusNode();
    await showDialog<void>(
      context: context,
      builder: (dialogContext) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          focusNode.requestFocus();
        });
        return AlertDialog(
          backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
          title: Text(title, style: TextStyle(fontWeight: FontWeight.bold)),
          content: TextField(
            controller: _searchController,
            focusNode: focusNode,
            autofocus: true,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              hintText: hintText,
              prefixIcon: const Icon(Icons.search_rounded),
              suffixIcon: _searchQuery.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear_rounded),
                      onPressed: _searchController.clear,
                    )
                  : null,
            ),
            onSubmitted: (_) => Navigator.pop(dialogContext),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Done'),
            ),
          ],
        );
      },
    );
    focusNode.dispose();
  }

  Widget _buildUnifiedActionsRow({
    required bool isDark,
    required StartupSettingsProvider settings,
    required String searchHint,
    Widget? categorySelector,
    bool? isGridView,
    VoidCallback? onViewToggle,
  }) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        if (categorySelector != null) ...[
          Expanded(child: categorySelector),
          const SizedBox(width: 8),
        ],
        if (settings.isPassSearchEnabled &&
            settings.passSearchStyle == PassSearchStyle.icon) ...[
          _buildPassControlButton(
            isDark: isDark,
            icon: Icons.search_rounded,
            tooltip: searchHint,
            onPressed: () => _showSearchDialog('Search', searchHint),
          ),
          const SizedBox(width: 8),
        ],
        if (isGridView != null && onViewToggle != null) ...[
          _buildPassControlButton(
            isDark: isDark,
            icon: Icons.grid_view_rounded,
            tooltip: 'Change grid columns',
            onPressed: onViewToggle,
          ),
          const SizedBox(width: 8),
        ],
        _buildPassControlButton(
          isDark: isDark,
          icon: Icons.settings_outlined,
          tooltip: 'Settings',
          onPressed: () {
            HapticFeedback.lightImpact();
            Navigator.push(
              context,
              SmoothPageRoute(page: const SettingsPage()),
            );
          },
        ),
      ],
    );
  }

  Widget _buildPaymentsActionsRow({
    required bool isDark,
    required StartupSettingsProvider settings,
    required String searchHint,
    required bool isGridView,
    required VoidCallback onViewToggle,
  }) {
    return Row(
      children: [
        Expanded(
          child: Container(
            height: 40,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(
              color: isDark
                  ? Colors.white.withValues(alpha: 0.05)
                  : Colors.black.withValues(alpha: 0.03),
              borderRadius: BorderRadius.circular(12),
            ),
            child: DropdownButtonHideUnderline(
              child: DropdownButton<String>(
                value:
                    settings
                        .categoriesFor(WalletSection.payments)
                        .contains(_selectedFilter)
                    ? _selectedFilter
                    : 'all',
                isExpanded: true,
                icon: Icon(
                  Icons.keyboard_arrow_down,
                  color: isDark ? Colors.white54 : Colors.black54,
                ),
                dropdownColor: isDark ? const Color(0xFF1E1E1E) : Colors.white,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: isDark ? Colors.white : Colors.black87,
                ),
                onChanged: (value) {
                  if (value != null) {
                    HapticFeedback.selectionClick();
                    setState(() => _selectedFilter = value);
                  }
                },
                items: [
                  const DropdownMenuItem(
                    value: 'all',
                    child: Text('All Cards'),
                  ),
                  ...settings
                      .categoriesFor(WalletSection.payments)
                      .map(
                        (category) => DropdownMenuItem(
                          value: category,
                          child: Text(category),
                        ),
                      ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(width: 8),
        ..._buildUnifiedActionButtons(
          isDark: isDark,
          settings: settings,
          searchHint: searchHint,
          isGridView: isGridView,
          onViewToggle: onViewToggle,
        ),
      ],
    );
  }

  List<Widget> _buildUnifiedActionButtons({
    required bool isDark,
    required StartupSettingsProvider settings,
    required String searchHint,
    bool? isGridView,
    VoidCallback? onViewToggle,
  }) {
    return [
      if (settings.isPassSearchEnabled &&
          settings.passSearchStyle == PassSearchStyle.icon) ...[
        _buildPassControlButton(
          isDark: isDark,
          icon: Icons.search_rounded,
          tooltip: searchHint,
          onPressed: () => _showSearchDialog('Search', searchHint),
        ),
        const SizedBox(width: 8),
      ],
      if (isGridView != null && onViewToggle != null) ...[
        _buildPassControlButton(
          isDark: isDark,
          icon: Icons.grid_view_rounded,
          tooltip: 'Change grid columns',
          onPressed: onViewToggle,
        ),
        const SizedBox(width: 8),
      ],
      _buildPassControlButton(
        isDark: isDark,
        icon: Icons.settings_outlined,
        tooltip: 'Settings',
        onPressed: () {
          HapticFeedback.lightImpact();
          Navigator.push(context, SmoothPageRoute(page: const SettingsPage()));
        },
      ),
    ];
  }

  Widget _buildIdentityCategorySelector({
    required bool isDark,
    required List<String> categories,
    required String value,
  }) {
    return Container(
      height: 40,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        color: isDark
            ? Colors.white.withValues(alpha: 0.05)
            : Colors.black.withValues(alpha: 0.03),
        borderRadius: BorderRadius.circular(12),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<String>(
          value: value,
          isExpanded: true,
          icon: Icon(
            Icons.keyboard_arrow_down,
            color: isDark ? Colors.white54 : Colors.black54,
          ),
          dropdownColor: isDark ? const Color(0xFF1E1E1E) : Colors.white,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: isDark ? Colors.white : Colors.black87,
          ),
          onChanged: (selected) {
            if (selected == null) return;
            HapticFeedback.selectionClick();
            setState(() => _selectedIdentityFilter = selected);
          },
          items: [
            const DropdownMenuItem(value: 'all', child: Text('All Categories')),
            ...categories.map(
              (category) =>
                  DropdownMenuItem(value: category, child: Text(category)),
            ),
          ],
        ),
      ),
    );
  }

  String _passCategoryLabel(String type) {
    return switch (PassType.fromValue(type).category) {
      PassCategory.retail => 'Retail',
      PassCategory.tickets => 'Tickets & Transit',
      PassCategory.access => 'Access',
      PassCategory.health => 'Health',
      PassCategory.identity => 'Identity',
      PassCategory.generic => 'Generic',
    };
  }

  bool _paymentCategoryMatches({
    required String? network,
    required String category,
  }) {
    final normalizedNetwork = network?.toLowerCase().replaceAll(' ', '') ?? '';
    final normalizedCategory = category.toLowerCase().replaceAll(' ', '');
    final aliases = switch (normalizedCategory) {
      'americanexpress' => {'americanexpress', 'amex'},
      'mastercard' => {'mastercard', 'master'},
      _ => {normalizedCategory},
    };
    return aliases.contains(normalizedNetwork);
  }

  double _passGridCellAspectRatio(BuildContext context, int columns) {
    const horizontalPadding = 32.0;
    const crossAxisSpacing = 12.0;
    const cardAspectRatio = 1.586;
    const labelHeight = 44.0;
    final usableWidth =
        MediaQuery.sizeOf(context).width -
        horizontalPadding -
        ((columns - 1) * crossAxisSpacing);
    final cardWidth = usableWidth / columns;
    final cellHeight =
        (cardWidth / cardAspectRatio) + (columns == 1 ? 0 : labelHeight);
    return cardWidth / cellHeight;
  }

  bool _hasImage(String? imagePath) =>
      imagePath != null && imagePath.isNotEmpty;

  _ExpiryStatus? _expiryStatus(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    final match = RegExp(r'^(\d{2})/(\d{2})$').firstMatch(value.trim());
    if (match == null) return null;
    final month = int.tryParse(match.group(1)!);
    final year = int.tryParse(match.group(2)!);
    if (month == null || year == null || month < 1 || month > 12) return null;

    final now = DateTime.now();
    final currentMonth = now.year * 12 + now.month;
    final expiryMonth = (2000 + year) * 12 + month;
    if (expiryMonth < currentMonth) return _ExpiryStatus.expired;
    final leadMonths = context
        .read<StartupSettingsProvider>()
        .expiryNotificationLeadMonths;
    if (expiryMonth <= currentMonth + leadMonths) {
      return _ExpiryStatus.expiringSoon;
    }
    return null;
  }

  Widget _buildExpiryIndicator({
    required String? expiry,
    required Widget child,
  }) {
    final status = _expiryStatus(expiry);
    if (status == null) return child;

    final isExpired = status == _ExpiryStatus.expired;
    final color = isExpired ? Colors.red.shade700 : Colors.orange.shade800;
    return Stack(
      fit: StackFit.expand,
      children: [
        child,
        Positioned(
          top: 8,
          right: 8,
          child: IgnorePointer(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(8),
                boxShadow: const [
                  BoxShadow(color: Colors.black26, blurRadius: 4),
                ],
              ),
              child: Text(
                isExpired ? 'Expired' : 'Expires soon',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildWalletGridTile({
    required Wallet wallet,
    required PassGridDisplayMode displayMode,
    required VoidCallback onTap,
    VoidCallback? onLongPress,
  }) {
    final imagePath = switch (displayMode) {
      PassGridDisplayMode.front => wallet.frontImagePath,
      PassGridDisplayMode.back => wallet.backImagePath,
      PassGridDisplayMode.virtualCards => null,
    };
    return _buildExpiryIndicator(
      expiry: wallet.expiry,
      child: GestureDetector(
        onTap: onTap,
        onLongPress: onLongPress,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(15),
          child: _hasImage(imagePath)
              ? EncryptedImageDisplay(imagePath: imagePath!, fit: BoxFit.cover)
              : _buildScaledVirtualCard(
                  child: GlassCreditCard(
                    wallet: wallet,
                    isMasked: true,
                    onCardTap: onTap,
                  ),
                ),
        ),
      ),
    );
  }

  Widget _buildIdentityGridTile({
    required IdentityCard card,
    required PassGridDisplayMode displayMode,
    required VoidCallback onTap,
    VoidCallback? onLongPress,
  }) {
    final imagePath = switch (displayMode) {
      PassGridDisplayMode.front => card.frontImagePath,
      PassGridDisplayMode.back => card.backImagePath,
      PassGridDisplayMode.virtualCards => null,
    };
    return _buildExpiryIndicator(
      expiry: card.expiryDate,
      child: GestureDetector(
        onTap: onTap,
        onLongPress: onLongPress,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(20),
          child: _hasImage(imagePath)
              ? EncryptedImageDisplay(imagePath: imagePath!, fit: BoxFit.cover)
              : _buildScaledVirtualCard(
                  child: IdentityCardWidget(card: card, onTap: onTap),
                ),
        ),
      ),
    );
  }

  Widget _buildScaledVirtualCard({required Widget child}) {
    return LayoutBuilder(
      builder: (context, constraints) => FittedBox(
        fit: BoxFit.contain,
        child: SizedBox(width: 320, child: child),
      ),
    );
  }

  Widget _buildPassesTab(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);
    final isDark = themeProvider.isDarkMode;
    final settings = context.watch<StartupSettingsProvider>();

    if (!settings.isPassSearchEnabled && _searchQuery.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && !settings.isPassSearchEnabled) {
          _searchController.clear();
        }
      });
    }

    return Consumer<PassProvider>(
      builder: (context, provider, child) {
        final passes = provider.passes;

        final searchedPasses = settings.isPassSearchEnabled
            ? provider.searchPasses(_searchQuery)
            : passes;
        final passCategories = settings.categoriesFor(WalletSection.passes);
        final activePassFilter = passCategories.contains(_selectedPassFilter)
            ? _selectedPassFilter
            : 'all';
        final filteredPasses = searchedPasses.where((pass) {
          if (activePassFilter == 'all') return true;
          return _passCategoryLabel(pass.type) == activePassFilter;
        }).toList();

        return CustomScrollView(
          physics: const BouncingScrollPhysics(
            parent: AlwaysScrollableScrollPhysics(),
          ),
          slivers: [
            if (_isSearchBarAtTop(settings))
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                  child: _buildPassSearchField(isDark),
                ),
              ),
            if (settings.controlRowPosition == ControlRowPosition.top)
              SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.fromLTRB(
                    16,
                    _isSearchBarAtTop(settings) ? 0 : 16,
                    16,
                    8,
                  ),
                  child: Row(
                    children: [
                      // Improved Category Selector
                      Expanded(
                        child: Container(
                          height: 40,
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          decoration: BoxDecoration(
                            color: isDark
                                ? Colors.white.withValues(alpha: 0.05)
                                : Colors.black.withValues(alpha: 0.03),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: DropdownButtonHideUnderline(
                            child: DropdownButton<String>(
                              value: _selectedPassFilter,
                              isExpanded: true,
                              icon: Icon(
                                Icons.keyboard_arrow_down,
                                color: isDark ? Colors.white54 : Colors.black54,
                              ),
                              dropdownColor: isDark
                                  ? const Color(0xFF1E1E1E)
                                  : Colors.white,
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: isDark ? Colors.white : Colors.black87,
                              ),
                              onChanged: (String? newValue) {
                                if (newValue != null) {
                                  HapticFeedback.selectionClick();
                                  setState(
                                    () => _selectedPassFilter = newValue,
                                  );
                                }
                              },
                              items: [
                                const DropdownMenuItem(
                                  value: 'all',
                                  child: Text('All Categories'),
                                ),
                                ...passCategories.map(
                                  (category) => DropdownMenuItem(
                                    value: category,
                                    child: Text(category),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                      if (settings.isPassSearchEnabled &&
                          settings.passSearchStyle == PassSearchStyle.icon) ...[
                        const SizedBox(width: 8),
                        _buildPassControlButton(
                          isDark: isDark,
                          icon: Icons.search_rounded,
                          tooltip: 'Search passes',
                          onPressed: _showPassSearchDialog,
                        ),
                      ],
                      const SizedBox(width: 8),
                      _buildPassControlButton(
                        isDark: isDark,
                        icon: Icons.grid_view_rounded,
                        tooltip: 'Change grid columns',
                        onPressed: () {
                          HapticFeedback.selectionClick();
                          final columns = settings.gridColumnsFor(
                            WalletSection.passes,
                          );
                          settings.setGridColumns(
                            WalletSection.passes,
                            columns == 3 ? 1 : columns + 1,
                          );
                        },
                      ),
                      const SizedBox(width: 8),
                      _buildPassControlButton(
                        isDark: isDark,
                        icon: Icons.settings_outlined,
                        tooltip: 'Settings',
                        onPressed: () {
                          HapticFeedback.lightImpact();
                          Navigator.push(
                            context,
                            SmoothPageRoute(page: const SettingsPage()),
                          );
                        },
                      ),
                    ],
                  ),
                ),
              ),
            SliverToBoxAdapter(
              child: SizedBox(
                height: settings.controlRowPosition == ControlRowPosition.bottom
                    ? 68
                    : 12,
              ),
            ),

            if (filteredPasses.isEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.all(48.0),
                  child: Center(
                    child: Text(
                      passes.isEmpty
                          ? "No passes added yet.\nTap the '+' to add one."
                          : 'No passes found.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: isDark ? Colors.white54 : Colors.black45,
                      ),
                    ),
                  ),
                ),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                sliver: SliverGrid.builder(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: settings.gridColumnsFor(
                      WalletSection.passes,
                    ),
                    crossAxisSpacing: 12.0,
                    mainAxisSpacing: 12.0,
                    childAspectRatio: _passGridCellAspectRatio(
                      context,
                      settings.gridColumnsFor(WalletSection.passes),
                    ),
                  ),
                  itemCount: filteredPasses.length,
                  itemBuilder: (context, index) {
                    final pass = filteredPasses[index];

                    final gridMode = switch (settings.gridModeFor(
                      WalletSection.passes,
                    )) {
                      PassGridDisplayMode.front => PassDisplayMode.front,
                      PassGridDisplayMode.back => PassDisplayMode.back,
                      PassGridDisplayMode.virtualCards => PassDisplayMode.card,
                    };

                    return _buildExpiryIndicator(
                      expiry: pass.expiryDate,
                      child: PassGridCard(
                        pass: pass,
                        displayMode: gridMode,
                        showLabels:
                            settings.gridColumnsFor(WalletSection.passes) != 1,
                        truncateOrganizationName:
                            settings.gridColumnsFor(WalletSection.passes) != 1,
                        onCardLongPress: () {
                          HapticFeedback.mediumImpact();
                          _showGridContextMenu(context, pass);
                        },
                        onCardTap: () async {
                          HapticFeedback.selectionClick();
                          final passProvider = Provider.of<PassProvider>(
                            context,
                            listen: false,
                          );
                          final result = await Navigator.push(
                            context,
                            SmoothPageRoute(
                              page: BarcodeCardDetailScreen(pass: pass),
                            ),
                          );
                          if (result == true && mounted) {
                            await passProvider.fetchPasses();
                          }
                        },
                      ),
                    );
                  },
                ),
              ),
            const SliverToBoxAdapter(child: SizedBox(height: 100)),
          ],
        );
      },
    );
  }

  void _showGridContextMenu(BuildContext context, Pass pass) {
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);
    final isDark = themeProvider.isDarkMode;

    showModalBottomSheet(
      context: context,
      backgroundColor: isDark ? const Color(0xFF1E1E1E) : Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Wrap(
            children: [
              ListTile(
                leading: const Icon(Icons.reorder_rounded),
                title: const Text('Reorder items'),
                onTap: () {
                  Navigator.pop(ctx);
                  _openReorderMode(WalletSection.passes);
                },
              ),
              ListTile(
                leading: const Icon(Icons.archive_outlined),
                title: const Text('Archive'),
                onTap: () async {
                  Navigator.pop(ctx);
                  await context.read<PassProvider>().archivePass(pass.id!);
                },
              ),
              ListTile(
                leading: const Icon(Icons.edit_outlined, color: Colors.blue),
                title: const Text('Edit'),
                onTap: () async {
                  Navigator.pop(ctx);
                  HapticFeedback.lightImpact();
                  final result = await Navigator.push(
                    context,
                    SmoothPageRoute(page: PassEditScreen(pass: pass)),
                  );
                  if (result == true && context.mounted) {
                    context.read<PassProvider>().fetchPasses();
                  }
                },
              ),
              ListTile(
                leading: const Icon(Icons.copy_rounded, color: Colors.blue),
                title: const Text('Copy'),
                onTap: () {
                  Navigator.pop(ctx);
                  HapticFeedback.mediumImpact();
                  ClipboardService.instance.copy(pass.barcodeValue);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: const Text('Pass data copied!'),
                      behavior: SnackBarBehavior.floating,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      duration: const Duration(seconds: 1),
                    ),
                  );
                },
              ),
              ListTile(
                leading: const Icon(
                  Icons.delete_outline_rounded,
                  color: Colors.red,
                ),
                title: const Text('Delete'),
                onTap: () {
                  Navigator.pop(ctx);
                  _showPassDeleteConfirmationDialog(
                    id: pass.id!,
                    name: pass.organizationName,
                  );
                },
              ),
            ],
          ),
        );
      },
    );
  }

  void _showWalletContextMenu(Wallet wallet) {
    HapticFeedback.mediumImpact();
    showModalBottomSheet<void>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: const Icon(Icons.reorder_rounded),
              title: const Text('Reorder items'),
              onTap: () {
                Navigator.pop(sheetContext);
                _openReorderMode(WalletSection.payments);
              },
            ),
            ListTile(
              leading: const Icon(Icons.archive_outlined),
              title: const Text('Archive'),
              onTap: () async {
                Navigator.pop(sheetContext);
                await context.read<WalletProvider>().archiveWallet(wallet.id!);
              },
            ),
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('Edit'),
              onTap: () async {
                Navigator.pop(sheetContext);
                final fullWallet = await context
                    .read<WalletProvider>()
                    .getWalletDetails(wallet.id!);
                if (fullWallet == null || !mounted) return;
                final result = await Navigator.push(
                  context,
                  SmoothPageRoute(page: WalletEditScreen(wallet: fullWallet)),
                );
                if (result == true && mounted) {
                  await context.read<WalletProvider>().fetchWallets();
                }
              },
            ),
            ListTile(
              leading: const Icon(Icons.copy_outlined),
              title: const Text('Copy number'),
              onTap: () {
                Navigator.pop(sheetContext);
                ClipboardService.instance.copy(wallet.number);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Card number copied')),
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete_outline, color: Colors.red),
              title: const Text('Delete'),
              onTap: () async {
                Navigator.pop(sheetContext);
                final confirmed = await showDialog<bool>(
                  context: context,
                  builder: (dialogContext) => AlertDialog(
                    title: const Text('Delete card?'),
                    content: Text('Delete "${wallet.name}" permanently?'),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(dialogContext, false),
                        child: const Text('Cancel'),
                      ),
                      FilledButton(
                        onPressed: () => Navigator.pop(dialogContext, true),
                        child: const Text('Delete'),
                      ),
                    ],
                  ),
                );
                if (confirmed == true && mounted) {
                  await context.read<WalletProvider>().deleteWallet(wallet.id!);
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  void _showIdentityContextMenu(IdentityCard card) {
    HapticFeedback.mediumImpact();
    showModalBottomSheet<void>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Wrap(
          children: [
            ListTile(
              leading: const Icon(Icons.reorder_rounded),
              title: const Text('Reorder items'),
              onTap: () {
                Navigator.pop(sheetContext);
                _openReorderMode(WalletSection.identity);
              },
            ),
            ListTile(
              leading: const Icon(Icons.archive_outlined),
              title: const Text('Archive'),
              onTap: () async {
                Navigator.pop(sheetContext);
                await context.read<IdentityProvider>().archiveIdentity(
                  card.id!,
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('Edit'),
              onTap: () async {
                Navigator.pop(sheetContext);
                final result = await Navigator.push(
                  context,
                  SmoothPageRoute(page: IdentityEditScreen(card: card)),
                );
                if (result == true && mounted) {
                  await context.read<IdentityProvider>().fetchIdentities();
                }
              },
            ),
            ListTile(
              leading: const Icon(Icons.copy_outlined),
              title: const Text('Copy ID value'),
              onTap: () {
                Navigator.pop(sheetContext);
                ClipboardService.instance.copy(card.value);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('ID value copied')),
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete_outline, color: Colors.red),
              title: const Text('Delete'),
              onTap: () {
                Navigator.pop(sheetContext);
                _showIdentityDeleteConfirmationDialog(
                  id: card.id!,
                  name: card.name,
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openReorderMode(WalletSection section) async {
    await Navigator.push<void>(
      context,
      SmoothPageRoute(page: ReorderItemsScreen(section: section)),
    );
  }

  void _showIdentityDeleteConfirmationDialog({
    required int id,
    required String name,
  }) {
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);
    final isDark = themeProvider.isDarkMode;

    showDialog(
      context: context,
      barrierColor: isDark ? Colors.black54 : Colors.black26,
      builder: (BuildContext ctx) {
        return AlertDialog(
          backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
          title: Text(
            'Delete Identity Card?',
            style: Theme.of(
              context,
            ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
          ),
          content: Text(
            'Are you sure you want to delete "$name"? This action cannot be undone.',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: isDark ? Colors.white70 : Colors.black87,
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: Text(
                'Cancel',
                style: TextStyle(
                  color: isDark ? Colors.white60 : Colors.black54,
                ),
              ),
            ),
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.error,
              ),
              onPressed: () {
                HapticFeedback.mediumImpact();
                context.read<IdentityProvider>().deleteIdentity(id);
                Navigator.of(ctx).pop();
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Identity Card Deleted!')),
                );
              },
              child: const Text('Delete'),
            ),
          ],
        );
      },
    );
  }

  Widget _buildIdentitiesTab(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);
    final isDark = themeProvider.isDarkMode;
    final settings = context.watch<StartupSettingsProvider>();

    if (!settings.isPassSearchEnabled && _searchQuery.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && !settings.isPassSearchEnabled) {
          _searchController.clear();
        }
      });
    }

    return Consumer<IdentityProvider>(
      builder: (context, provider, child) {
        final identities = provider.identities;

        final searchedIdentities = settings.isPassSearchEnabled
            ? provider.searchIdentities(_searchQuery)
            : identities;
        final identityCategories = settings.categoriesFor(
          WalletSection.identity,
        );
        final activeIdentityFilter =
            identityCategories.contains(_selectedIdentityFilter)
            ? _selectedIdentityFilter
            : 'all';
        final filteredIdentities = searchedIdentities.where((identity) {
          return activeIdentityFilter == 'all' ||
              identity.cardType.toLowerCase() ==
                  activeIdentityFilter.toLowerCase();
        }).toList();

        return CustomScrollView(
          physics: const BouncingScrollPhysics(
            parent: AlwaysScrollableScrollPhysics(),
          ),
          slivers: [
            if (_isSearchBarAtTop(settings))
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                  child: _buildSearchField(isDark, 'Search identities...'),
                ),
              ),
            if (settings.controlRowPosition == ControlRowPosition.top)
              SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.fromLTRB(
                    16,
                    _isSearchBarAtTop(settings) ? 0 : 16,
                    16,
                    8,
                  ),
                  child: _buildUnifiedActionsRow(
                    isDark: isDark,
                    settings: settings,
                    searchHint: 'Search identities...',
                    categorySelector: _buildIdentityCategorySelector(
                      isDark: isDark,
                      categories: identityCategories,
                      value: activeIdentityFilter,
                    ),
                    isGridView: true,
                    onViewToggle: () {
                      HapticFeedback.selectionClick();
                      final columns = settings.gridColumnsFor(
                        WalletSection.identity,
                      );
                      settings.setGridColumns(
                        WalletSection.identity,
                        columns == 3 ? 1 : columns + 1,
                      );
                    },
                  ),
                ),
              ),

            SliverToBoxAdapter(
              child: SizedBox(
                height: settings.controlRowPosition == ControlRowPosition.bottom
                    ? 68
                    : 12,
              ),
            ),
            if (filteredIdentities.isEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.all(48.0),
                  child: Center(
                    child: Text(
                      identities.isEmpty
                          ? "No identity cards yet.\nTap the '+' to add one."
                          : 'No identity cards found.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: isDark ? Colors.white54 : Colors.black45,
                      ),
                    ),
                  ),
                ),
              )
            else
              const SliverToBoxAdapter(child: SizedBox(height: 12)),
            if (filteredIdentities.isNotEmpty)
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                sliver: SliverGrid(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: settings.gridColumnsFor(
                      WalletSection.identity,
                    ),
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 1.586,
                  ),
                  delegate: SliverChildBuilderDelegate((context, index) {
                    final card = filteredIdentities[index];
                    return _buildIdentityGridTile(
                      card: card,
                      displayMode: settings.gridModeFor(WalletSection.identity),
                      onLongPress: () => _showIdentityContextMenu(card),
                      onTap: () async {
                        HapticFeedback.selectionClick();
                        final result = await Navigator.push(
                          context,
                          SmoothPageRoute(
                            page: IdentityCardDetailScreen(card: card),
                          ),
                        );
                        if (result == true && context.mounted) {
                          context.read<IdentityProvider>().fetchIdentities();
                        }
                      },
                    );
                  }, childCount: filteredIdentities.length),
                ),
              ),
            const SliverToBoxAdapter(child: SizedBox(height: 100)),
          ],
        );
      },
    );
  }
}
