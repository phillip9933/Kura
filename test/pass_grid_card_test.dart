import 'package:flutter_test/flutter_test.dart';
import 'package:kura/models/pass.dart';
import 'package:kura/widgets/pass_grid_card.dart';

void main() {
  group('PassGridCard strip layout', () {
    final pass = Pass(
      type: 'generic',
      organizationName: 'Example pass',
      barcodeValue: '',
      frontImagePath: 'background.enc',
      logoImagePath: 'logo.enc',
      iconImagePath: 'icon.enc',
    );

    test('uses available height for a two-column business-card strip', () {
      expect(
        PassGridCard.usesCompactStripLayout(
          gridColumns: 2,
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
            gridColumns: 3,
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
          gridColumns: 3,
          passType: 'generic',
          hasStrip: false,
        ),
        isFalse,
      );
    });

    test('renders strip imagery in virtual cards mode', () {
      expect(
        PassGridCard.showsPkpassStrip(
          displayMode: PassDisplayMode.card,
          hasStripImage: true,
        ),
        isTrue,
      );
    });

    test('identifies social and link metadata for compact grids', () {
      expect(
        PassGridCard.isSocialOrLinkField({
          'label': 'Blog',
          'value': 'https://example.com',
        }),
        isTrue,
      );
      expect(
        PassGridCard.isSocialOrLinkField({
          'label': 'Twitter',
          'value': '@kura',
        }),
        isTrue,
      );
      expect(
        PassGridCard.isSocialOrLinkField({
          'label': 'Member ID',
          'value': '12345',
        }),
        isFalse,
      );
    });

    test('uses imported PKPASS background and logo assets', () {
      expect(PassGridCard.pkpassBackgroundImagePath(pass), 'background.enc');
      expect(PassGridCard.pkpassBrandImagePath(pass), 'logo.enc');
    });

    test('uses the imported icon when a PKPASS logo is unavailable', () {
      final iconOnlyPass = Pass(
        type: 'generic',
        organizationName: 'Icon-only pass',
        barcodeValue: '',
        iconImagePath: 'icon.enc',
      );

      expect(PassGridCard.pkpassBrandImagePath(iconOnlyPass), 'icon.enc');
    });

    test(
      'uses an imported thumbnail when no other PKPASS image is available',
      () {
        final thumbnailOnlyPass = Pass(
          type: 'generic',
          organizationName: 'Thumbnail-only pass',
          barcodeValue: '',
          thumbnailImagePath: 'thumbnail.enc',
        );

        expect(
          PassGridCard.pkpassBackgroundImagePath(thumbnailOnlyPass),
          'thumbnail.enc',
        );
        expect(
          PassGridCard.pkpassBrandImagePath(thumbnailOnlyPass),
          'thumbnail.enc',
        );
      },
    );
  });
}
