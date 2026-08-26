import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:kura/models/db_helper.dart';
import 'package:kura/models/provider_helper.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:kura/widgets/barcode_card_entry_form.dart';
import 'package:kura/screens/homescreen.dart';
import 'package:kura/widgets/display_barcode_screen.dart';
import 'package:kura/widgets/encrypted_image_display.dart';
import 'package:kura/widgets/full_screen_image_viewer.dart';
import 'package:kura/widgets/barcode_card.dart';
import 'share_secure_screen.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/services/clipboard_service.dart';

class BarcodeCardDetailScreen extends StatefulWidget {
  final Pass pass;

  const BarcodeCardDetailScreen({super.key, required this.pass});

  @override
  State<BarcodeCardDetailScreen> createState() =>
      _BarcodeCardDetailScreenState();
}

class _BarcodeCardDetailScreenState extends State<BarcodeCardDetailScreen> {
  bool _isPathValid(String? path) => path != null && path.isNotEmpty;

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
    final isDark = themeProvider.isDarkMode;
    final p = widget.pass;

    return Scaffold(
      appBar: AppBar(
        title: const SizedBox.shrink(),
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
        actions: [
          Container(
            margin: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF0F0F0),
              borderRadius: BorderRadius.circular(12),
            ),
            child: IconButton(
              icon: Icon(
                Icons.share_rounded,
                color: isDark ? Colors.white : Colors.black,
                size: 20,
              ),
              tooltip: 'Share Pass (Encrypted Data)',
              onPressed: () {
                HapticFeedback.mediumImpact();
                Navigator.push(
                  context,
                  SmoothPageRoute(page: ShareSecureScreen(pass: widget.pass)),
                );
              },
            ),
          ),
          Container(
            margin: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF0F0F0),
              borderRadius: BorderRadius.circular(12),
            ),
            child: IconButton(
              icon: Icon(
                Icons.edit,
                color: isDark ? Colors.white : Colors.black,
                size: 20,
              ),
              onPressed: () {
                HapticFeedback.lightImpact();
                _navigateToEditScreen(context);
              },
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          BarcodeCard(
            pass: p,
            minimal: true,
            onCardTap: () {
              if (p.barcodeValue.isNotEmpty) {
                HapticFeedback.mediumImpact();
                Navigator.push(
                  context,
                  SmoothPageRoute(
                    page: DisplayBarcodeScreen(
                      barcodeData: p.barcodeValue,
                      barcodeFormat: p.barcodeFormat,
                      cardName: p.organizationName,
                    ),
                  ),
                );
              }
            },
          ),
          const SizedBox(height: 24),

          _buildDetailsSection(p, isDark),

          if (_hasPassImages(p)) _buildPassImagesSection(p, isDark),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildDetailsSection(Pass pass, bool isDark) {
    final details = _detailEntries(
      pass,
      context.read<StartupSettingsProvider>(),
    );
    if (details.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: _LiquidGlassSection(
        title: 'Details',
        icon: Icons.description_outlined,
        isDark: isDark,
        child: Column(
          children: details
              .map((entry) => _buildDetailRow(entry.label, entry.value, isDark))
              .toList(),
        ),
      ),
    );
  }

  List<({String label, String value})> _detailEntries(
    Pass pass,
    StartupSettingsProvider settings,
  ) {
    final entries = <({String label, String value})>[];
    void add(String label, String? value) {
      final text = value?.trim() ?? '';
      if (text.isNotEmpty) entries.add((label: label, value: text));
    }

    add('Pass Type', pass.type);
    add('Organization Name', pass.organizationName);
    add('Description', pass.description);
    add('Logo Text', pass.logoText);
    add('Barcode Value', pass.barcodeValue);
    add('Transit Type', pass.transitType);
    add('Expiry Date', pass.expiryDate);
    final customFields = pass.fields?['customFields'];
    if (customFields is Map) {
      customFields.forEach(
        (key, value) => add(key.toString(), value?.toString()),
      );
    }
    for (final key in const [
      'primaryFields',
      'secondaryFields',
      'auxiliaryFields',
      'headerFields',
      'backFields',
    ]) {
      final fields = pass.fields?[key];
      if (fields is! List) continue;
      for (final field in fields) {
        if (field is! Map) continue;
        final label = field['label']?.toString().trim() ?? '';
        final value = field['value']?.toString().trim() ?? '';
        if (label.isNotEmpty && value.isNotEmpty) {
          entries.add((label: label, value: value));
        }
      }
    }
    return entries;
  }

  bool _hasPassImages(Pass pass) {
    return _isPathValid(pass.frontImagePath) ||
        _isPathValid(pass.backImagePath) ||
        _isPathValid(pass.stripImagePath) ||
        _isPathValid(pass.thumbnailImagePath);
  }

  Widget _buildPassImagesSection(Pass pass, bool isDark) {
    return _LiquidGlassSection(
      title: 'Pass Images',
      icon: Icons.photo_library_outlined,
      isDark: isDark,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (_isPathValid(pass.frontImagePath))
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: _buildImageThumbnail(
                  pass.frontImagePath!,
                  'Front',
                  isDark,
                ),
              ),
            if (_isPathValid(pass.backImagePath))
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: _buildImageThumbnail(
                  pass.backImagePath!,
                  'Back',
                  isDark,
                ),
              ),
            if (_isPathValid(pass.stripImagePath))
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: _buildImageThumbnail(
                  pass.stripImagePath!,
                  'Strip',
                  isDark,
                ),
              ),
            if (_isPathValid(pass.thumbnailImagePath))
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: _buildImageThumbnail(
                  pass.thumbnailImagePath!,
                  'Thumbnail',
                  isDark,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildDetailRow(String label, String value, bool isDark) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: TextStyle(
              color: isDark ? Colors.white54 : Colors.black54,
              fontSize: 13,
              fontWeight: FontWeight.w500,
            ),
          ),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: TextStyle(
                color: isDark ? Colors.white : Colors.black,
                fontSize: 14,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          IconButton(
            tooltip: 'Copy $label',
            icon: const Icon(Icons.copy_outlined, size: 18),
            onPressed: () {
              ClipboardService.instance.copy(value);
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(SnackBar(content: Text('$label copied')));
            },
          ),
        ],
      ),
    );
  }

  void _navigateToEditScreen(BuildContext context) async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => PassEditScreen(pass: widget.pass),
      ),
    );
    if (result == true && context.mounted) Navigator.pop(context, true);
  }
}

class PassEditScreen extends StatefulWidget {
  final Pass pass;
  const PassEditScreen({super.key, required this.pass});
  @override
  State<PassEditScreen> createState() => PassEditScreenState();
}

class PassEditScreenState extends State<PassEditScreen> {
  final _formKey = GlobalKey<BarcodeCardEntryFormState>();
  bool _isDark = false;

  Future<void> _managePass({required bool archive}) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(archive ? 'Archive Pass?' : 'Delete Pass?'),
        content: Text(
          archive
              ? 'This pass will move to Archive. You can restore it later.'
              : 'This permanently deletes "${widget.pass.organizationName}". This action cannot be undone.',
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
    final provider = context.read<PassProvider>();
    if (archive) {
      await provider.archivePass(widget.pass.id!);
    } else {
      await provider.deletePass(widget.pass.id!);
    }
    if (mounted) Navigator.pop(context, true);
  }

  @override
  Widget build(BuildContext context) {
    _isDark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      appBar: AppBar(
        title: const SizedBox.shrink(),
        leading: Container(
          margin: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: _isDark
                ? Colors.white.withValues(alpha: 0.08)
                : Colors.black.withValues(alpha: 0.05),
            borderRadius: BorderRadius.circular(12),
          ),
          child: IconButton(
            icon: Icon(
              Icons.arrow_back_ios_new_rounded,
              color: _isDark ? Colors.white : Colors.black,
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
                backgroundColor: _isDark ? Colors.white : Colors.black,
                foregroundColor: _isDark ? Colors.black : Colors.white,
              ),
              child: const Text("SAVE"),
            ),
          ),
        ],
      ),
      body: BarcodeCardEntryForm(
        key: _formKey,
        existingPass: widget.pass,
        footer: _buildManagementActions(),
      ),
    );
  }

  Widget _buildManagementActions() {
    return Row(
      children: [
        Expanded(
          child: OutlinedButton.icon(
            onPressed: () => _managePass(archive: true),
            icon: const Icon(Icons.archive_outlined),
            label: const Text('Archive'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: FilledButton.icon(
            onPressed: () => _managePass(archive: false),
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

class _LiquidGlassSection extends StatelessWidget {
  final String title;
  final IconData icon;
  final Widget child;
  final bool isDark;
  const _LiquidGlassSection({
    required this.title,
    required this.icon,
    required this.child,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 8),
          child: Row(
            children: [
              Icon(
                icon,
                size: 14,
                color: isDark ? Colors.white38 : Colors.black38,
              ),
              const SizedBox(width: 8),
              Text(
                title.toUpperCase(),
                style: TextStyle(
                  color: isDark ? Colors.white38 : Colors.black38,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.2,
                  fontSize: 11,
                ),
              ),
            ],
          ),
        ),
        Container(
          padding: const EdgeInsets.all(16),
          width: double.infinity,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF5F5F5),
          ),
          child: child,
        ),
      ],
    );
  }
}
