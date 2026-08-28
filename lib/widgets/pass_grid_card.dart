import 'package:flutter/material.dart';
import 'package:barcode_widget/barcode_widget.dart';
import 'package:kura/models/pass.dart';
import 'package:kura/services/barcode_utils.dart';
import 'package:kura/widgets/encrypted_image_display.dart';

enum PassDisplayMode { card, front, back }

class PassGridCard extends StatelessWidget {
  final Pass pass;
  final VoidCallback onCardTap;
  final VoidCallback onCardLongPress;
  final PassDisplayMode displayMode;
  final bool showLabels;
  final bool truncateOrganizationName;

  const PassGridCard({
    super.key,
    required this.pass,
    required this.onCardTap,
    required this.onCardLongPress,
    this.displayMode = PassDisplayMode.front,
    this.showLabels = true,
    this.truncateOrganizationName = false,
  });

  static bool usesCompactStripLayout({
    required double availableWidth,
    required String passType,
    required bool hasStrip,
  }) {
    return availableWidth < 145 && passType != 'boardingPass' && hasStrip;
  }

  Color? _parseColor(String? hexString) {
    if (hexString == null || hexString.isEmpty) return null;
    final rgb = RegExp(
      r'^rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)$',
    ).firstMatch(hexString);
    if (rgb != null) {
      return Color.fromARGB(
        255,
        int.parse(rgb.group(1)!),
        int.parse(rgb.group(2)!),
        int.parse(rgb.group(3)!),
      );
    }
    final buffer = StringBuffer();
    if (hexString.length == 6 || hexString.length == 7) buffer.write('ff');
    buffer.write(hexString.replaceFirst('#', ''));
    try {
      return Color(int.parse(buffer.toString(), radix: 16));
    } catch (_) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GestureDetector(
      onTap: onCardTap,
      onLongPress: onCardLongPress,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          // ISO/IEC 7810 ID-1 standard card ratio (1.586 : 1)
          AspectRatio(
            aspectRatio: 1.586,
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                color: isDark ? const Color(0xFF1E1E1E) : Colors.grey.shade200,
                border: Border.all(
                  color: isDark
                      ? Colors.white.withValues(alpha: 0.12)
                      : Colors.black.withValues(alpha: 0.08),
                  width: 1,
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: isDark ? 0.3 : 0.06),
                    blurRadius: 8,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              clipBehavior: Clip.antiAlias,
              child: _buildCardContent(context, isDark),
            ),
          ),
          if (showLabels) ...[
            const SizedBox(height: 6),
            Text(
              pass.organizationName.isNotEmpty
                  ? pass.organizationName
                  : (pass.logoText ?? 'Pass'),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: isDark ? Colors.white : Colors.black87,
              ),
            ),
            if (pass.description != null && pass.description!.isNotEmpty)
              Text(
                pass.description!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 11,
                  color: isDark ? Colors.white54 : Colors.black45,
                ),
              ),
          ],
        ],
      ),
    );
  }

  Widget _buildCardContent(BuildContext context, bool isDark) {
    if (pass.sourceType == 'pkpass') {
      return _buildPkpassCard(context, isDark);
    }

    // Mode 1: Front Image (with fallback to digital card)
    if (displayMode == PassDisplayMode.front &&
        pass.frontImagePath != null &&
        pass.frontImagePath!.isNotEmpty) {
      return EncryptedImageDisplay(
        imagePath: pass.frontImagePath!,
        fit: BoxFit.cover,
      );
    }

    // Styled Digital "Fake Card" View
    final customBgColor = _parseColor(pass.backgroundColor);
    final customFgColor =
        _parseColor(pass.foregroundColor) ??
        (isDark ? Colors.white : Colors.black87);

    return LayoutBuilder(
      builder: (context, constraints) {
        final isCompact = constraints.maxWidth < 160;
        final scale = (constraints.maxWidth / 220).clamp(0.8, 1.55).toDouble();
        final padding = (isCompact ? 5.0 : 10.0) * scale;
        final iconSize = (constraints.maxWidth * 0.16).clamp(18.0, 42.0);
        final accountNumber = _accountNumber;
        final barcodeHeight =
            ((constraints.maxHeight - (padding * 2) - 26) * 0.72)
                .clamp(24.0, 110.0)
                .toDouble();

        return Container(
          padding: EdgeInsets.all(padding),
          decoration: BoxDecoration(
            color:
                customBgColor ??
                (isDark ? const Color(0xFF242424) : Colors.grey.shade100),
            gradient: customBgColor == null
                ? LinearGradient(
                    colors: isDark
                        ? [const Color(0xFF2C2C2E), const Color(0xFF1C1C1E)]
                        : [Colors.grey.shade100, Colors.grey.shade300],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  )
                : null,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  _buildPassIcon(iconSize, customFgColor),
                  SizedBox(width: (isCompact ? 4 : 7) * scale),
                  Expanded(
                    child: _buildOrganizationName(
                      isCompact,
                      customFgColor,
                      scale,
                    ),
                  ),
                  Icon(
                    Icons.qr_code_2_rounded,
                    size: (isCompact ? 12 : 16) * scale,
                    color: customFgColor.withValues(alpha: 0.7),
                  ),
                ],
              ),
              if (accountNumber.isNotEmpty)
                Expanded(
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      accountNumber,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: (isCompact ? 8 : 10) * scale,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 0.6 * scale,
                        color: customFgColor.withValues(alpha: 0.78),
                      ),
                    ),
                  ),
                )
              else
                const Spacer(),
              if (pass.barcodeValue.isNotEmpty)
                Container(
                  width: double.infinity,
                  height: barcodeHeight,
                  padding: EdgeInsets.all((isCompact ? 3 : 6) * scale),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(6 * scale),
                  ),
                  child: BarcodeWidget(
                    barcode: BarcodeUtils.getBarcodeFromFormat(
                      pass.barcodeFormat,
                    ),
                    data: pass.barcodeValue,
                    color: Colors.black,
                    errorBuilder: (_, _) => const Center(
                      child: Icon(
                        Icons.error_outline,
                        color: Colors.red,
                        size: 18,
                      ),
                    ),
                  ),
                )
              else
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (pass.logoText != null && pass.logoText!.isNotEmpty)
                      Text(
                        pass.logoText!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: (isCompact ? 9 : 12) * scale,
                          fontWeight: FontWeight.w700,
                          color: customFgColor,
                        ),
                      ),
                    Text(
                      pass.type.toUpperCase(),
                      style: TextStyle(
                        fontSize: (isCompact ? 7 : 9) * scale,
                        fontWeight: FontWeight.w500,
                        color: customFgColor.withValues(alpha: 0.6),
                      ),
                    ),
                  ],
                ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildPkpassCard(BuildContext context, bool isDark) {
    final background =
        _parseColor(pass.backgroundColor) ??
        (isDark ? const Color(0xFF242424) : Colors.grey.shade100);
    final foreground =
        _parseColor(pass.foregroundColor) ??
        (isDark ? Colors.white : Colors.black87);
    final label =
        _parseColor(pass.labelColor) ?? foreground.withValues(alpha: 0.72);
    return LayoutBuilder(
      builder: (context, constraints) {
        final scale = (constraints.maxWidth / 220).clamp(0.48, 1.0).toDouble();
        final isNarrow = constraints.maxWidth < 145;
        final isCompact = !isNarrow && constraints.maxWidth < 200;
        final padding =
            (isNarrow
                ? 6.0
                : isCompact
                ? 8.0
                : 10.0) *
            scale;
        final hasStrip =
            displayMode == PassDisplayMode.front &&
            pass.stripImagePath?.isNotEmpty == true;
        // Two-column cards are compact, but still have enough vertical space
        // for a strip image. Reserve the fixed-height treatment for the
        // genuinely narrow three-column layout.
        final showsCompactStrip = PassGridCard.usesCompactStripLayout(
          availableWidth: constraints.maxWidth,
          passType: pass.type,
          hasStrip: hasStrip,
        );
        final header = _fields('headerFields');
        final secondary = _fields('secondaryFields');
        final auxiliary = _fields('auxiliaryFields');
        return Container(
          color: background,
          padding: EdgeInsets.all(padding),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  _buildPkpassBrand(30 * scale, foreground),
                  SizedBox(width: 7 * scale),
                  Expanded(
                    child: Text(
                      pass.logoText ?? pass.organizationName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: foreground,
                        fontSize: 12 * scale,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  if (!isNarrow && header.isNotEmpty)
                    SizedBox(
                      width: (isCompact ? 58 : 76) * scale,
                      child: _pkpassFieldRow(
                        header,
                        label,
                        foreground,
                        scale,
                        alignEnd: true,
                        maxFields: 1,
                      ),
                    ),
                ],
              ),
              SizedBox(height: (isNarrow ? 3 : 5) * scale),
              if (pass.type == 'boardingPass')
                _boardingFields(
                  label,
                  foreground,
                  scale,
                  prominent: !isCompact && !isNarrow,
                )
              else
                _pkpassFieldRow(
                  _fields('primaryFields'),
                  label,
                  foreground,
                  scale,
                  primary: true,
                  maxFields: isNarrow ? 1 : 2,
                ),
              if (!isNarrow && !showsCompactStrip && secondary.isNotEmpty) ...[
                SizedBox(height: (isCompact ? 3 : 5) * scale),
                _pkpassFieldRow(
                  secondary,
                  label,
                  foreground,
                  scale,
                  maxFields: isCompact ? 2 : null,
                ),
              ],
              if (!isCompact && auxiliary.isNotEmpty) ...[
                SizedBox(height: 5 * scale),
                _pkpassFieldRow(auxiliary, label, foreground, scale),
              ],
              if (hasStrip) ...[
                SizedBox(height: 5 * scale),
                if (showsCompactStrip)
                  SizedBox(
                    height: 26 * scale,
                    width: double.infinity,
                    child: EncryptedImageDisplay(
                      imagePath: pass.stripImagePath!,
                      fit: BoxFit.contain,
                    ),
                  )
                else
                  Expanded(
                    child: EncryptedImageDisplay(
                      imagePath: pass.stripImagePath!,
                      width: double.infinity,
                      height: double.infinity,
                      fit: BoxFit.contain,
                    ),
                  ),
              ] else
                const Spacer(),
            ],
          ),
        );
      },
    );
  }

  Widget _buildPkpassBrand(double size, Color foreground) =>
      pass.logoImagePath?.isNotEmpty == true
      ? SizedBox(
          width: size * 1.8,
          height: size,
          child: EncryptedImageDisplay(
            imagePath: pass.logoImagePath!,
            fit: BoxFit.contain,
            errorWidget: _buildPassIcon(size, foreground),
          ),
        )
      : _buildPassIcon(size, foreground);
  Widget _boardingFields(
    Color label,
    Color foreground,
    double scale, {
    required bool prominent,
  }) {
    final primary = _fields('primaryFields');
    if (primary.length < 2) {
      return _pkpassFieldRow(
        primary,
        label,
        foreground,
        scale,
        primary: true,
        maxFields: prominent ? null : 1,
      );
    }
    return SizedBox(
      height: prominent ? 48 * scale : null,
      child: Row(
        children: [
          Expanded(
            child: _pkpassField(
              primary.first,
              label,
              foreground,
              scale,
              primary: true,
              prominent: prominent,
            ),
          ),
          Padding(
            padding: EdgeInsets.symmetric(horizontal: 5 * scale),
            child: Icon(
              Icons.arrow_forward_rounded,
              color: foreground,
              size: (prominent ? 20 : 16) * scale,
            ),
          ),
          Expanded(
            child: _pkpassField(
              primary[1],
              label,
              foreground,
              scale,
              primary: true,
              prominent: prominent,
              alignEnd: true,
            ),
          ),
        ],
      ),
    );
  }

  Widget _pkpassFieldRow(
    List<Map<String, dynamic>> fields,
    Color label,
    Color foreground,
    double scale, {
    bool primary = false,
    bool alignEnd = false,
    int? maxFields,
  }) {
    var populated = fields.where((field) => _value(field).isNotEmpty).toList();
    if (maxFields != null) populated = populated.take(maxFields).toList();
    if (populated.isEmpty) return const SizedBox.shrink();
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: populated
          .map(
            (field) => Expanded(
              child: _pkpassField(
                field,
                label,
                foreground,
                scale,
                primary: primary,
                alignEnd: alignEnd,
              ),
            ),
          )
          .toList(),
    );
  }

  Widget _pkpassField(
    Map<String, dynamic> field,
    Color label,
    Color foreground,
    double scale, {
    bool primary = false,
    bool prominent = false,
    bool alignEnd = false,
  }) => Column(
    crossAxisAlignment: alignEnd
        ? CrossAxisAlignment.end
        : CrossAxisAlignment.start,
    mainAxisSize: MainAxisSize.min,
    children: [
      if ((field['label']?.toString() ?? '').isNotEmpty)
        Text(
          field['label'].toString().toUpperCase(),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          textAlign: alignEnd ? TextAlign.end : TextAlign.start,
          style: TextStyle(
            color: label,
            fontSize:
                (prominent
                    ? 9
                    : primary
                    ? 8
                    : 7) *
                scale,
            fontWeight: FontWeight.w700,
          ),
        ),
      Text(
        _value(field),
        maxLines: primary ? 2 : 1,
        overflow: TextOverflow.ellipsis,
        textAlign: alignEnd ? TextAlign.end : TextAlign.start,
        style: TextStyle(
          color: foreground,
          fontSize:
              (prominent
                  ? 22
                  : primary
                  ? 18
                  : 10) *
              scale,
          fontWeight: primary ? FontWeight.w800 : FontWeight.w600,
        ),
      ),
    ],
  );
  List<Map<String, dynamic>> _fields(String section) {
    final sectionFields = pass.fields?[section];
    if (sectionFields is! List) return const [];
    return sectionFields
        .whereType<Map>()
        .map((field) => Map<String, dynamic>.from(field))
        .toList();
  }

  String _value(Map<String, dynamic> field) => field['value']?.toString() ?? '';

  String get _accountNumber {
    final customFields = pass.fields?['customFields'];
    if (customFields is! Map) return '';
    return customFields['Account #']?.toString().trim() ?? '';
  }

  Widget _buildOrganizationName(
    bool isCompact,
    Color foregroundColor,
    double scale,
  ) {
    final text = Text(
      pass.organizationName.toUpperCase(),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TextStyle(
        fontSize: (isCompact ? 8.5 : 11) * scale,
        fontWeight: FontWeight.bold,
        letterSpacing: 0.5 * scale,
        color: foregroundColor.withValues(alpha: 0.8),
      ),
    );
    if (truncateOrganizationName) return text;
    return FittedBox(
      fit: BoxFit.scaleDown,
      alignment: Alignment.centerLeft,
      child: text,
    );
  }

  Widget _buildPassIcon(double size, Color foregroundColor) {
    if (pass.iconImagePath != null && pass.iconImagePath!.isNotEmpty) {
      return ClipOval(
        child: EncryptedImageDisplay(
          imagePath: pass.iconImagePath!,
          width: size,
          height: size,
          fit: BoxFit.cover,
          errorWidget: _buildFallbackIcon(size, foregroundColor),
        ),
      );
    }
    return _buildFallbackIcon(size, foregroundColor);
  }

  Widget _buildFallbackIcon(double size, Color foregroundColor) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: foregroundColor.withValues(alpha: 0.15),
        shape: BoxShape.circle,
      ),
      child: Icon(
        Icons.confirmation_number_outlined,
        size: size * 0.58,
        color: foregroundColor.withValues(alpha: 0.8),
      ),
    );
  }
}
