CREATE TABLE wallets(
            id INTEGER PRIMARY KEY,
            name TEXT,
            number TEXT,
            expiry TEXT,
            network TEXT,
            issuer TEXT,
            customFields TEXT,
            spends TEXT,
            rewards TEXT,
            annualFeeWaiver TEXT,
            maxlimit TEXT,
            cardtype TEXT,
            billdate TEXT,
            category TEXT,
            color TEXT,
            frontImagePath TEXT,
            backImagePath TEXT,
            orderIndex INTEGER DEFAULT 0,
            isArchived INTEGER NOT NULL DEFAULT 0
          );
CREATE INDEX idx_wallets_order ON wallets(orderIndex);
PRAGMA user_version=8;