ALTER TABLE lignes_sim ADD COLUMN retrait_code TEXT;
ALTER TABLE lignes_sim ADD COLUMN retrait_menu TEXT;
ALTER TABLE lignes_sim ADD COLUMN retrait_pin_separe INTEGER NOT NULL DEFAULT 0;
ALTER TABLE lignes_sim ADD COLUMN retrait_max_steps INTEGER NOT NULL DEFAULT 1;
