#include "KuraBarcodeKit.h"
#include "Zint/zint.h"
#include <stdlib.h>
#include <string.h>

struct Format {
    const char *name;
    int symbology;
    size_t minimum;
    size_t maximum;
};

static const struct Format formats[] = {
    {"QR Code", BARCODE_QRCODE, 1, 8192}, {"Aztec", BARCODE_AZTEC, 1, 8192},
    {"PDF417", BARCODE_PDF417, 1, 8192}, {"Code 128", BARCODE_CODE128, 1, 8192},
    {"Data Matrix", BARCODE_DATAMATRIX, 1, 8192}, {"Code 39", BARCODE_EXCODE39, 1, 512},
    {"Code 93", BARCODE_CODE93, 1, 512}, {"EAN-8", BARCODE_EAN8, 7, 8},
    {"EAN-13", BARCODE_EAN13, 12, 13}, {"UPC-A", BARCODE_UPCA, 11, 12},
    {"UPC-E", BARCODE_UPCE, 7, 8}, {"ITF", BARCODE_C25INTER, 2, 80},
    {"Codabar", BARCODE_CODABAR, 1, 512}, {"ITF-14", BARCODE_ITF14, 13, 14},
    {"GS1-128", BARCODE_GS1_128, 1, 512}, {"ISBN", BARCODE_ISBNX, 10, 13},
    {"Telepen", BARCODE_TELEPEN, 1, 512}, {"POSTNET", BARCODE_POSTNET, 5, 11},
    {"RM4SCC", BARCODE_RM4SCC, 1, 512}, {"EAN-2", BARCODE_EAN_2ADDON, 2, 2},
    {"EAN-5", BARCODE_EAN_5ADDON, 5, 5}
};

static bool valid_length(int symbol, size_t length) {
    switch (symbol) {
        case BARCODE_ISBNX: return length == 10 || length == 13;
        case BARCODE_POSTNET: return length == 5 || length == 9 || length == 11;
        case BARCODE_C25INTER: return length % 2 == 0;
        default: return true;
    }
}

static bool numeric_format(int symbol) {
    switch (symbol) {
        case BARCODE_EAN8: case BARCODE_EAN13: case BARCODE_UPCA: case BARCODE_UPCE:
        case BARCODE_C25INTER: case BARCODE_ITF14: case BARCODE_POSTNET:
        case BARCODE_EAN_2ADDON: case BARCODE_EAN_5ADDON:
            return true;
        default: return false;
    }
}

static uint8_t codabar_guard(uint8_t ch) {
    if (ch >= 'a' && ch <= 'z') ch -= 'a' - 'A';
    switch (ch) {
        case 'A': case 'B': case 'C': case 'D': return ch;
        case 'T': return 'A';
        case 'N': return 'B';
        case '*': return 'C';
        case 'E': return 'D';
        default: return 0;
    }
}

void kura_barcode_dispose(KuraBarcodeImage *image) {
    if (image != NULL && image->pixels != NULL) {
        free(image->pixels);
        *image = (KuraBarcodeImage){0};
    }
}

bool kura_barcode_render(const char *format, const uint8_t *payload, size_t length, KuraBarcodeImage *image) {
    if (image == NULL) return false;
    *image = (KuraBarcodeImage){0};
    if (format == NULL || payload == NULL || length == 0 || length > 8192) return false;
    int symbology = 0;
    for (size_t index = 0; index < sizeof(formats) / sizeof(formats[0]); ++index) {
        const struct Format *entry = &formats[index];
        if (strcmp(format, entry->name) == 0 && length >= entry->minimum && length <= entry->maximum) {
            symbology = entry->symbology;
            break;
        }
    }
    if (!symbology || !valid_length(symbology, length)) return false;
    if (numeric_format(symbology)) {
        for (size_t i = 0; i < length; ++i) if (payload[i] < '0' || payload[i] > '9') return false;
    }
    if (symbology == BARCODE_EXCODE39) {
        bool basic = true;
        const char alphabet[] = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%";
        for (size_t i = 0; i < length; ++i) {
            if (payload[i] == 0 || strchr(alphabet, payload[i]) == NULL) { basic = false; break; }
        }
        if (basic) symbology = BARCODE_CODE39;
    }
    /* Match the Android writer's implicit A guards and alternate start/stop characters. */
    uint8_t codabar[514];
    if (symbology == BARCODE_CODABAR) {
        uint8_t start = codabar_guard(payload[0]), stop = codabar_guard(payload[length - 1]);
        if ((start == 0) != (stop == 0)) return false;
        if (start == 0) {
            codabar[0] = 'A';
            memcpy(codabar + 1, payload, length);
            codabar[length + 1] = 'A';
            length += 2;
        } else {
            memcpy(codabar, payload, length);
            codabar[0] = start;
            codabar[length - 1] = stop;
        }
        payload = codabar;
    }
    struct zint_symbol *symbol = ZBarcode_Create();
    if (symbol == NULL) return false;
    symbol->symbology = symbology;
    symbol->input_mode = symbology == BARCODE_GS1_128 ? GS1_MODE | GS1PARENS_MODE : UNICODE_MODE;
    symbol->output_options = BARCODE_QUIET_ZONES | OUT_BUFFER_INTERMEDIATE;
    symbol->show_hrt = 0;
    symbol->scale = 2.0f;
    int status = ZBarcode_Encode(symbol, payload, (int)length);
    /* Warnings other than ECI may describe a modified payload or a noncompliant symbol. */
    if ((status == 0 || status == ZINT_WARN_USES_ECI) && ZBarcode_Buffer(symbol, 0) == 0) {
        int width = symbol->bitmap_width, height = symbol->bitmap_height;
        if (width > 0 && height > 0 && width <= 8192 && height <= 4096
                && (size_t)width * height <= 8 * 1024 * 1024 && symbol->bitmap != NULL) {
            size_t count = (size_t)width * height;
            uint8_t *pixels = malloc(count);
            if (pixels != NULL) {
                for (size_t i = 0; i < count; ++i) pixels[i] = symbol->bitmap[i] == '1' ? 0 : 255;
                *image = (KuraBarcodeImage){pixels, width, height};
            }
        }
    }
    ZBarcode_Delete(symbol);
    return image->pixels != NULL;
}
