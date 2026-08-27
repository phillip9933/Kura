import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:kura/widgets/credit_card_entry_form.dart';
import 'package:kura/widgets/barcode_card_entry_form.dart';
import 'package:kura/widgets/identity_card_entry_form.dart';

class AddCardScreen extends StatefulWidget {
  final int initialTabIndex;
  final String? initialSharedImagePath;
  final String? initialBarcodeValue;
  final String? initialBarcodeFormat;

  const AddCardScreen({
    super.key,
    this.initialTabIndex = 0,
    this.initialSharedImagePath,
    this.initialBarcodeValue,
    this.initialBarcodeFormat,
  });

  @override
  State<AddCardScreen> createState() => _AddCardScreenState();
}

class _AddCardScreenState extends State<AddCardScreen> {
  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);
    final isDark = themeProvider.isDarkMode;
    final textColor = isDark ? Colors.white : Colors.black;

    final int effectiveIndex = widget.initialTabIndex;

    Widget form;

    switch (effectiveIndex) {
      case 1:
        form = BarcodeCardEntryForm(
          initialSharedImagePath: widget.initialSharedImagePath,
          initialBarcodeValue: widget.initialBarcodeValue,
          initialBarcodeFormat: widget.initialBarcodeFormat,
        );
        break;
      case 2:
        form = IdentityCardEntryForm();
        break;
      case 0:
      default:
        form = CreditCardEntryForm();
        break;
    }

    return Scaffold(
      appBar: AppBar(
        leading: Container(
          margin: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF1A1A1A) : const Color(0xFFF0F0F0),
            borderRadius: BorderRadius.circular(12),
          ),
          child: IconButton(
            icon: Icon(
              Icons.arrow_back_ios_new_rounded,
              color: textColor,
              size: 20,
            ),
            onPressed: () => Navigator.pop(context),
          ),
        ),
      ),
      body: form,
    );
  }
}
