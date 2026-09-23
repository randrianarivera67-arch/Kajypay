ALTER TABLE sms ADD COLUMN nom_payeur TEXT;
CREATE TABLE IF NOT EXISTS codes_appairage (code TEXT PRIMARY KEY, appareil_id TEXT NOT NULL REFERENCES appareils(id), expire_le INTEGER NOT NULL, utilise INTEGER NOT NULL DEFAULT 0);
CREATE INDEX IF NOT EXISTS idx_appareils_jeton ON appareils(jeton_hash);
CREATE INDEX IF NOT EXISTS idx_codes_appareil ON codes_appairage(appareil_id);
