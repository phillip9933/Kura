// lib/widgets/display_barcode_screen.dart - MODERN 2024 DESIGN

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:kura/services/clipboard_service.dart';
import 'package:provider/provider.dart';
import 'package:barcode_widget/barcode_widget.dart';
import 'package:screen_brightness/screen_brightness.dart';
import '../models/theme_provider.dart';
import '../models/startup_settings_provider.dart';
import '../services/barcode_utils.dart';

class DisplayBarcodeScreen extends StatefulWidget {
  final String barcodeData;
  final String? barcodeFormat;
  final String cardName;

  const DisplayBarcodeScreen({
    super.key,
    required this.barcodeData,
    this.barcodeFormat,
    required this.cardName,
  });

  @override
  State<DisplayBarcodeScreen> createState() => _DisplayBarcodeScreenState();
}

class _DisplayBarcodeScreenState extends State<DisplayBarcodeScreen> {
  late final List<_BarcodeFormat> _formats;
  int _selectedIndex = 0;
  bool _isFlipped = false;
  double? _previousBrightness;
  bool _brightnessWasChanged = false;

  @override
  void initState() {
    super.initState();

    // Build list of formats from BarcodeUtils
    _formats = BarcodeUtils.supportedFormats.entries
        .map(
          (e) => _BarcodeFormat(
            e.value,
            e.key,
            BarcodeUtils.getIconForFormat(e.key),
          ),
        )
        .toList();

    // Set initial selection based on pass format
    if (widget.barcodeFormat != null) {
      final label = BarcodeUtils.getLabelFromFormat(widget.barcodeFormat);
      final index = _formats.indexWhere((f) => f.name == label);
      if (index != -1) {
        _selectedIndex = index;
      }
    }

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final settings = context.read<StartupSettingsProvider>();
      setState(() {
        _isFlipped =
            settings.defaultBarcodeOrientation == BarcodeOrientation.flipped;
      });
      if (settings.maxBrightnessOnBarcodeView) _enableMaximumBrightness();
    });
  }

  Future<void> _enableMaximumBrightness() async {
    try {
      if (!await ScreenBrightness.instance.canChangeSystemBrightness) return;
      _previousBrightness = await ScreenBrightness.instance.system;
      await ScreenBrightness.instance.setSystemScreenBrightness(1.0);
      _brightnessWasChanged = true;
    } catch (_) {
      // Brightness is an enhancement; barcode viewing must remain usable when
      // the platform does not expose application brightness controls.
    }
  }

  Future<void> _restoreBrightness() async {
    if (!_brightnessWasChanged) return;
    _brightnessWasChanged = false;
    try {
      if (_previousBrightness != null) {
        await ScreenBrightness.instance.setSystemScreenBrightness(
          _previousBrightness!,
        );
      }
    } catch (_) {}
  }

  Future<void> _close() async {
    await _restoreBrightness();
    if (mounted) Navigator.pop(context);
  }

  @override
  void dispose() {
    _restoreBrightness();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);
    final isDark = themeProvider.isDarkMode;
    final bgColor = isDark ? const Color(0xFF0A0A0A) : Colors.white;
    final textColor = isDark ? Colors.white : Colors.black;

    return Scaffold(
      backgroundColor: bgColor,
      floatingActionButton: Padding(
        padding: const EdgeInsets.only(bottom: 84),
        child: FloatingActionButton.extended(
          heroTag: 'rotate_barcode',
          onPressed: () {
            HapticFeedback.selectionClick();
            setState(() => _isFlipped = !_isFlipped);
          },
          icon: const Icon(Icons.rotate_90_degrees_ccw_rounded),
          label: const Text('Rotate'),
        ),
      ),
      body: PopScope(
        onPopInvokedWithResult: (didPop, _) {
          if (didPop) _restoreBrightness();
        },
        child: SafeArea(
          child: Column(
            children: [
              // Header
              _buildHeader(context, isDark, textColor),

              // Barcode Display
              Expanded(
                child: Center(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 24),
                    child: AnimatedRotation(
                      turns: _isFlipped ? 0.5 : 0,
                      duration: const Duration(milliseconds: 220),
                      child: _buildBarcodeCard(isDark),
                    ),
                  ),
                ),
              ),

              // Format Selector
              _buildFormatSelector(isDark, textColor),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, bool isDark, Color textColor) {
    return Padding(
      padding: const EdgeInsets.all(20),
      child: Row(
        children: [
          // Close button
          GestureDetector(
            onTap: _close,
            child: Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: isDark
                    ? Colors.white.withValues(alpha: 0.059)
                    : Colors.black.withValues(alpha: 0.031),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(Icons.close_rounded, color: textColor, size: 22),
            ),
          ),
          const SizedBox(width: 16),
          // Title
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.cardName,
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: textColor,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  "Show this to cashier",
                  style: TextStyle(
                    fontSize: 13,
                    color: isDark ? Colors.white54 : Colors.black45,
                  ),
                ),
              ],
            ),
          ),
          // Copy button
          GestureDetector(
            onTap: () {
              ClipboardService.instance.copy(widget.barcodeData);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  behavior: SnackBarBehavior.floating,
                  margin: const EdgeInsets.all(16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  content: const Text('Copied to clipboard'),
                  duration: const Duration(seconds: 2),
                ),
              );
            },
            child: Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: isDark
                    ? Colors.white.withValues(alpha: 0.059)
                    : Colors.black.withValues(alpha: 0.031),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(Icons.copy_rounded, color: textColor, size: 20),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBarcodeCard(bool isDark) {
    final format = _formats[_selectedIndex];
    // Check if it's a 2D/Square-ish barcode
    final is2D =
        format.name == 'QR Code' ||
        format.name == 'Aztec' ||
        format.name == 'Data Matrix' ||
        format.name == 'PDF417';

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(32),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        boxShadow: isDark
            ? null
            : [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.039),
                  blurRadius: 30,
                  offset: const Offset(0, 10),
                ),
              ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Barcode
          Container(
            constraints: BoxConstraints(
              maxWidth: is2D ? 200 : double.infinity,
              minHeight: is2D ? 200 : 80,
            ),
            child: BarcodeWidget(
              barcode: format.barcode,
              data: widget.barcodeData,
              color: Colors.black,
              height: is2D ? 200 : 80,
              errorBuilder: (context, error) => _buildError(),
            ),
          ),

          const SizedBox(height: 28),

          // Separator
          Container(height: 1, color: Colors.grey.shade200),

          const SizedBox(height: 20),

          // Number
          SelectableText(
            widget.barcodeData,
            style: const TextStyle(
              fontSize: 18,
              letterSpacing: 2,
              color: Colors.black87,
              fontWeight: FontWeight.w500,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildError() {
    return Container(
      height: 100,
      alignment: Alignment.center,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.error_outline_rounded,
            size: 40,
            color: Colors.red.shade400,
          ),
          const SizedBox(height: 12),
          Text(
            'Cannot display in this format',
            style: TextStyle(color: Colors.red.shade400, fontSize: 13),
          ),
        ],
      ),
    );
  }

  Widget _buildFormatSelector(bool isDark, Color textColor) {
    return Container(
      height: 80,
      margin: const EdgeInsets.fromLTRB(20, 0, 20, 20),
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: _formats.length,
        separatorBuilder: (context, index) => const SizedBox(width: 12),
        itemBuilder: (context, index) {
          final format = _formats[index];
          final isSelected = index == _selectedIndex;

          return GestureDetector(
            onTap: () {
              HapticFeedback.lightImpact();
              setState(() => _selectedIndex = index);
            },
            child: Container(
              width: 80,
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: isSelected
                    ? (isDark ? Colors.white : Colors.black)
                    : (isDark
                          ? Colors.white.withValues(alpha: 0.047)
                          : Colors.black.withValues(alpha: 0.031)),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    format.icon,
                    size: 20,
                    color: isSelected
                        ? (isDark ? Colors.black : Colors.white)
                        : (isDark ? Colors.white54 : Colors.black45),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    format.name,
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: isSelected
                          ? FontWeight.w700
                          : FontWeight.w500,
                      color: isSelected
                          ? (isDark ? Colors.black : Colors.white)
                          : (isDark ? Colors.white54 : Colors.black45),
                    ),
                    textAlign: TextAlign.center,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _BarcodeFormat {
  final Barcode barcode;
  final String name;
  final IconData icon;

  _BarcodeFormat(this.barcode, this.name, this.icon);
}
