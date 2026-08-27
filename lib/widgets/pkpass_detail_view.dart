import 'package:barcode_widget/barcode_widget.dart';
import 'package:flutter/material.dart';
import 'package:kura/models/pass.dart';
import 'package:kura/services/barcode_utils.dart';
import 'package:kura/widgets/encrypted_image_display.dart';

class PkpassDetailView extends StatelessWidget {
  const PkpassDetailView({
    super.key,
    required this.pass,
    required this.onBarcodeTap,
  });

  final Pass pass;
  final VoidCallback onBarcodeTap;

  @override
  Widget build(BuildContext context) {
    final background = _color(
      pass.backgroundColor,
      Theme.of(context).colorScheme.primary,
    );
    final foreground = _color(pass.foregroundColor, Colors.white);
    final label = _color(pass.labelColor, foreground.withValues(alpha: 0.72));
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Container(
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: background,
            borderRadius: BorderRadius.circular(18),
            boxShadow: const [
              BoxShadow(
                color: Colors.black26,
                blurRadius: 16,
                offset: Offset(0, 8),
              ),
            ],
          ),
          child: Stack(
            children: [
              if (_hasImage(pass.frontImagePath))
                Positioned.fill(
                  child: Opacity(
                    opacity: 0.12,
                    child: EncryptedImageDisplay(
                      imagePath: pass.frontImagePath!,
                      fit: BoxFit.cover,
                    ),
                  ),
                ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Padding(
                    padding: const EdgeInsets.all(18),
                    child: Row(
                      children: [
                        _brand(foreground),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            pass.logoText ?? pass.organizationName,
                            style: TextStyle(
                              color: foreground,
                              fontSize: 17,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                        if (_fields('headerFields').isNotEmpty)
                          SizedBox(
                            width: 92,
                            child: _field(
                              _fields('headerFields').first,
                              label,
                              foreground,
                            ),
                          ),
                      ],
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(18, 16, 18, 20),
                    child: pass.type == 'boardingPass'
                        ? _boardingPassFields(label, foreground)
                        : _standardPassFields(label, foreground),
                  ),
                  if (_hasImage(pass.stripImagePath))
                    EncryptedImageDisplay(
                      imagePath: pass.stripImagePath!,
                      height: 150,
                      fit: BoxFit.cover,
                    ),
                  if (pass.barcodeValue.isNotEmpty)
                    Material(
                      color: Colors.white,
                      child: InkWell(
                        onTap: onBarcodeTap,
                        child: Padding(
                          padding: const EdgeInsets.all(18),
                          child: Column(
                            children: [
                              SizedBox(
                                height: 76,
                                child: BarcodeWidget(
                                  barcode: BarcodeUtils.getBarcodeFromFormat(
                                    pass.barcodeFormat,
                                  ),
                                  data: pass.barcodeValue,
                                  color: Colors.black,
                                  errorBuilder: (_, _) =>
                                      const Icon(Icons.error_outline),
                                ),
                              ),
                              if ((pass.barcodeAltText ?? '').isNotEmpty)
                                Padding(
                                  padding: const EdgeInsets.only(top: 8),
                                  child: Text(
                                    pass.barcodeAltText!,
                                    style: const TextStyle(
                                      color: Colors.black,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  if (_hasImage(pass.footerImagePath))
                    EncryptedImageDisplay(
                      imagePath: pass.footerImagePath!,
                      height: 56,
                      fit: BoxFit.contain,
                    ),
                ],
              ),
            ],
          ),
        ),
        if (_fields('backFields').isNotEmpty) ...[
          const SizedBox(height: 24),
          Text('PASS DETAILS', style: Theme.of(context).textTheme.labelMedium),
          const SizedBox(height: 8),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: _fields('backFields')
                    .map(
                      (field) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(_value(field)),
                        subtitle: _label(field).isEmpty
                            ? null
                            : Text(_label(field)),
                      ),
                    )
                    .toList(),
              ),
            ),
          ),
        ],
      ],
    );
  }

  Widget _brand(Color foreground) {
    if (_hasImage(pass.logoImagePath)) {
      return SizedBox(
        width: 76,
        height: 36,
        child: EncryptedImageDisplay(
          imagePath: pass.logoImagePath!,
          fit: BoxFit.contain,
        ),
      );
    }
    if (_hasImage(pass.iconImagePath)) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: EncryptedImageDisplay(
          imagePath: pass.iconImagePath!,
          width: 36,
          height: 36,
          fit: BoxFit.cover,
        ),
      );
    }
    return Icon(Icons.confirmation_number_outlined, color: foreground);
  }

  Widget _standardPassFields(Color label, Color foreground) {
    final primary = _fields('primaryFields');
    final hasThumbnail = _hasImage(pass.thumbnailImagePath);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: primary
                    .take(hasThumbnail ? 2 : 3)
                    .map(
                      (field) => Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: _field(field, label, foreground, primary: true),
                      ),
                    )
                    .toList(),
              ),
            ),
            if (hasThumbnail) ...[
              const SizedBox(width: 16),
              ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: EncryptedImageDisplay(
                  imagePath: pass.thumbnailImagePath!,
                  width: 92,
                  height: 92,
                  fit: BoxFit.cover,
                ),
              ),
            ],
          ],
        ),
        _fieldRow('secondaryFields', label, foreground),
        _fieldRow('auxiliaryFields', label, foreground),
      ],
    );
  }

  Widget _fieldRow(String section, Color label, Color foreground) {
    final fields = _fields(section);
    if (fields.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 18),
      child: Wrap(
        spacing: 16,
        runSpacing: 12,
        children: fields
            .map(
              (field) =>
                  SizedBox(width: 132, child: _field(field, label, foreground)),
            )
            .toList(),
      ),
    );
  }

  Widget _boardingPassFields(Color label, Color foreground) {
    final primary = _fields('primaryFields');
    final origin = primary.isNotEmpty
        ? primary.first
        : const <String, dynamic>{};
    final destination = primary.length > 1
        ? primary[1]
        : const <String, dynamic>{};
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_flightField.isNotEmpty || pass.relevantDate != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Text(
              [_flightField, pass.relevantDate]
                  .whereType<String>()
                  .where((value) => value.isNotEmpty)
                  .join('  •  '),
              style: TextStyle(
                color: label,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        Row(
          children: [
            Expanded(child: _field(origin, label, foreground, primary: true)),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: Icon(Icons.arrow_forward_rounded, color: foreground),
            ),
            Expanded(
              child: _field(destination, label, foreground, primary: true),
            ),
          ],
        ),
        if (pass.transitType != null && pass.transitType!.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              _transitTypeLabel(pass.transitType!),
              style: TextStyle(
                color: label,
                fontSize: 11,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        _fieldRow('secondaryFields', label, foreground),
        _fieldRow('auxiliaryFields', label, foreground),
      ],
    );
  }

  Widget _field(
    Map<String, dynamic> field,
    Color label,
    Color foreground, {
    bool primary = false,
  }) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    mainAxisSize: MainAxisSize.min,
    children: [
      if (_label(field).isNotEmpty)
        Text(
          _label(field).toUpperCase(),
          style: TextStyle(
            color: label,
            fontSize: 10,
            fontWeight: FontWeight.w700,
          ),
        ),
      Text(
        _value(field),
        maxLines: primary ? 2 : 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          color: foreground,
          fontSize: primary ? 24 : 15,
          fontWeight: primary ? FontWeight.w700 : FontWeight.w600,
        ),
      ),
    ],
  );

  List<Map<String, dynamic>> _fields(String section) {
    final values = pass.fields?[section];
    if (values is! List) return const [];
    return values
        .whereType<Map>()
        .map((field) => Map<String, dynamic>.from(field))
        .toList();
  }

  String _label(Map<String, dynamic> field) => field['label']?.toString() ?? '';
  String _value(Map<String, dynamic> field) => field['value']?.toString() ?? '';
  String _transitTypeLabel(String transitType) => transitType
      .replaceFirst('PKTransitType', '')
      .replaceAllMapped(
        RegExp(r'([a-z])([A-Z])'),
        (match) => '${match.group(1)} ${match.group(2)}',
      );
  String get _flightField {
    for (final section in [
      'headerFields',
      'secondaryFields',
      'auxiliaryFields',
    ]) {
      for (final field in _fields(section)) {
        final label = _label(field).toLowerCase();
        if (label.contains('flight')) return _value(field);
      }
    }
    return '';
  }

  bool _hasImage(String? path) => path != null && path.isNotEmpty;
  Color _color(String? value, Color fallback) {
    if (value == null) return fallback;
    final rgb = RegExp(
      r'^rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)$',
    ).firstMatch(value);
    if (rgb != null) {
      return Color.fromARGB(
        255,
        int.parse(rgb.group(1)!),
        int.parse(rgb.group(2)!),
        int.parse(rgb.group(3)!),
      );
    }
    final hex = value.replaceAll('#', '');
    if (hex.length == 6) {
      try {
        return Color(int.parse('FF$hex', radix: 16));
      } on FormatException {
        return fallback;
      }
    }
    return fallback;
  }
}
