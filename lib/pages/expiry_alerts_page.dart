import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:provider/provider.dart';

class ExpiryAlertsPage extends StatelessWidget {
  const ExpiryAlertsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<StartupSettingsProvider>();
    final isDark = context.watch<ThemeProvider>().isDarkMode;
    final divider = Divider(
      color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFE8E8E8),
      height: 1,
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Expiry Alerts')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.notifications_active_outlined),
                  title: const Text('Startup Expiry Alerts'),
                  subtitle: const Text(
                    'Show expired and upcoming expiry alerts when Kura opens',
                  ),
                  trailing: Switch(
                    value: provider.isExpiryNotificationEnabled,
                    onChanged: provider.setExpiryNotificationEnabled,
                  ),
                ),
                divider,
                ListTile(
                  leading: const Icon(Icons.calendar_month_outlined),
                  title: const Text('Alert Lead Time'),
                  subtitle: Text(
                    'Alert for items expiring within ${provider.expiryNotificationLeadMonths} months',
                  ),
                  trailing: TextButton(
                    onPressed: provider.isExpiryNotificationEnabled
                        ? () => _showLeadTimeDialog(context, provider)
                        : null,
                    child: Text(
                      '${provider.expiryNotificationLeadMonths} months',
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _showLeadTimeDialog(
    BuildContext context,
    StartupSettingsProvider provider,
  ) {
    final isDark = context.read<ThemeProvider>().isDarkMode;
    final controller = TextEditingController(
      text: provider.expiryNotificationLeadMonths.toString(),
    );
    var showValidationError = false;
    showDialog(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          backgroundColor: isDark ? const Color(0xFF0A0A0A) : Colors.white,
          title: const Text(
            'Alert Lead Time',
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Show startup alerts for items expiring within this number of months.',
              ),
              const SizedBox(height: 16),
              TextField(
                controller: controller,
                autofocus: true,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: InputDecoration(
                  labelText: 'Months before expiry',
                  suffixText: 'months',
                  errorText: showValidationError
                      ? 'Enter at least 1 month'
                      : null,
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
                final months = int.tryParse(controller.text.trim());
                if (months == null || months < 1) {
                  setDialogState(() => showValidationError = true);
                  return;
                }
                await provider.setExpiryNotificationLeadMonths(months);
                if (dialogContext.mounted) Navigator.pop(dialogContext);
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }
}
