import 'dart:async';
import 'package:flutter/services.dart';

class ClipboardService {
  static final ClipboardService _instance = ClipboardService._();
  static ClipboardService get instance => _instance;

  ClipboardService._();

  Timer? _clearTimer;
  int _copyGeneration = 0;

  static const Duration _defaultClearDuration = Duration(seconds: 40);

  Future<void> copy(String text, {Duration? clearAfter}) async {
    final generation = ++_copyGeneration;
    await Clipboard.setData(ClipboardData(text: text));

    // A newer copy may have completed while this platform call was pending.
    if (generation != _copyGeneration) return;

    _clearTimer?.cancel();
    _clearTimer = Timer(
      clearAfter ?? _defaultClearDuration,
      () => _clearClipboard(generation),
    );
  }

  Future<void> _clearClipboard(int generation) async {
    if (generation != _copyGeneration) return;
    try {
      await Clipboard.setData(const ClipboardData(text: ''));
    } catch (_) {}

    if (generation == _copyGeneration) _clearTimer = null;
  }
}
