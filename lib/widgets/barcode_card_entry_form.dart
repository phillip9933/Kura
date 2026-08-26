import 'dart:io';
import 'package:barcode_scan2/barcode_scan2.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:image_cropper/image_cropper.dart';
import 'package:kura/models/db_helper.dart';
import 'package:kura/services/barcode_decoder_service.dart';
import 'package:kura/services/barcode_utils.dart';
import 'package:kura/services/image_service.dart';
import 'package:kura/services/auto_backup_service.dart';
import 'package:kura/widgets/barcode_card.dart';
import 'package:kura/widgets/color_picker.dart';
import 'package:kura/models/pass_types.dart';
import 'package:provider/provider.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/widgets/configured_custom_fields.dart';
import 'package:kura/widgets/full_screen_image_viewer.dart';

class BarcodeCardEntryForm extends StatefulWidget {
  final Pass? existingPass;
  final String? initialSharedImagePath;

  const BarcodeCardEntryForm({
    super.key,
    this.existingPass,
    this.initialSharedImagePath,
  });

  @override
  State<BarcodeCardEntryForm> createState() => BarcodeCardEntryFormState();
}

class BarcodeCardEntryFormState extends State<BarcodeCardEntryForm> {
  final _organizationController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _logoTextController = TextEditingController();
  final _barcodeValueController = TextEditingController();
  final _accountNumberController = TextEditingController();

  bool _isSaving = false;

  String _selectedType = 'generic';
  String _selectedColor = 'obsidian';
  String _selectedBarcodeFormat = 'QR Code';
  String? _transitType;
  String? _frontImagePath;
  String? _backImagePath;
  String? _iconImagePath;

  final Map<String, List<Map<String, dynamic>>> _dynamicFields = {
    'primaryFields': [],
    'secondaryFields': [],
    'auxiliaryFields': [],
    'headerFields': [],
    'backFields': [],
  };
  final Map<String, TextEditingController> _customFieldControllers = {};
  final Map<String, CustomFieldDataType> _localFieldTypes = {};

  @override
  void initState() {
    super.initState();

    if (widget.existingPass != null) {
      final p = widget.existingPass!;
      _organizationController.text = p.organizationName;
      _descriptionController.text = p.description ?? '';
      _logoTextController.text = p.logoText ?? '';
      _barcodeValueController.text = p.barcodeValue;
      _selectedType = p.type;
      _transitType = p.transitType;
      _selectedBarcodeFormat = BarcodeUtils.getLabelFromFormat(p.barcodeFormat);
      _frontImagePath = p.frontImagePath;
      _backImagePath = p.backImagePath;
      _iconImagePath = p.iconImagePath;

      // Deep copy fields if they exist
      if (p.fields != null) {
        p.fields!.forEach((key, value) {
          if (value is List && _dynamicFields.containsKey(key)) {
            _dynamicFields[key] = List<Map<String, dynamic>>.from(
              value.map((v) => Map<String, dynamic>.from(v as Map)),
            );
          }
        });
        final customFields = p.fields!['customFields'];
        if (customFields is Map) {
          customFields.forEach((key, value) {
            _customFieldControllers[key.toString()] = TextEditingController(
              text: value?.toString() ?? '',
            );
          });
          final accountController = _customFieldControllers.remove('Account #');
          _accountNumberController.text = accountController?.text ?? '';
          accountController?.dispose();
        }
      }

      // Load color from existing pass background color
      if (p.backgroundColor != null && p.backgroundColor!.isNotEmpty) {
        _selectedColor = p.backgroundColor!;
      }
    }

    _organizationController.addListener(() => setState(() {}));
    _barcodeValueController.addListener(() => setState(() {}));

    if (widget.initialSharedImagePath != null &&
        widget.initialSharedImagePath!.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _scanFromImagePath(widget.initialSharedImagePath!);
      });
    }
  }

  @override
  void dispose() {
    _organizationController.dispose();
    _descriptionController.dispose();
    _logoTextController.dispose();
    _barcodeValueController.dispose();
    _accountNumberController.dispose();
    for (final controller in _customFieldControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  void _addData() async {
    final org = _organizationController.text.trim();
    final value = _barcodeValueController.text.trim();
    if (org.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Organization is required.')),
      );
      return;
    }
    if (value.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Barcode value is required.')),
      );
      return;
    }

    setState(() => _isSaving = true);
    try {
      // Preserve every legacy field section from existing passes. Those fields
      // are no longer rendered after the configurable form migration, but an
      // upgrade or edit must never discard previously saved information.
      final fields = Map<String, dynamic>.from(
        widget.existingPass?.fields ?? {},
      );
      fields['customFields'] = {
        ...Map<String, dynamic>.from(
          fields['customFields'] is Map ? fields['customFields'] as Map : {},
        ),
        for (final entry in _customFieldControllers.entries)
          if (entry.value.text.trim().isNotEmpty)
            entry.key: entry.value.text.trim(),
        if (_accountNumberController.text.trim().isNotEmpty)
          'Account #': _accountNumberController.text.trim(),
      };
      final pass = Pass(
        id: widget.existingPass?.id,
        type: _selectedType,
        organizationName: org,
        description: _descriptionController.text.trim(),
        logoText: _logoTextController.text.trim(),
        barcodeValue: value,
        barcodeFormat: BarcodeUtils.getInternalFormatName(
          _selectedBarcodeFormat,
        ),
        transitType: _transitType,
        frontImagePath: _frontImagePath,
        backImagePath: _backImagePath,
        iconImagePath: _iconImagePath,
        stripImagePath: widget.existingPass?.stripImagePath,
        thumbnailImagePath: widget.existingPass?.thumbnailImagePath,
        fields: fields,
        backgroundColor: _selectedColor,
      );

      if (widget.existingPass != null) {
        await PassDatabaseHelper.instance.updatePass(pass);
      } else {
        await PassDatabaseHelper.instance.insertPass(pass);
        AutoBackupService.triggerBackup();
      }

      if (mounted) Navigator.pop(context, true);
    } catch (_) {
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  void save() => _addData();

  Future<void> _scan() async {
    try {
      final result = await BarcodeScanner.scan();
      if (result.type == ResultType.Barcode) {
        final format = BarcodeUtils.getLabelFromScannerFormat(result.format);
        setState(() {
          _barcodeValueController.text = result.rawContent;
          if (format != null) _selectedBarcodeFormat = format;
        });
        if (format == null && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Barcode scanned, but its type could not be determined. Select a barcode format manually.',
              ),
            ),
          );
        }
      }
    } catch (_) {}
  }

  Future<void> _scanFromImagePath(String filePath) async {
    try {
      final scanResult = await BarcodeDecoderService.scanImageFile(
        File(filePath),
      );
      if (scanResult != null && scanResult.text.isNotEmpty) {
        final format = scanResult.format;
        final isKnownFormat =
            format != null && BarcodeUtils.supportedFormats.containsKey(format);
        final detectedFormat = isKnownFormat ? format : null;
        setState(() {
          _barcodeValueController.text = scanResult.text;
          if (detectedFormat != null) _selectedBarcodeFormat = detectedFormat;
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                isKnownFormat
                    ? 'Scanned $format: ${scanResult.text}'
                    : 'Barcode scanned, but its type could not be determined. Select a barcode format manually.',
              ),
            ),
          );
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'No barcode or QR code detected in the selected image.',
              ),
            ),
          );
        }
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Error reading image file.')),
        );
      }
    }
  }

  Future<void> _scanFromGallery() async {
    try {
      final picker = ImagePicker();
      final pickedFile = await picker.pickImage(source: ImageSource.gallery);
      if (pickedFile == null) return;
      await _scanFromImagePath(pickedFile.path);
    } catch (_) {}
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

  Future<void> _pickIconImage() async {
    final picker = ImagePicker();
    final pickedFile = await picker.pickImage(source: ImageSource.gallery);
    if (pickedFile == null) return;

    final croppedFile = await ImageCropper().cropImage(
      sourcePath: pickedFile.path,
      aspectRatio: const CropAspectRatio(ratioX: 1, ratioY: 1),
      compressFormat: ImageCompressFormat.png,
      compressQuality: 90,
      uiSettings: [
        AndroidUiSettings(
          toolbarTitle: 'Crop Pass Icon',
          lockAspectRatio: true,
          cropStyle: CropStyle.circle,
          aspectRatioPresets: const [CropAspectRatioPreset.square],
        ),
        IOSUiSettings(
          title: 'Crop Pass Icon',
          aspectRatioLockEnabled: true,
          aspectRatioPickerButtonHidden: true,
          aspectRatioPresets: const [CropAspectRatioPreset.square],
        ),
      ],
    );
    if (croppedFile == null) return;
    final encryptedPath = await saveImageToAppDirectory(File(croppedFile.path));
    if (encryptedPath != null && mounted) {
      setState(() => _iconImagePath = encryptedPath);
    }
  }

  void _viewIconImage() {
    if (_iconImagePath == null) return;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => FullScreenImageViewer(imagePath: _iconImagePath!),
      ),
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
            height: 80,
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
                    size: 28,
                  )
                : Icon(
                    Icons.add_a_photo_outlined,
                    size: 24,
                    color: isDark ? Colors.white38 : Colors.black38,
                  ),
          ),
        ),
        const SizedBox(height: 6),
        Text(
          label,
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w500,
            color: isDark ? Colors.white54 : Colors.black54,
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return _buildManualEntryView();
  }

  Widget _buildManualEntryView() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final settings = context.watch<StartupSettingsProvider>();
    final categories = settings.categoriesFor(WalletSection.passes);
    final selectedCategory =
        categories.contains(_passCategoryForType(_selectedType))
        ? _passCategoryForType(_selectedType)
        : categories.first;
    for (final schema in settings.customFieldsFor(WalletSection.passes)) {
      _customFieldControllers.putIfAbsent(
        schema.name,
        TextEditingController.new,
      );
    }
    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        BarcodeCard(
          pass: Pass(
            type: _selectedType,
            organizationName: _organizationController.text.isEmpty
                ? 'ORGANIZATION'
                : _organizationController.text,
            description: _descriptionController.text.isEmpty
                ? widget.existingPass?.description
                : _descriptionController.text,
            logoText: _logoTextController.text.isEmpty
                ? widget.existingPass?.logoText
                : _logoTextController.text,
            barcodeValue: _barcodeValueController.text.isEmpty
                ? '123456789'
                : _barcodeValueController.text,
            barcodeFormat: BarcodeUtils.getInternalFormatName(
              _selectedBarcodeFormat,
            ),
            transitType: _transitType,
            fields: _dynamicFields,
            frontImagePath: _frontImagePath,
            backImagePath: _backImagePath,
            iconImagePath: _iconImagePath,
            stripImagePath: widget.existingPass?.stripImagePath,
            thumbnailImagePath: widget.existingPass?.thumbnailImagePath,
            backgroundColor: _selectedColor,
            foregroundColor: widget.existingPass?.foregroundColor,
            labelColor: widget.existingPass?.labelColor,
          ),
          minimal: true,
          onIconTap: _pickIconImage,
          onIconView: _viewIconImage,
          onIconRemove: () => setState(() => _iconImagePath = null),
          onCardTap: () {},
        ),
        const SizedBox(height: 24),
        ColorPicker(
          selectedColor: _selectedColor,
          onColorSelected: (color) => setState(() => _selectedColor = color),
        ),
        const SizedBox(height: 24),
        TextFormField(
          controller: _organizationController,
          decoration: const InputDecoration(labelText: 'Name (Organization)'),
        ),
        const SizedBox(height: 16),
        TextFormField(
          controller: _barcodeValueController,
          decoration: InputDecoration(
            labelText: 'Barcode Value',
            suffixIcon: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  icon: const Icon(Icons.image_outlined),
                  tooltip: 'Import from Gallery',
                  onPressed: _scanFromGallery,
                ),
                IconButton(
                  icon: const Icon(Icons.camera_alt_rounded),
                  tooltip: 'Scan Barcode',
                  onPressed: _scan,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<String>(
          initialValue: _selectedBarcodeFormat,
          decoration: const InputDecoration(labelText: 'Barcode Format'),
          items: BarcodeUtils.supportedFormats.keys
              .map((f) => DropdownMenuItem(value: f, child: Text(f)))
              .toList(),
          onChanged: (v) => setState(() => _selectedBarcodeFormat = v!),
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<String>(
          initialValue: selectedCategory,
          decoration: const InputDecoration(labelText: 'Pass Category'),
          items: categories
              .map(
                (category) =>
                    DropdownMenuItem(value: category, child: Text(category)),
              )
              .toList(),
          onChanged: (v) {
            if (v != null && v != selectedCategory) {
              setState(() {
                _selectedType = _defaultTypeForCategory(v);
              });
            }
          },
        ),
        const SizedBox(height: 24),
        TextFormField(
          controller: _accountNumberController,
          decoration: const InputDecoration(labelText: 'Account #'),
        ),
        const SizedBox(height: 24),
        ConfiguredCustomFields(
          schemas: settings.customFieldsFor(WalletSection.passes),
          controllers: _customFieldControllers,
          localFieldTypes: _localFieldTypes,
        ),

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
        const SizedBox(height: 32),
        if (widget.existingPass == null)
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
                      'SAVE PASS',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1,
                      ),
                    ),
            ),
          ),
        const SizedBox(height: 24),
      ],
    );
  }

  String _passCategoryForType(String type) =>
      switch (PassType.fromValue(type).category) {
        PassCategory.retail => 'Retail',
        PassCategory.tickets => 'Tickets & Transit',
        PassCategory.access => 'Access',
        PassCategory.health => 'Health',
        PassCategory.identity => 'Identity',
        PassCategory.generic => 'Generic',
      };

  String _defaultTypeForCategory(String category) => switch (category) {
    'Retail' => 'loyaltyCard',
    'Tickets & Transit' => 'eventTicket',
    'Access' => 'campusId',
    'Health' => 'healthInsuranceCard',
    'Identity' => 'digitalCredential',
    _ => 'generic',
  };
}
