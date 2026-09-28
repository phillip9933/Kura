CREATE TABLE identities(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT,
            value TEXT,
            cardType TEXT,
            frontImagePath TEXT,
            backImagePath TEXT,
            color TEXT,
            expiry_date TEXT,
            customFields TEXT,
            orderIndex INTEGER DEFAULT 0,
            isArchived INTEGER NOT NULL DEFAULT 0
          );
CREATE INDEX idx_identities_order ON identities(orderIndex);
PRAGMA user_version=6;