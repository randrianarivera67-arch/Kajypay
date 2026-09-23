CREATE TABLE IF NOT EXISTS lignes_sim (id TEXT PRIMARY KEY, appareil_id TEXT NOT NULL REFERENCES appareils(id), slot INTEGER NOT NULL CHECK (slot IN (1,2)), operateur TEXT NOT NULL CHECK (operateur IN ('mvola','orange','airtel')), numero TEXT, actif INTEGER NOT NULL DEFAULT 1, cree_le INTEGER NOT NULL, UNIQUE (appareil_id, slot));
ALTER TABLE sms ADD COLUMN sim_slot INTEGER;
INSERT INTO lignes_sim (id, appareil_id, slot, operateur, numero, actif, cree_le) SELECT lower(hex(randomblob(16))), a.id, 1, a.operateur, a.numero, 1, a.cree_le FROM appareils a WHERE NOT EXISTS (SELECT 1 FROM lignes_sim l WHERE l.appareil_id = a.id);
CREATE INDEX IF NOT EXISTS idx_lignes_appareil ON lignes_sim(appareil_id);
CREATE INDEX IF NOT EXISTS idx_sms_client_sim ON sms(client_id, sim_slot, recu_le);
