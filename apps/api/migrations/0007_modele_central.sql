CREATE TABLE IF NOT EXISTS modeles_retrait (
  operateur TEXT PRIMARY KEY CHECK (operateur IN ('mvola','orange','airtel')),
  code TEXT NOT NULL,
  pin_separe INTEGER NOT NULL DEFAULT 0,
  max_steps INTEGER NOT NULL DEFAULT 1,
  maj INTEGER NOT NULL
);
ALTER TABLE retraits ADD COLUMN pin_chiffre TEXT;
