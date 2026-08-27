import 'dart:convert';
import 'dart:io';
import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:kura/models/db_helper.dart';
import 'package:kura/services/image_service.dart';

class _PassImagePaths {
  const _PassImagePaths({
    this.icon,
    this.logo,
    this.strip,
    this.thumbnail,
    this.background,
    this.footer,
  });

  final String? icon;
  final String? logo;
  final String? strip;
  final String? thumbnail;
  final String? background;
  final String? footer;
}

class PkpassService {
  static final PkpassService instance = PkpassService._();
  static const int _maxUncompressedArchiveBytes = 10 * 1024 * 1024;
  static const int _maxArchiveFiles = 30;
  static const int _maxPassJsonBytes = 500 * 1024;
  static const int _maxNestedPkpassDepth = 3;

  PkpassService._();

  Future<Pass?> parsePkpass(String filePath) async {
    try {
      final bytes = await File(filePath).readAsBytes();
      return await _parsePkpassFromBytes(bytes);
    } catch (_) {
      return null;
    }
  }

  Future<Pass?> _parsePkpassFromBytes(Uint8List bytes, {int depth = 0}) async {
    try {
      if (depth > _maxNestedPkpassDepth) return null;
      final archive = ZipDecoder().decodeBytes(bytes);
      final files = archive.files.where((file) => file.isFile).toList();
      if (files.length > _maxArchiveFiles) return null;

      var uncompressedBytes = 0;
      for (final file in files) {
        if (file.size < 0 ||
            file.size > _maxUncompressedArchiveBytes - uncompressedBytes) {
          return null;
        }
        uncompressedBytes += file.size;
      }

      for (final file in files) {
        if (file.name.endsWith('.pkpass') && file.isFile) {
          final nestedPass = await _parsePkpassFromBytes(
            Uint8List.fromList(file.content as List<int>),
            depth: depth + 1,
          );
          if (nestedPass != null) {
            return nestedPass;
          }
        }
      }

      ArchiveFile? passFile;
      for (final file in files) {
        if (file.name == 'pass.json' || file.name.endsWith('/pass.json')) {
          passFile = file;
          break;
        }
      }

      if (passFile == null) {
        return null;
      }

      if (passFile.size < 0 || passFile.size > _maxPassJsonBytes) {
        return null;
      }

      final passJsonBytes = passFile.content as List<int>;
      if (passJsonBytes.length > _maxPassJsonBytes) return null;

      String passJsonStr;
      try {
        passJsonStr = utf8.decode(passJsonBytes);
      } catch (_) {
        passJsonStr = latin1.decode(passJsonBytes);
      }
      final passJson = Map<String, dynamic>.from(
        jsonDecode(passJsonStr) as Map,
      );
      final localizedStrings = _localizedStrings(archive);
      _resolveLocalizedValues(passJson, localizedStrings);

      String name =
          passJson['organizationName'] ??
          passJson['description'] ??
          'Imported Pass';
      String description = passJson['description'] ?? '';
      String? logoText = passJson['logoText'];
      final expiryDate = _expiryDateValue(
        passJson['expirationDate'] ?? passJson['relevantDate'],
      );
      String number = '';
      String? barcodeFormat;
      String? barcodeAltText;

      if (passJson['barcodes'] != null &&
          passJson['barcodes'] is List &&
          (passJson['barcodes'] as List).isNotEmpty) {
        number = (passJson['barcodes'] as List)[0]['message'] ?? '';
        barcodeFormat = (passJson['barcodes'] as List)[0]['format']?.toString();
        barcodeAltText = (passJson['barcodes'] as List)[0]['altText']
            ?.toString();
      } else if (passJson['barcode'] != null) {
        number = passJson['barcode']['message'] ?? '';
        barcodeFormat = passJson['barcode']['format']?.toString();
        barcodeAltText = passJson['barcode']['altText']?.toString();
      }

      // Determine pass type and extract fields
      final types = [
        'storeCard',
        'coupon',
        'eventTicket',
        'generic',
        'boardingPass',
      ];
      String passType = 'generic';
      Map<String, dynamic> fields = {};
      String? transitType;

      for (var type in types) {
        if (passJson[type] != null) {
          passType = type;
          final passData = passJson[type];

          fields['primaryFields'] = passData['primaryFields'];
          fields['secondaryFields'] = passData['secondaryFields'];
          fields['auxiliaryFields'] = passData['auxiliaryFields'];
          fields['backFields'] = passData['backFields'];
          fields['headerFields'] = passData['headerFields'];

          if (type == 'boardingPass') {
            transitType = passData['transitType'];
          }
          break;
        }
      }

      // Extract colors
      String? backgroundColor = passJson['backgroundColor'];
      String? foregroundColor = passJson['foregroundColor'];
      String? labelColor = passJson['labelColor'];

      final imagePaths = await _extractPassImages(archive);

      return Pass(
        type: passType,
        organizationName: name,
        description: description,
        logoText: logoText,
        backgroundColor: backgroundColor,
        foregroundColor: foregroundColor,
        labelColor: labelColor,
        barcodeValue: number,
        barcodeFormat: barcodeFormat,
        barcodeAltText: barcodeAltText,
        transitType: transitType,
        relevantDate: passJson['relevantDate']?.toString(),
        expiryDate: expiryDate,
        frontImagePath: imagePaths.background,
        backImagePath: null,
        stripImagePath: imagePaths.strip,
        thumbnailImagePath: imagePaths.thumbnail,
        iconImagePath: imagePaths.icon,
        logoImagePath: imagePaths.logo,
        footerImagePath: imagePaths.footer,
        sourceType: 'pkpass',
        fields: fields,
      );
    } catch (_) {
      return null;
    }
  }

  String? _expiryDateValue(dynamic value) {
    if (value == null) return null;
    final date = DateTime.tryParse(value.toString());
    if (date == null) return null;
    return '${date.month.toString().padLeft(2, '0')}/${(date.year % 100).toString().padLeft(2, '0')}';
  }

  Map<String, String> _localizedStrings(Archive archive) {
    final files = archive.files.where((file) => file.isFile).toList();
    ArchiveFile? stringsFile;
    for (final preferredDirectory in ['en.lproj', 'Base.lproj']) {
      stringsFile = files.cast<ArchiveFile?>().firstWhere(
        (file) =>
            file?.name.endsWith('$preferredDirectory/pass.strings') ?? false,
        orElse: () => null,
      );
      if (stringsFile != null) break;
    }
    stringsFile ??= files.cast<ArchiveFile?>().firstWhere(
      (file) => file?.name.endsWith('.lproj/pass.strings') ?? false,
      orElse: () => null,
    );
    if (stringsFile == null) return const {};

    try {
      final bytes = stringsFile.content as List<int>;
      final content = _decodeStringsFile(bytes);
      final strings = <String, String>{};
      for (final match in RegExp(
        r'"((?:\\.|[^"\\])*)"\s*=\s*"((?:\\.|[^"\\])*)"\s*;',
      ).allMatches(content)) {
        strings[_unescapeString(match.group(1)!)] = _unescapeString(
          match.group(2)!,
        );
      }
      return strings;
    } catch (_) {
      return const {};
    }
  }

  String _decodeStringsFile(List<int> bytes) {
    if (bytes.length >= 2 && bytes[0] == 0xFF && bytes[1] == 0xFE) {
      return String.fromCharCodes([
        for (var index = 2; index + 1 < bytes.length; index += 2)
          bytes[index] | (bytes[index + 1] << 8),
      ]);
    }
    if (bytes.length >= 2 && bytes[0] == 0xFE && bytes[1] == 0xFF) {
      return String.fromCharCodes([
        for (var index = 2; index + 1 < bytes.length; index += 2)
          (bytes[index] << 8) | bytes[index + 1],
      ]);
    }
    try {
      return utf8.decode(bytes);
    } catch (_) {
      return latin1.decode(bytes);
    }
  }

  String _unescapeString(String value) => value
      .replaceAll(r'\"', '"')
      .replaceAll(r'\n', '\n')
      .replaceAll(r'\\', r'\');

  void _resolveLocalizedValues(dynamic value, Map<String, String> strings) {
    if (value is Map) {
      for (final entry in value.entries.toList()) {
        final child = entry.value;
        if (child is String && strings.containsKey(child)) {
          value[entry.key] = strings[child];
        } else {
          _resolveLocalizedValues(child, strings);
        }
      }
    } else if (value is List) {
      for (final child in value) {
        _resolveLocalizedValues(child, strings);
      }
    }
  }

  Future<_PassImagePaths> _extractPassImages(Archive archive) async {
    final files = archive.files.where((file) => file.isFile).toList();
    return _PassImagePaths(
      icon: await _saveArchiveImage(files, [
        'icon@3x.png',
        'icon@2x.png',
        'icon.png',
      ]),
      logo: await _saveArchiveImage(files, [
        'logo@3x.png',
        'logo@2x.png',
        'logo.png',
      ]),
      strip: await _saveArchiveImage(files, [
        'strip@3x.png',
        'strip@2x.png',
        'strip.png',
      ]),
      thumbnail: await _saveArchiveImage(files, [
        'thumbnail@3x.png',
        'thumbnail@2x.png',
        'thumbnail.png',
      ]),
      background: await _saveArchiveImage(files, [
        'background@3x.png',
        'background@2x.png',
        'background.png',
      ]),
      footer: await _saveArchiveImage(files, [
        'footer@3x.png',
        'footer@2x.png',
        'footer.png',
      ]),
    );
  }

  Future<String?> _saveArchiveImage(
    List<ArchiveFile> files,
    List<String> names,
  ) async {
    ArchiveFile? imageFile;
    for (final name in names) {
      for (final file in files) {
        if (file.name == name || file.name.endsWith('/$name')) {
          imageFile = file;
          break;
        }
      }
      if (imageFile != null) break;
    }
    if (imageFile == null || imageFile.size <= 0) return null;

    final extension = imageFile.name.endsWith('.jpg') ? '.jpg' : '.png';
    final temporaryFile = File(
      '${Directory.systemTemp.path}/kura_pkpass_${DateTime.now().microsecondsSinceEpoch}$extension',
    );
    try {
      await temporaryFile.writeAsBytes(imageFile.content as List<int>);
      return await saveImageToAppDirectory(temporaryFile);
    } catch (_) {
      if (await temporaryFile.exists()) await temporaryFile.delete();
      return null;
    }
  }

  Future<Uint8List?> generatePkpass(Pass pass) async {
    try {
      final passJson = _generatePassJson(pass);
      final passJsonContent = utf8.encode(jsonEncode(passJson));

      // A minimal 1x1 black PNG for icon.png (required by Apple Wallet)
      final iconBytes = Uint8List.fromList([
        0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
        0x49,
        0x48,
        0x44,
        0x52,
        0x00,
        0x00,
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x01,
        0x08,
        0x00,
        0x00,
        0x00,
        0x00,
        0x3A,
        0x7E,
        0x9B,
        0x55,
        0x00,
        0x00,
        0x00,
        0x0A,
        0x49,
        0x44,
        0x41,
        0x54,
        0x08,
        0xD7,
        0x63,
        0x60,
        0x00,
        0x00,
        0x00,
        0x02,
        0x00,
        0x01,
        0xE2,
        0x21,
        0xBC,
        0x33,
        0x00,
        0x00,
        0x00,
        0x00,
        0x49,
        0x45,
        0x4E,
        0x44,
        0xAE,
        0x42,
        0x60,
        0x82,
      ]);

      final manifest = {
        'pass.json': sha1.convert(passJsonContent).toString(),
        'icon.png': sha1.convert(iconBytes).toString(),
      };

      final archive = Archive();
      archive.addFile(
        ArchiveFile('pass.json', passJsonContent.length, passJsonContent),
      );
      archive.addFile(ArchiveFile('icon.png', iconBytes.length, iconBytes));

      final manifestContent = utf8.encode(jsonEncode(manifest));
      archive.addFile(
        ArchiveFile('manifest.json', manifestContent.length, manifestContent),
      );

      final zipData = ZipEncoder().encode(archive);
      return Uint8List.fromList(zipData);
    } catch (_) {
      return null;
    }
  }

  Map<String, dynamic> _generatePassJson(Pass pass) {
    final Map<String, dynamic> passJson = {
      'formatVersion': 1,
      'passTypeIdentifier': 'pass.com.sidhant.wallet',
      'teamIdentifier': 'WALLETBOX',
      'serialNumber':
          pass.id?.toString() ??
          DateTime.now().millisecondsSinceEpoch.toString(),
      'organizationName': pass.organizationName,
      'description': pass.description ?? pass.organizationName,
      'logoText': pass.logoText ?? pass.organizationName,
      'sharingProhibited': false,
    };

    if (pass.backgroundColor != null) {
      passJson['backgroundColor'] = _hexToRgb(pass.backgroundColor!);
    }
    if (pass.foregroundColor != null) {
      passJson['foregroundColor'] = _hexToRgb(pass.foregroundColor!);
    }
    if (pass.labelColor != null) {
      passJson['labelColor'] = _hexToRgb(pass.labelColor!);
    }

    if (pass.barcodeValue.isNotEmpty) {
      passJson['barcodes'] = [
        {
          'format': pass.barcodeFormat ?? 'PKBarcodeFormatQR',
          'message': pass.barcodeValue,
          'messageEncoding': 'iso-8859-1',
          'altText': pass.barcodeAltText ?? pass.barcodeValue,
        },
      ];
      passJson['barcode'] = passJson['barcodes'][0];
    }

    final typeData = <String, dynamic>{};
    if (pass.fields != null) {
      pass.fields!.forEach((key, value) {
        if (value is List) {
          typeData[key] = value
              .map(
                (f) => {
                  'key':
                      f['key'] ??
                      '${f['label']?.toString().toLowerCase().replaceAll(' ', '_') ?? 'field'}_${value.indexOf(f)}',
                  'label': f['label'] ?? '',
                  'value': f['value'] ?? '',
                },
              )
              .toList();
        }
      });
    }

    if (pass.type == 'boardingPass' && pass.transitType != null) {
      typeData['transitType'] = pass.transitType;
    }

    passJson[pass.type] = typeData;

    return passJson;
  }

  String _hexToRgb(String hex) {
    if (!hex.startsWith('#')) return hex; // Already in rgb or other format
    hex = hex.replaceAll('#', '');
    try {
      if (hex.length == 6) {
        final r = int.parse(hex.substring(0, 2), radix: 16);
        final g = int.parse(hex.substring(2, 4), radix: 16);
        final b = int.parse(hex.substring(4, 6), radix: 16);
        return 'rgb($r, $g, $b)';
      }
    } catch (_) {}
    return 'rgb(0, 0, 0)';
  }
}
