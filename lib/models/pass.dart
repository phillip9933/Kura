import 'dart:convert';
import 'package:kura/services/encryption_service.dart';

class Pass {
  final int? id;
  final String type;
  final String organizationName;
  final String? description;
  final String? logoText;
  final String? backgroundColor;
  final String? foregroundColor;
  final String? labelColor;
  final String barcodeValue;
  final String? barcodeFormat;
  final String? barcodeAltText;
  final String? transitType;
  final String? relevantDate;
  final String? expiryDate;
  final String? frontImagePath;
  final String? backImagePath;
  final String? stripImagePath;
  final String? thumbnailImagePath;
  final String? iconImagePath;
  final Map<String, dynamic>? fields;
  int orderIndex;
  bool isArchived;

  Pass({
    this.id,
    required this.type,
    required this.organizationName,
    this.description,
    this.logoText,
    this.backgroundColor,
    this.foregroundColor,
    this.labelColor,
    required this.barcodeValue,
    this.barcodeFormat,
    this.barcodeAltText,
    this.transitType,
    this.relevantDate,
    this.expiryDate,
    this.frontImagePath,
    this.backImagePath,
    this.stripImagePath,
    this.thumbnailImagePath,
    this.iconImagePath,
    this.fields,
    this.orderIndex = 0,
    this.isArchived = false,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'type': type,
      'organizationName': organizationName,
      'description': description,
      'logoText': logoText,
      'backgroundColor': backgroundColor,
      'foregroundColor': foregroundColor,
      'labelColor': labelColor,
      'barcodeValue': barcodeValue,
      'barcodeFormat': barcodeFormat,
      'barcodeAltText': barcodeAltText,
      'transitType': transitType,
      'relevantDate': relevantDate,
      'expiry_date': expiryDate,
      'frontImagePath': frontImagePath,
      'backImagePath': backImagePath,
      'stripImagePath': stripImagePath,
      'thumbnailImagePath': thumbnailImagePath,
      'iconImagePath': iconImagePath,
      'fields': fields != null ? jsonEncode(fields) : null,
      'orderIndex': orderIndex,
      'isArchived': isArchived,
    };
  }

  Map<String, dynamic> toEncryptedMap() {
    final enc = EncryptionService.instance;
    return {
      'id': id,
      'type': type,
      'organizationName': enc.encryptText(organizationName),
      'description': enc.encryptText(description),
      'logoText': enc.encryptText(logoText),
      'backgroundColor': backgroundColor,
      'foregroundColor': foregroundColor,
      'labelColor': labelColor,
      'barcodeValue': enc.encryptText(barcodeValue),
      'barcodeFormat': barcodeFormat,
      'barcodeAltText': enc.encryptText(barcodeAltText),
      'transitType': transitType,
      'relevantDate': enc.encryptText(relevantDate),
      'expiry_date': enc.encryptText(expiryDate),
      'frontImagePath': frontImagePath,
      'backImagePath': backImagePath,
      'stripImagePath': stripImagePath,
      'thumbnailImagePath': thumbnailImagePath,
      'iconImagePath': iconImagePath,
      'fields': fields != null ? enc.encryptJson(fields!) : null,
      'orderIndex': orderIndex,
      'isArchived': isArchived,
    };
  }

  factory Pass.fromMap(Map<String, dynamic> map) {
    return Pass(
      id: map['id'],
      type: map['type'],
      organizationName: map['organizationName'],
      description: map['description'],
      logoText: map['logoText'],
      backgroundColor: map['backgroundColor'],
      foregroundColor: map['foregroundColor'],
      labelColor: map['labelColor'],
      barcodeValue: map['barcodeValue'],
      barcodeFormat: map['barcodeFormat'],
      barcodeAltText: map['barcodeAltText'],
      transitType: map['transitType'],
      relevantDate: map['relevantDate'],
      expiryDate: map['expiry_date'],
      frontImagePath: map['frontImagePath'],
      backImagePath: map['backImagePath'],
      stripImagePath: map['stripImagePath'],
      thumbnailImagePath: map['thumbnailImagePath'],
      iconImagePath: map['iconImagePath'],
      fields: map['fields'] != null ? jsonDecode(map['fields']) : null,
      orderIndex: map['orderIndex'] ?? 0,
      isArchived: map['isArchived'] == 1 || map['isArchived'] == true,
    );
  }

  factory Pass.fromEncryptedMap(Map<String, dynamic> map) {
    final enc = EncryptionService.instance;
    return Pass(
      id: map['id'],
      type: map['type'] ?? 'generic',
      organizationName: enc.decryptText(map['organizationName']) ?? '',
      description: enc.decryptText(map['description']),
      logoText: enc.decryptText(map['logoText']),
      backgroundColor: map['backgroundColor'],
      foregroundColor: map['foregroundColor'],
      labelColor: map['labelColor'],
      barcodeValue: enc.decryptText(map['barcodeValue']) ?? '',
      barcodeFormat: map['barcodeFormat'],
      barcodeAltText: enc.decryptText(map['barcodeAltText']),
      transitType: map['transitType'],
      relevantDate: enc.decryptText(map['relevantDate']),
      expiryDate: enc.decryptText(map['expiry_date']),
      frontImagePath: map['frontImagePath'],
      backImagePath: map['backImagePath'],
      stripImagePath: map['stripImagePath'],
      thumbnailImagePath: map['thumbnailImagePath'],
      iconImagePath: map['iconImagePath'],
      fields: map['fields'] != null
          ? enc.decryptJsonToDynamicMap(map['fields'])
          : null,
      orderIndex: map['orderIndex'] ?? 0,
      isArchived: map['isArchived'] == 1 || map['isArchived'] == true,
    );
  }
}
