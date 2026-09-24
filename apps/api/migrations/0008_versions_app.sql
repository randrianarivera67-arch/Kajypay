CREATE TABLE IF NOT EXISTS versions_app (
  app TEXT PRIMARY KEY,
  version_code INTEGER NOT NULL,
  version_nom TEXT NOT NULL,
  url TEXT NOT NULL,
  notes TEXT,
  obligatoire INTEGER NOT NULL DEFAULT 0,
  maj INTEGER NOT NULL
);
