ALTER TABLE clients ADD COLUMN retrait_autorise INTEGER NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS retraits (
  id TEXT PRIMARY KEY,
  client_id TEXT NOT NULL REFERENCES clients(id),
  appareil_id TEXT REFERENCES appareils(id),
  sim_slot INTEGER,
  operateur TEXT NOT NULL CHECK (operateur IN ('mvola','orange','airtel')),
  numero_beneficiaire TEXT NOT NULL,
  montant_ar INTEGER NOT NULL,
  reference_client TEXT,
  statut TEXT NOT NULL DEFAULT 'en_attente' CHECK (statut IN ('en_attente','pris','envoye','confirme','echoue','annule')),
  texte_operateur TEXT,
  motif TEXT,
  cree_le INTEGER NOT NULL,
  pris_le INTEGER,
  fini_le INTEGER
);
CREATE INDEX IF NOT EXISTS idx_retraits_client ON retraits(client_id, cree_le);
CREATE INDEX IF NOT EXISTS idx_retraits_attente ON retraits(client_id, statut, cree_le);
