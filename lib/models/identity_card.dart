import 'package:kura/services/encryption_service.dart';
import 'dart:convert';

class IdentityCard {
  final int? id;
  final String name;
  final String value;
  final String cardType; // e.g., Passport, License, etc.
  final String? frontImagePath;
  final String? backImagePath;
  final String? color;
  final String? expiryDate;
  final Map<String, String>? customFields;
  int orderIndex;
  bool isArchived;

  IdentityCard({
    this.id,
    required this.name,
    required this.value,
    this.cardType = 'Identity Card',
    this.frontImagePath,
    this.backImagePath,
    this.color,
    this.expiryDate,
    this.customFields,
    this.orderIndex = 0,
    this.isArchived = false,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'value': value,
      'cardType': cardType,
      'frontImagePath': frontImagePath,
      'backImagePath': backImagePath,
      'color': color,
      'expiry_date': expiryDate,
      'customFields': customFields == null ? null : jsonEncode(customFields),
      'orderIndex': orderIndex,
      'isArchived': isArchived,
    };
  }

  Map<String, dynamic> toEncryptedMap() {
    final enc = EncryptionService.instance;
    return {
      'id': id,
      'name': enc.encryptText(name),
      'value': enc.encryptText(value),
      'cardType': enc.encryptText(cardType),
      'frontImagePath': frontImagePath,
      'backImagePath': backImagePath,
      'color': color,
      'expiry_date': enc.encryptText(expiryDate),
      'customFields': customFields == null
          ? null
          : enc.encryptJson(customFields!.cast<String, dynamic>()),
      'orderIndex': orderIndex,
      'isArchived': isArchived,
    };
  }

  factory IdentityCard.fromMap(Map<String, dynamic> map) {
    return IdentityCard(
      id: map['id'],
      name: map['name'],
      value: map['value'],
      cardType: map['cardType'] ?? 'Identity Card',
      frontImagePath: map['frontImagePath'],
      backImagePath: map['backImagePath'],
      color: map['color'],
      expiryDate: map['expiry_date'],
      customFields: map['customFields'] == null
          ? null
          : Map<String, String>.from(jsonDecode(map['customFields'])),
      orderIndex: map['orderIndex'] ?? 0,
      isArchived: map['isArchived'] == 1 || map['isArchived'] == true,
    );
  }

  factory IdentityCard.fromEncryptedMap(Map<String, dynamic> map) {
    final enc = EncryptionService.instance;
    return IdentityCard(
      id: map['id'],
      name: enc.decryptText(map['name']) ?? '',
      value: enc.decryptText(map['value']) ?? '',
      cardType: enc.decryptText(map['cardType']) ?? 'Identity Card',
      frontImagePath: map['frontImagePath'],
      backImagePath: map['backImagePath'],
      color: map['color'],
      expiryDate: enc.decryptText(map['expiry_date']),
      customFields: map['customFields'] == null
          ? null
          : enc.decryptJsonToStringMap(map['customFields']),
      orderIndex: map['orderIndex'] ?? 0,
      isArchived: map['isArchived'] == 1 || map['isArchived'] == true,
    );
  }
}
