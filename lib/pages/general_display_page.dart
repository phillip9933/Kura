import 'package:flutter/material.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:provider/provider.dart';

class GeneralDisplayPage extends StatelessWidget {
  const GeneralDisplayPage({super.key});

  @override
  Widget build(BuildContext context) {
    final themeProvider = context.watch<ThemeProvider>();
    final startupProvider = context.watch<StartupSettingsProvider>();
    final isDark = themeProvider.isDarkMode;
    final divider = Divider(
      color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFE8E8E8),
      height: 1,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('General Display')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.brightness_6_outlined),
                  title: const Text('App Theme'),
                  subtitle: Text(
                    _getThemeDisplayName(themeProvider.themePreference),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => _showThemeDialog(context, themeProvider),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.payments_outlined),
                  title: const Text('Default Currency'),
                  subtitle: Text(
                    '${startupProvider.selectedCurrencyCode} (${startupProvider.selectedCurrencySymbol})',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => _showCurrencyDialog(context, startupProvider),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.home_outlined),
                  title: const Text('Default Tab on Launch'),
                  subtitle: Text(
                    _getDefaultScreenName(startupProvider.defaultScreenIndex),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () =>
                      _showDefaultScreenDialog(context, startupProvider),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  String _getThemeDisplayName(ThemePreference preference) {
    return switch (preference) {
      ThemePreference.light => 'Light',
      ThemePreference.dark => 'Dark',
      ThemePreference.system => 'Follow System',
    };
  }

  String _getDefaultScreenName(int index) {
    return switch (index) {
      0 => 'Payments',
      1 => 'Passes',
      2 => 'Identity',
      _ => 'Payments',
    };
  }

  void _showCurrencyDialog(
    BuildContext context,
    StartupSettingsProvider provider,
  ) {
    final isDark = context.read<ThemeProvider>().isDarkMode;
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
        title: const Text(
          'Choose Currency',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        content: SizedBox(
          width: double.maxFinite,
          child: ListView.builder(
            shrinkWrap: true,
            itemCount: StartupSettingsProvider.majorCurrencies.length,
            itemBuilder: (context, index) {
              final currency = StartupSettingsProvider.majorCurrencies[index];
              return RadioListTile<String>(
                title: Text('${currency['name']} (${currency['symbol']})'),
                value: currency['code']!,
                groupValue: provider.selectedCurrencyCode,
                onChanged: (value) {
                  if (value == null) return;
                  provider.setCurrency(value, currency['symbol']!);
                  Navigator.pop(dialogContext);
                },
              );
            },
          ),
        ),
      ),
    );
  }

  void _showDefaultScreenDialog(
    BuildContext context,
    StartupSettingsProvider provider,
  ) {
    final isDark = context.read<ThemeProvider>().isDarkMode;
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
        title: const Text(
          'Default Screen',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (provider.showPaymentsTab)
              _buildRadioOption('Payments', 0, provider.defaultScreenIndex, (
                value,
              ) {
                provider.setDefaultScreen(value);
                Navigator.pop(dialogContext);
              }),
            if (provider.showPassesTab)
              _buildRadioOption('Passes', 1, provider.defaultScreenIndex, (
                value,
              ) {
                provider.setDefaultScreen(value);
                Navigator.pop(dialogContext);
              }),
            if (provider.showIdentityTab)
              _buildRadioOption('Identity', 2, provider.defaultScreenIndex, (
                value,
              ) {
                provider.setDefaultScreen(value);
                Navigator.pop(dialogContext);
              }),
          ],
        ),
      ),
    );
  }

  Widget _buildRadioOption(
    String label,
    int value,
    int groupValue,
    ValueChanged<int> onChanged,
  ) {
    return RadioListTile<int>(
      title: Text(label),
      value: value,
      groupValue: groupValue,
      onChanged: (selected) {
        if (selected != null) onChanged(selected);
      },
    );
  }

  void _showThemeDialog(BuildContext context, ThemeProvider themeProvider) {
    final isDark = themeProvider.isDarkMode;
    showDialog(
      context: context,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
        title: const Text(
          'Choose Theme',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: ThemePreference.values
              .map(
                (preference) => RadioListTile<ThemePreference>(
                  title: Text(_getThemeDisplayName(preference)),
                  value: preference,
                  groupValue: themeProvider.themePreference,
                  onChanged: (value) {
                    if (value == null) return;
                    themeProvider.setThemePreference(value);
                    Navigator.pop(dialogContext);
                  },
                ),
              )
              .toList(),
        ),
      ),
    );
  }
}
