import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:kura/models/db_helper.dart';
import 'package:kura/models/provider_helper.dart';
import 'package:kura/services/image_service.dart';
import 'package:kura/services/auto_backup_service.dart';
import 'package:kura/widgets/identity_card_widget.dart';
import 'package:kura/widgets/color_picker.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/widgets/configured_custom_fields.dart';

class IdentityCardEntryForm extends StatefulWidget {
  final IdentityCard? existingCard;
  const IdentityCardEntryForm({super.key, this.existingCard});

  @override
  State<IdentityCardEntryForm> createState() => IdentityCardEntryFormState();
}

class IdentityCardEntryFormState extends State<IdentityCardEntryForm> {
  final _nameController = TextEditingController();
  final _valueController = TextEditingController();
  final _cardTypeController = TextEditingController();
  final Map<String, TextEditingController> _customFieldControllers = {};
  final Map<String, CustomFieldDataType> _localFieldTypes = {};
  String? _frontImagePath;
  String? _backImagePath;
  String _selectedColor = 'obsidian';
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    if (widget.existingCard != null) {
      _nameController.text = widget.existingCard!.name;
      _valueController.text = widget.existingCard!.value;
      _cardTypeController.text = widget.existingCard!.cardType;
      _frontImagePath = widget.existingCard!.frontImagePath;
      _backImagePath = widget.existingCard!.backImagePath;
      _selectedColor = widget.existingCard!.color ?? 'obsidian';
      for (final entry
          in widget.existingCard!.customFields?.entries ??
              <MapEntry<String, String>>[]) {
        _customFieldControllers[entry.key] = TextEditingController(
          text: entry.value,
        );
      }
    }
    _nameController.addListener(() => setState(() {}));
    _valueController.addListener(() => setState(() {}));
    _cardTypeController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _nameController.dispose();
    _valueController.dispose();
    _cardTypeController.dispose();
    for (final controller in _customFieldControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _pickImage(bool isFront) async {
    try {
      final croppedFile = await pickAndCropCardImage(
        context,
        sideLabel: isFront ? 'Front' : 'Back',
      );
      if (croppedFile == null) return;
      final encryptedPath = await saveImageToAppDirectory(croppedFile);
      if (encryptedPath == null || !mounted) return;
      setState(() {
        if (isFront) {
          _frontImagePath = encryptedPath;
        } else {
          _backImagePath = encryptedPath;
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

  Future<void> _saveData() async {
    final settings = context.read<StartupSettingsProvider>();
    final name = _nameController.text.trim();
    final value = _valueController.text.trim();
    final cardType = _cardTypeController.text.trim();
    final categories = settings.categoriesFor(WalletSection.identity);

    if (name.isEmpty || value.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Name and Value are required.')),
      );
      return;
    }

    setState(() => _isSaving = true);

    try {
      final card = IdentityCard(
        id: widget.existingCard?.id,
        name: name,
        value: value,
        cardType: categories.contains(cardType) ? cardType : categories.first,
        frontImagePath: _frontImagePath,
        backImagePath: _backImagePath,
        color: _selectedColor,
        customFields: {
          for (final entry in _customFieldControllers.entries)
            if (entry.value.text.trim().isNotEmpty)
              entry.key: entry.value.text.trim(),
        },
        orderIndex: widget.existingCard?.orderIndex ?? 0,
      );

      if (widget.existingCard != null) {
        await IdentityDatabaseHelper.instance.updateIdentity(card);
      } else {
        await IdentityDatabaseHelper.instance.insertIdentity(card);
        AutoBackupService.triggerBackup();
      }

      if (mounted) {
        context.read<IdentityProvider>().fetchIdentities();
        Navigator.pop(context, true);
      }
    } catch (_) {
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  void save() => _saveData();

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final settings = context.watch<StartupSettingsProvider>();
    final categories = settings.categoriesFor(WalletSection.identity);
    final selectedCategory = categories.contains(_cardTypeController.text)
        ? _cardTypeController.text
        : categories.first;
    for (final schema in settings.customFieldsFor(WalletSection.identity)) {
      _customFieldControllers.putIfAbsent(
        schema.name,
        TextEditingController.new,
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        // Preview
        IdentityCardWidget(
          card: IdentityCard(
            name: _nameController.text.isEmpty ? 'NAME' : _nameController.text,
            value: _valueController.text.isEmpty
                ? 'ID NUMBER'
                : _valueController.text,
            cardType: _cardTypeController.text.isEmpty
                ? 'IDENTITY CARD'
                : _cardTypeController.text,
            frontImagePath: _frontImagePath,
            backImagePath: _backImagePath,
            color: _selectedColor,
          ),
          onTap: () {},
        ),
        const SizedBox(height: 32),

        ColorPicker(
          selectedColor: _selectedColor,
          onColorSelected: (color) => setState(() => _selectedColor = color),
        ),
        const SizedBox(height: 32),

        ...[
          DropdownButtonFormField<String>(
            initialValue: selectedCategory,
            decoration: const InputDecoration(labelText: 'Card Label'),
            items: categories
                .map(
                  (category) =>
                      DropdownMenuItem(value: category, child: Text(category)),
                )
                .toList(),
            onChanged: (value) =>
                setState(() => _cardTypeController.text = value!),
          ),
          const SizedBox(height: 16),
        ],
        TextField(
          controller: _nameController,
          decoration: InputDecoration(
            labelText: 'Full Name',
            hintText: 'e.g. John Doe',
            prefixIcon: const Icon(Icons.person_outline_rounded),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _valueController,
          decoration: InputDecoration(
            labelText: 'ID Value / Number',
            hintText: 'e.g. 123-456-789',
            prefixIcon: const Icon(Icons.badge_outlined),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 24),
        ConfiguredCustomFields(
          schemas: settings.customFieldsFor(WalletSection.identity),
          controllers: _customFieldControllers,
          localFieldTypes: _localFieldTypes,
        ),
        const SizedBox(height: 32),
        Text(
          'ATTACHMENTS (OPTIONAL)',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.bold,
            color: isDark ? Colors.white54 : Colors.black54,
            letterSpacing: 1.2,
          ),
        ),
        const SizedBox(height: 16),

        Row(
          children: [
            Expanded(
              child: _buildImagePickerTile(
                'Front Side',
                _frontImagePath,
                () => _pickImage(true),
                isDark,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: _buildImagePickerTile(
                'Back Side',
                _backImagePath,
                () => _pickImage(false),
                isDark,
              ),
            ),
          ],
        ),

        const SizedBox(height: 48),

        if (widget.existingCard == null)
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
              onPressed: _isSaving ? null : _saveData,
              child: _isSaving
                  ? CircularProgressIndicator(
                      color: Theme.of(context).colorScheme.onPrimary,
                    )
                  : const Text(
                      'SAVE IDENTITY CARD',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1,
                      ),
                    ),
            ),
          ),
      ],
    );
  }

  Widget _buildImagePickerTile(
    String label,
    String? path,
    VoidCallback onTap,
    bool isDark,
  ) {
    return Column(
      children: [
        GestureDetector(
          onTap: onTap,
          child: Container(
            height: 100,
            width: double.infinity,
            decoration: BoxDecoration(
              color: isDark
                  ? Colors.white.withValues(alpha: 0.05)
                  : Colors.black.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: path != null
                    ? Colors.green.withValues(alpha: 0.5)
                    : (isDark ? Colors.white12 : Colors.black12),
              ),
            ),
            child: path != null
                ? const Icon(
                    Icons.check_circle_rounded,
                    color: Colors.green,
                    size: 32,
                  )
                : Icon(
                    Icons.add_a_photo_outlined,
                    color: isDark ? Colors.white38 : Colors.black38,
                  ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          label,
          style: TextStyle(
            fontSize: 11,
            color: isDark ? Colors.white54 : Colors.black54,
          ),
        ),
      ],
    );
  }
}
