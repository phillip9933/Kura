import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
import 'package:image_cropper/image_cropper.dart';
import 'package:image_picker/image_picker.dart';
import 'package:flutter/material.dart';
import 'package:kura/services/encryption_service.dart';

/// Saves an image to the app's documents directory and encrypts it.
///
/// The image is first copied to the app directory, then encrypted using
/// AES-256-GCM. The original unencrypted file is deleted after encryption.
/// Returns the path to the encrypted file (.enc extension).
Future<String?> saveImageToAppDirectory(File imageFile) async {
  try {
    final directory = await getApplicationDocumentsDirectory();
    final fileExtension = p.extension(imageFile.path);
    final newFileName =
        '${DateTime.now().microsecondsSinceEpoch}$fileExtension';
    final newPath = p.join(directory.path, newFileName);
    final newFile = await imageFile.copy(newPath);

    // Encrypt the saved image file
    final encryptedPath = await EncryptionService.instance.encryptImageFile(
      newFile.path,
    );

    // Securely delete the original source file (e.g. from camera cache or gallery)
    // to ensure no plaintext traces are left on disk.
    if (await imageFile.exists()) {
      await imageFile.delete();
    }

    return encryptedPath;
  } catch (_) {
    return null;
  }
}

/// Lets users choose a source, then aligns the image to a standard ID-1 card.
Future<File?> pickAndCropCardImage(
  BuildContext context, {
  required String sideLabel,
}) async {
  final source = await showModalBottomSheet<ImageSource>(
    context: context,
    builder: (sheetContext) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.camera_alt_outlined),
            title: const Text('Take Photo'),
            onTap: () => Navigator.pop(sheetContext, ImageSource.camera),
          ),
          ListTile(
            leading: const Icon(Icons.photo_library_outlined),
            title: const Text('Choose from Gallery'),
            onTap: () => Navigator.pop(sheetContext, ImageSource.gallery),
          ),
          ListTile(
            leading: const Icon(Icons.close_rounded),
            title: const Text('Cancel'),
            onTap: () => Navigator.pop(sheetContext),
          ),
        ],
      ),
    ),
  );
  if (source == null) return null;

  final pickedFile = await ImagePicker().pickImage(
    source: source,
    maxWidth: 2048,
    maxHeight: 2048,
    imageQuality: 90,
  );
  if (pickedFile == null) return null;

  final croppedFile = await ImageCropper().cropImage(
    sourcePath: pickedFile.path,
    aspectRatio: const CropAspectRatio(ratioX: 1.586, ratioY: 1),
    compressFormat: ImageCompressFormat.jpg,
    compressQuality: 90,
    uiSettings: [
      AndroidUiSettings(
        toolbarTitle: 'Crop $sideLabel Image',
        lockAspectRatio: true,
        aspectRatioPresets: const [_CardAspectRatioPreset()],
      ),
      IOSUiSettings(
        title: 'Crop $sideLabel Image',
        aspectRatioLockEnabled: true,
        aspectRatioPickerButtonHidden: true,
        aspectRatioPresets: const [_CardAspectRatioPreset()],
      ),
    ],
  );
  return croppedFile == null ? null : File(croppedFile.path);
}

class _CardAspectRatioPreset implements CropAspectRatioPresetData {
  const _CardAspectRatioPreset();

  @override
  (int, int)? get data => (1586, 1000);

  @override
  String get name => 'Card';
}
