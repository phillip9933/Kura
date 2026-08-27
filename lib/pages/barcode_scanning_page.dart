import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:provider/provider.dart';

class BarcodeScanningPage extends StatefulWidget {
  const BarcodeScanningPage({super.key});

  @override
  State<BarcodeScanningPage> createState() => _BarcodeScanningPageState();
}

class _BarcodeScanningPageState extends State<BarcodeScanningPage> {
  static const _systemSettingsChannel = MethodChannel(
    'app.kura.wallet/system_settings',
  );

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<StartupSettingsProvider>();
    final isDark = context.watch<ThemeProvider>().isDarkMode;
    final divider = Divider(
      color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFE8E8E8),
      height: 1,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Barcode & Scanning')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.screen_rotation_outlined),
                  title: const Text('Default Barcode Orientation'),
                  subtitle: Text(
                    provider.defaultBarcodeOrientation ==
                            BarcodeOrientation.flipped
                        ? 'Flipped'
                        : 'Default',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => _showBarcodeOrientationDialog(provider),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.brightness_high_outlined),
                  title: const Text('Max Brightness on Barcode View'),
                  subtitle: const Text(
                    'Temporarily maximize brightness for fullscreen barcodes',
                  ),
                  trailing: Switch(
                    value: provider.maxBrightnessOnBarcodeView,
                    onChanged: (enabled) =>
                        _setBarcodeBrightnessEnabled(provider, enabled),
                  ),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.qr_code_scanner_rounded),
                  title: const Text('QR Import Scanner'),
                  subtitle: const Text(
                    'Show the scanner button in section controls',
                  ),
                  trailing: Switch(
                    value: provider.isQrImportScannerEnabled,
                    onChanged: provider.setQrImportScannerEnabled,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _setBarcodeBrightnessEnabled(
    StartupSettingsProvider provider,
    bool enabled,
  ) async {
    await provider.setMaxBrightnessOnBarcodeView(enabled);
    if (!enabled || !Platform.isAndroid || !mounted) return;
    try {
      await _systemSettingsChannel.invokeMethod<void>(
        'requestWriteSettingsPermission',
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Allow “Modify system settings” to enable barcode brightness.',
            ),
          ),
        );
      }
    } catch (_) {
      // The setting remains saved; the barcode screen will still work without
      // brightness enhancement if the platform cannot open this page.
    }
  }

  void _showBarcodeOrientationDialog(StartupSettingsProvider provider) {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Default Barcode Orientation'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            RadioListTile<BarcodeOrientation>(
              title: const Text('Default'),
              value: BarcodeOrientation.defaultOrientation,
              groupValue: provider.defaultBarcodeOrientation,
              onChanged: (value) {
                if (value == null) return;
                provider.setDefaultBarcodeOrientation(value);
                Navigator.pop(dialogContext);
              },
            ),
            RadioListTile<BarcodeOrientation>(
              title: const Text('Flipped'),
              value: BarcodeOrientation.flipped,
              groupValue: provider.defaultBarcodeOrientation,
              onChanged: (value) {
                if (value == null) return;
                provider.setDefaultBarcodeOrientation(value);
                Navigator.pop(dialogContext);
              },
            ),
          ],
        ),
      ),
    );
  }
}
