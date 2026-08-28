import 'package:flutter_test/flutter_test.dart';
import 'package:kura/widgets/pass_grid_card.dart';

void main() {
  group('PassGridCard strip layout', () {
    test('uses available height for a two-column business-card strip', () {
      expect(
        PassGridCard.usesCompactStripLayout(
          availableWidth: 180,
          passType: 'generic',
          hasStrip: true,
        ),
        isFalse,
      );
    });

    test(
      'uses compact height for a narrow three-column business-card strip',
      () {
        expect(
          PassGridCard.usesCompactStripLayout(
            availableWidth: 120,
            passType: 'generic',
            hasStrip: true,
          ),
          isTrue,
        );
      },
    );

    test('does not use compact strip layout without a strip image', () {
      expect(
        PassGridCard.usesCompactStripLayout(
          availableWidth: 120,
          passType: 'generic',
          hasStrip: false,
        ),
        isFalse,
      );
    });
  });
}
