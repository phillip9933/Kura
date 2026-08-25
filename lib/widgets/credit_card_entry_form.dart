import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:kura/models/db_helper.dart';
import 'package:kura/models/provider_helper.dart';
import 'package:kura/models/theme_provider.dart';
import 'package:kura/services/card_utils.dart';
import 'package:kura/services/image_service.dart';
import 'package:kura/services/auto_backup_service.dart';
import 'package:kura/widgets/color_picker.dart';
import 'package:kura/widgets/form_section.dart';
import 'package:kura/widgets/glass_credit_card.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/widgets/configured_custom_fields.dart';
import 'package:kura/widgets/encrypted_image_display.dart';

class CreditCardEntryForm extends StatefulWidget {
  const CreditCardEntryForm({super.key, this.existingWallet});

  final Wallet? existingWallet;

  @override
  State<CreditCardEntryForm> createState() => CreditCardEntryFormState();
}

class CreditCardEntryFormState extends State<CreditCardEntryForm> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _numberController = TextEditingController();
  final _expiryController = TextEditingController();
  final _issuerController = TextEditingController();
  String _network = "visa";
  String _selectedColor = 'default';
  File? _frontImageFile;
  File? _backImageFile;
  String? _existingFrontImagePath;
  String? _existingBackImagePath;
  bool _isSaving = false;

  final Map<String, TextEditingController> _customFieldControllers = {};
  final Map<String, CustomFieldDataType> _localFieldTypes = {};

  @override
  void initState() {
    super.initState();
    final wallet = widget.existingWallet;
    if (wallet != null) {
      _nameController.text = wallet.name;
      _numberController.text = wallet.number;
      _expiryController.text = _ExpiryDateFormatter.normalize(wallet.expiry);
      _issuerController.text = wallet.issuer ?? '';
      _network = wallet.network ?? _network;
      _selectedColor = wallet.color ?? _selectedColor;
      if (wallet.frontImagePath?.isNotEmpty ?? false) {
        _existingFrontImagePath = wallet.frontImagePath;
      }
      if (wallet.backImagePath?.isNotEmpty ?? false) {
        _existingBackImagePath = wallet.backImagePath;
      }
      for (final entry
          in wallet.customFields?.entries ?? <MapEntry<String, String>>[]) {
        _customFieldControllers[entry.key] = TextEditingController(
          text: entry.value,
        );
      }
    }
    _nameController.addListener(_onFieldChanged);
    _numberController.addListener(_onNumberChanged);
    _expiryController.addListener(_onFieldChanged);
  }

  void _onFieldChanged() {
    if (mounted) setState(() {});
  }

  void _onNumberChanged() {
    final detected = CardUtils.detectCardNetwork(_numberController.text);
    final configuredCategory = _configuredPaymentCategory(detected);
    if (configuredCategory != null && configuredCategory != _network) {
      setState(() => _network = configuredCategory);
    } else if (mounted) {
      setState(() {});
    }
  }

  String? _configuredPaymentCategory(String? detectedNetwork) {
    if (detectedNetwork == null) return null;
    final categories = context.read<StartupSettingsProvider>().categoriesFor(
      WalletSection.payments,
    );
    final normalizedDetected = detectedNetwork.toLowerCase().replaceAll(
      ' ',
      '',
    );
    for (final category in categories) {
      final normalizedCategory = category.toLowerCase().replaceAll(' ', '');
      if (normalizedCategory == normalizedDetected ||
          (normalizedDetected == 'amex' &&
              normalizedCategory == 'americanexpress')) {
        return category;
      }
    }
    return null;
  }

  /// Get maximum card number length
  int _getMaxCardLength(String network) {
    return 19;
  }

  @override
  void dispose() {
    _nameController.removeListener(_onFieldChanged);
    _numberController.removeListener(_onNumberChanged);
    _expiryController.removeListener(_onFieldChanged);
    _nameController.dispose();
    _numberController.dispose();
    _expiryController.dispose();
    _issuerController.dispose();
    for (final controller in _customFieldControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _pickImage(bool isFront) async {
    try {
      final pickedFile = await pickAndCropCardImage(
        context,
        sideLabel: isFront ? 'Front' : 'Back',
      );
      if (pickedFile == null) return;
      setState(() {
        if (isFront) {
          _frontImageFile = pickedFile;
        } else {
          _backImageFile = pickedFile;
        }
      });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Unable to process image. Please try again.'),
          ),
        );
      }
    }
  }

  void _addData() async {
    if (_formKey.currentState!.validate()) {
      setState(() => _isSaving = true);
      try {
        String? frontImagePath = _existingFrontImagePath;
        if (_frontImageFile != null) {
          frontImagePath = await saveImageToAppDirectory(_frontImageFile!);
        }
        String? backImagePath = _existingBackImagePath;
        if (_backImageFile != null) {
          backImagePath = await saveImageToAppDirectory(_backImageFile!);
        }

        final customFields = <String, String>{
          for (final entry in _customFieldControllers.entries)
            if (entry.value.text.trim().isNotEmpty)
              entry.key: entry.value.text.trim(),
        };

        Wallet wallet = Wallet(
          id: widget.existingWallet?.id,
          name: _nameController.text,
          number: _numberController.text,
          expiry: _expiryController.text,
          network: _network,
          issuer: _issuerController.text,
          customFields: customFields.isNotEmpty ? customFields : null,
          color: _selectedColor,
          frontImagePath: frontImagePath,
          backImagePath: backImagePath,
        );
        if (widget.existingWallet == null) {
          await DatabaseHelper.instance.insertWallet(wallet);
        } else {
          await DatabaseHelper.instance.updateWallet(wallet);
          if (mounted) {
            await context.read<WalletProvider>().fetchWallets();
          }
        }
        AutoBackupService.triggerBackup();

        if (mounted) {
          Navigator.pop(context, true);
        }
      } finally {
        if (mounted) setState(() => _isSaving = false);
      }
    }
  }

  void save() => _addData();

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<StartupSettingsProvider>();
    final categories = settings.categoriesFor(WalletSection.payments);
    if (!categories.contains(_network)) _network = categories.first;
    for (final schema in settings.customFieldsFor(WalletSection.payments)) {
      _customFieldControllers.putIfAbsent(
        schema.name,
        TextEditingController.new,
      );
    }
    final previewWallet = Wallet(
      name: _nameController.text.isEmpty ? "CARD NAME" : _nameController.text,
      number: _numberController.text.padRight(16, '•'),
      expiry: _expiryController.text.padRight(4, '•'),
      network: _network,
      color: _selectedColor,
    );

    return Form(
      key: _formKey,
      child: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          GlassCreditCard(
            isMasked: false,
            wallet: previewWallet,
            onCardTap: () {},
          ),
          const SizedBox(height: 24),
          FormSection(
            children: [
              ColorPicker(
                selectedColor: _selectedColor,
                onColorSelected: (color) =>
                    setState(() => _selectedColor = color),
              ),
              const SizedBox(height: 24),
              TextFormField(
                controller: _nameController,
                decoration: const InputDecoration(labelText: 'Card Name'),
                validator: (v) => v!.isEmpty ? 'Please enter a name' : null,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _numberController,
                decoration: InputDecoration(
                  labelText: 'Card Number',
                  suffixIcon: Consumer<ThemeProvider>(
                    builder: (context, themeProvider, _) {
                      final isDark = themeProvider.isDarkMode;
                      final detectedNetwork = CardUtils.detectCardNetwork(
                        _numberController.text,
                      );
                      if (detectedNetwork == null ||
                          _numberController.text.isEmpty) {
                        return const SizedBox.shrink();
                      }
                      return Padding(
                        padding: const EdgeInsets.all(12.0),
                        child: Text(
                          detectedNetwork.toUpperCase(),
                          style: TextStyle(
                            color: isDark
                                ? Colors.white.withValues(alpha: 0.702)
                                : Colors.black.withValues(alpha: 0.702),
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        ),
                      );
                    },
                  ),
                ),
                keyboardType: TextInputType.number,
                inputFormatters: [
                  FilteringTextInputFormatter.digitsOnly,
                  LengthLimitingTextInputFormatter(_getMaxCardLength(_network)),
                ],
                validator: (v) {
                  if (v == null || v.isEmpty) {
                    return 'Please enter a card number';
                  }
                  final cleaned = v.replaceAll(RegExp(r'\D'), '');
                  if (cleaned.length < 15 || cleaned.length > 19) {
                    return 'Card number must be 15-19 digits';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _expiryController,
                decoration: const InputDecoration(labelText: 'Expiry (MM/YY)'),
                keyboardType: TextInputType.number,
                inputFormatters: [_ExpiryDateFormatter()],
                validator: (value) {
                  final expiry = value?.trim() ?? '';
                  if (!RegExp(r'^\d{2}/\d{2}$').hasMatch(expiry)) {
                    return 'Use MM/YY format';
                  }
                  final month = int.parse(expiry.substring(0, 2));
                  return month >= 1 && month <= 12
                      ? null
                      : 'Enter a valid month';
                },
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                initialValue: _network,
                decoration: const InputDecoration(labelText: 'Card Network'),
                items: categories.map((String value) {
                  return DropdownMenuItem<String>(
                    value: value,
                    child: Text(value),
                  );
                }).toList(),
                onChanged: (newValue) => setState(() => _network = newValue!),
              ),
            ],
          ),
          FormSection(
            children: [
              Text(
                'ATTACHMENTS (OPTIONAL)',
                style: Theme.of(context).textTheme.labelLarge,
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: _buildImagePickerTile(
                      label: 'Front Side',
                      imageFile: _frontImageFile,
                      encryptedImagePath: _existingFrontImagePath,
                      onTap: () => _pickImage(true),
                      onRemove: () => setState(() {
                        _frontImageFile = null;
                        _existingFrontImagePath = null;
                      }),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: _buildImagePickerTile(
                      label: 'Back Side',
                      imageFile: _backImageFile,
                      encryptedImagePath: _existingBackImagePath,
                      onTap: () => _pickImage(false),
                      onRemove: () => setState(() {
                        _backImageFile = null;
                        _existingBackImagePath = null;
                      }),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              ConfiguredCustomFields(
                schemas: settings.customFieldsFor(WalletSection.payments),
                controllers: _customFieldControllers,
                localFieldTypes: _localFieldTypes,
              ),
            ],
          ),
          if (widget.existingWallet == null) ...[
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              height: 56,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Theme.of(context).colorScheme.primary,
                  foregroundColor: Theme.of(context).colorScheme.onPrimary,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  elevation: 0,
                ),
                onPressed: _isSaving ? null : _addData,
                child: _isSaving
                    ? CircularProgressIndicator(
                        color: Theme.of(context).colorScheme.onPrimary,
                      )
                    : const Text(
                        'SAVE CARD',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1,
                        ),
                      ),
              ),
            ),
          ],
          const SizedBox(height: 16),
        ],
      ),
    );
  }

  Widget _buildImagePickerTile({
    required String label,
    required File? imageFile,
    required String? encryptedImagePath,
    required VoidCallback onTap,
    required VoidCallback onRemove,
  }) {
    final hasImage = imageFile != null || encryptedImagePath != null;
    return Column(
      children: [
        GestureDetector(
          onTap: onTap,
          child: Stack(
            children: [
              Container(
                height: 100,
                width: double.infinity,
                clipBehavior: Clip.antiAlias,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: hasImage
                        ? Colors.green.withValues(alpha: 0.5)
                        : Theme.of(context).dividerColor,
                  ),
                ),
                child: imageFile != null
                    ? Image.file(imageFile, fit: BoxFit.cover)
                    : encryptedImagePath != null
                    ? EncryptedImageDisplay(
                        imagePath: encryptedImagePath,
                        fit: BoxFit.cover,
                        errorWidget: const Icon(Icons.broken_image_outlined),
                      )
                    : const Icon(Icons.add_a_photo_outlined),
              ),
              if (hasImage)
                Positioned(
                  top: 2,
                  right: 2,
                  child: Material(
                    color: Colors.black54,
                    shape: const CircleBorder(),
                    child: IconButton(
                      tooltip: 'Remove $label',
                      icon: const Icon(
                        Icons.close,
                        color: Colors.white,
                        size: 18,
                      ),
                      onPressed: onRemove,
                    ),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        Text(label, style: Theme.of(context).textTheme.labelSmall),
      ],
    );
  }
}

class _ExpiryDateFormatter extends TextInputFormatter {
  static String normalize(String value) {
    final digits = value.replaceAll(RegExp(r'\D'), '');
    if (digits.length <= 2) return digits;
    return '${digits.substring(0, 2)}/${digits.substring(2, digits.length.clamp(2, 4))}';
  }

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    final digits = newValue.text
        .replaceAll(RegExp(r'\D'), '')
        .substring(
          0,
          newValue.text.replaceAll(RegExp(r'\D'), '').length.clamp(0, 4),
        );
    final formatted = normalize(digits);
    return TextEditingValue(
      text: formatted,
      selection: TextSelection.collapsed(offset: formatted.length),
    );
  }
}
