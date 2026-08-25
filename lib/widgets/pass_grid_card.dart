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

  Color? _parseColor(String? hexString) {
    if (hexString == null || hexString.isEmpty) return null;
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
    // Mode 1: Front Image (with fallback to digital card)
    if (displayMode == PassDisplayMode.front &&
        pass.frontImagePath != null &&
        pass.frontImagePath!.isNotEmpty) {
      return EncryptedImageDisplay(
        imagePath: pass.frontImagePath!,
        fit: BoxFit.cover,
      );
    }

    // Mode 2: Back Image (with fallback to digital card)
    if (displayMode == PassDisplayMode.back &&
        pass.backImagePath != null &&
        pass.backImagePath!.isNotEmpty) {
      return EncryptedImageDisplay(
        imagePath: pass.backImagePath!,
        fit: BoxFit.cover,
      );
    }

    // Mode 3: Styled Digital "Fake Card" View
    final customBgColor = _parseColor(pass.backgroundColor);
    final customFgColor =
        _parseColor(pass.foregroundColor) ??
        (isDark ? Colors.white : Colors.black87);

    return LayoutBuilder(
      builder: (context, constraints) {
        final isCompact = constraints.maxWidth < 160;
        final scale =
            (constraints.maxWidth / 220).clamp(0.8, 1.55).toDouble();
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
