#ifndef KURA_BARCODE_KIT_H
#define KURA_BARCODE_KIT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef struct {
    uint8_t *pixels;  /* One grayscale byte per pixel, row-major. */
    int width;
    int height;
} KuraBarcodeImage;

/* On success the caller owns image->pixels and calls kura_barcode_dispose(). */
bool kura_barcode_render(const char *format, const uint8_t *payload, size_t length, KuraBarcodeImage *image);
void kura_barcode_dispose(KuraBarcodeImage *image);

#endif
