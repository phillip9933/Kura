import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:kura/services/clipboard_service.dart';
import 'package:provider/provider.dart';
import 'package:kura/screens/homescreen.dart';
import 'package:kura/models/db_helper.dart';
import 'package:kura/models/provider_helper.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/widgets/full_screen_image_viewer.dart';
import 'package:kura/widgets/glass_credit_card.dart';
import 'package:kura/widgets/encrypted_image_display.dart';
import 'package:kura/widgets/credit_card_entry_form.dart';
import 'package:kura/screens/share_secure_screen.dart';

// WalletDetailScreen with liquid glass design
class WalletDetailScreen extends StatefulWidget {
  final Wallet wallet;
  const WalletDetailScreen({super.key, required this.wallet});
  @override
  State<WalletDetailScreen> createState() => _WalletDetailScreenState();
}

class _WalletDetailScreenState extends State<WalletDetailScreen> {
  late Wallet currentWallet;

  @override
  void initState() {
    super.initState();
    currentWallet = widget.wallet;
  }

  Widget _buildImageThumbnail(String imagePath, String label, bool isDark) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: Column(
        children: [
          GestureDetector(
            onTap: () {
              Navigator.push(
                context,
                SmoothPageRoute(
                  page: FullScreenImageViewer(imagePath: imagePath),
                ),
              );
            },
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: isDark
                      ? Colors.white.withValues(alpha: 0.102)
                      : Colors.black.withValues(alpha: 0.078),
                ),
                boxShadow: [
                  BoxShadow(
                    color: isDark
                        ? Colors.black.withValues(alpha: 0.302)
                        : Colors.black.withValues(alpha: 0.078),
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: EncryptedImageDisplay(
                  imagePath: imagePath,
                  height: 100,
                  width: 150,
                  fit: BoxFit.cover,
                  cacheHeight: 200,
                  cacheWidth: 300,
                  errorWidget: Container(
                    height: 100,
                    width: 150,
                    color: isDark
                        ? Colors.white.withValues(alpha: 0.051)
                        : Colors.black.withValues(alpha: 0.031),
                    child: Icon(
                      Icons.error_outline,
                      color: isDark ? Colors.white38 : Colors.black38,
                    ),
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: isDark ? Colors.white60 : Colors.black54,
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final startupProvider = Provider.of<StartupSettingsProvider>(context);
    final isDark = themeProvider.isDarkMode;
    bool isPathValid(String? path) => path != null && path.isNotEmpty;

    return Scaffold(
      appBar: AppBar(
        title: const SizedBox.shrink(),
        leading: Container(
          margin: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: isDark
                ? Colors.white.withValues(alpha: 0.078)
                : Colors.black.withValues(alpha: 0.051),
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
        actions: [
          Container(
            margin: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: isDark
                  ? Colors.white.withValues(alpha: 0.078)
                  : Colors.black.withValues(alpha: 0.051),
              borderRadius: BorderRadius.circular(12),
            ),
            child: IconButton(
              icon: Icon(
                Icons.share_rounded,
                color: isDark ? Colors.white : Colors.black,
              ),
              onPressed: () {
                HapticFeedback.mediumImpact();
                Navigator.push(
                  context,
                  SmoothPageRoute(
                    page: ShareSecureScreen(wallet: currentWallet),
                  ),
                );
              },
            ),
          ),
          Container(
            margin: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: isDark
                  ? Colors.white.withValues(alpha: 0.078)
                  : Colors.black.withValues(alpha: 0.051),
              borderRadius: BorderRadius.circular(12),
            ),
            child: IconButton(
              icon: Icon(
                Icons.edit_outlined,
                color: isDark ? Colors.white : Colors.black,
              ),
              onPressed: () async {
                final walletProvider = context.read<WalletProvider>();
                final result = await Navigator.push<bool>(
                  context,
                  SmoothPageRoute(
                    page: WalletEditScreen(wallet: currentWallet),
                  ),
                );

                if (result == true && mounted) {
                  final updatedWallet = await walletProvider.getWalletDetails(
                    currentWallet.id!,
                  );
                  if (updatedWallet != null && mounted) {
                    setState(() => currentWallet = updatedWallet);
                  } else if (mounted) {
                    Navigator.pop(context, true);
                  }
                }
              },
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          GlassCreditCard(
            isMasked: false,
            wallet: currentWallet,
            onCardTap: () {
              ClipboardService.instance.copy(currentWallet.number);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Card Number Copied!')),
              );
            },
          ),
          const SizedBox(height: 20),
          if (_detailEntries(currentWallet, startupProvider).isNotEmpty)
            _LiquidGlassDetailSection(
              title: "Details",
              icon: Icons.tune_outlined,
              children: _detailEntries(currentWallet, startupProvider).map((
                entry,
              ) {
                return _LiquidGlassDetailTile(title: entry.$1, value: entry.$2);
              }).toList(),
            ),
          if (isPathValid(currentWallet.frontImagePath) ||
              isPathValid(currentWallet.backImagePath))
            _LiquidGlassDetailSection(
              title: "Card Images",
              icon: Icons.photo_library_outlined,
              children: [
                Row(
                  children: [
                    if (isPathValid(currentWallet.frontImagePath))
                      Expanded(
                        child: _buildImageThumbnail(
                          currentWallet.frontImagePath!,
                          'Front',
                          isDark,
                        ),
                      ),
                    if (isPathValid(currentWallet.frontImagePath) &&
                        isPathValid(currentWallet.backImagePath))
                      const SizedBox(width: 12),
                    if (isPathValid(currentWallet.backImagePath))
                      Expanded(
                        child: _buildImageThumbnail(
                          currentWallet.backImagePath!,
                          'Back',
                          isDark,
                        ),
                      ),
                  ],
                ),
              ],
            ),
        ],
      ),
    );
  }

  List<(String, String)> _detailEntries(
    Wallet wallet,
    StartupSettingsProvider settings,
  ) {
    final entries = <(String, String)>[];
    void add(String label, String? value) {
      final text = value?.trim() ?? '';
      if (text.isNotEmpty) entries.add((label, text));
    }

    add('Card Name', wallet.name);
    add('Card Number', wallet.number);
    add('Expiry Date', wallet.expiry);
    add('Issuer', wallet.issuer);
    add('Card Network', wallet.network);
    wallet.customFields?.forEach(add);
    return entries;
  }
}

// --- Wallet edit route ---
class WalletEditScreen extends StatefulWidget {
  const WalletEditScreen({super.key, required this.wallet});

  final Wallet wallet;

  @override
  State<WalletEditScreen> createState() => _WalletEditScreenState();
}

class _WalletEditScreenState extends State<WalletEditScreen> {
  final _formKey = GlobalKey<CreditCardEntryFormState>();

  Future<void> _manageWallet({required bool archive}) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(archive ? 'Archive Card?' : 'Delete Card?'),
        content: Text(
          archive
              ? 'This card will move to Archive. You can restore it later.'
              : 'This permanently deletes "${widget.wallet.name}". This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: archive
                ? null
                : FilledButton.styleFrom(
                    backgroundColor: Theme.of(context).colorScheme.error,
                  ),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(archive ? 'Archive' : 'Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    HapticFeedback.mediumImpact();
    final provider = context.read<WalletProvider>();
    if (archive) {
      await provider.archiveWallet(widget.wallet.id!);
    } else {
      await provider.deleteWallet(widget.wallet.id!);
    }
    if (mounted) Navigator.pop(context, true);
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      appBar: AppBar(
        title: const SizedBox.shrink(),
        leading: Container(
          margin: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: isDark
                ? Colors.white.withValues(alpha: 0.08)
                : Colors.black.withValues(alpha: 0.05),
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
        actions: [
          Container(
            margin: const EdgeInsets.all(8),
            child: FilledButton(
              onPressed: () => _formKey.currentState?.save(),
              style: FilledButton.styleFrom(
                backgroundColor: isDark ? Colors.white : Colors.black,
                foregroundColor: isDark ? Colors.black : Colors.white,
              ),
              child: const Text('SAVE'),
            ),
          ),
        ],
      ),
      body: CreditCardEntryForm(
        key: _formKey,
        existingWallet: widget.wallet,
        footer: _buildManagementActions(),
      ),
    );
  }

  Widget _buildManagementActions() {
    return Row(
      children: [
        Expanded(
          child: OutlinedButton.icon(
            onPressed: () => _manageWallet(archive: true),
            icon: const Icon(Icons.archive_outlined),
            label: const Text('Archive'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: FilledButton.icon(
            onPressed: () => _manageWallet(archive: false),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            icon: const Icon(Icons.delete_outline),
            label: const Text('Delete'),
          ),
        ),
      ],
    );
  }
}

// --- LIQUID GLASS DETAIL SECTION ---
class _LiquidGlassDetailSection extends StatelessWidget {
  final String title;
  final IconData? icon;
  final List<Widget> children;

  const _LiquidGlassDetailSection({
    required this.title,
    this.icon,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final textTheme = Theme.of(context).textTheme;
    final isDark = themeProvider.isDarkMode;
    final textColor = isDark ? Colors.white38 : Colors.black38;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 12, top: 8),
          child: Row(
            children: [
              if (icon != null) ...[
                Icon(icon, size: 16, color: textColor),
                const SizedBox(width: 8),
              ],
              Text(
                title.toUpperCase(),
                style: textTheme.labelSmall?.copyWith(
                  color: textColor,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.2,
                ),
              ),
            ],
          ),
        ),
        Container(
          margin: const EdgeInsets.only(bottom: 24),
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF5F5F5),
            border: Border.all(
              color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFE8E8E8),
              width: 0.5,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: children,
          ),
        ),
      ],
    );
  }
}

// --- LIQUID GLASS DETAIL TILE ---
class _LiquidGlassDetailTile extends StatelessWidget {
  final String title;
  final String value;

  const _LiquidGlassDetailTile({required this.title, required this.value});

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final textTheme = Theme.of(context).textTheme;
    final isDark = themeProvider.isDarkMode;
    final textColor = isDark ? Colors.white : Colors.black;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10.0),
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: textTheme.bodyMedium?.copyWith(
                color: textColor.withValues(alpha: 0.7),
              ),
            ),
          ),
          Text(
            value,
            style: textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.bold,
              color: textColor,
            ),
          ),
          IconButton(
            tooltip: 'Copy $title',
            icon: const Icon(Icons.copy_outlined, size: 18),
            onPressed: () {
              ClipboardService.instance.copy(value);
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(SnackBar(content: Text('$title copied')));
            },
          ),
        ],
      ),
    );
  }
}
