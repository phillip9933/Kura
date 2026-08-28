import 'package:flutter_test/flutter_test.dart';
import 'package:kura/models/startup_settings_provider.dart';
import 'package:kura/screens/homescreen.dart';

void main() {
  group('HomeScreen grid top spacing', () {
    test('keeps spacing below a top control row', () {
      expect(HomeScreen.gridTopSpacing(ControlRowPosition.top), 12);
    });

    test('removes artificial top spacing when controls are at the bottom', () {
      expect(HomeScreen.gridTopSpacing(ControlRowPosition.bottom), 0);
    });
  });
}
