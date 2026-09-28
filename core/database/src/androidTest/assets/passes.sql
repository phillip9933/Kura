CREATE TABLE passes(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT,
            organizationName TEXT,
            description TEXT,
            logoText TEXT,
            backgroundColor TEXT,
            foregroundColor TEXT,
            labelColor TEXT,
            barcodeValue TEXT,
            barcodeFormat TEXT,
            barcodeAltText TEXT,
            transitType TEXT,
            relevantDate TEXT,
            expiry_date TEXT,
            frontImagePath TEXT,
            backImagePath TEXT,
            stripImagePath TEXT,
            thumbnailImagePath TEXT,
            iconImagePath TEXT,
            logoImagePath TEXT,
            footerImagePath TEXT,
            sourceType TEXT,
            fields TEXT,
            orderIndex INTEGER DEFAULT 0,
            isArchived INTEGER NOT NULL DEFAULT 0
          );
CREATE INDEX idx_passes_order ON passes(orderIndex);
PRAGMA user_version=7;