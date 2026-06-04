-- Card PIN hash (entity expects column; omitted from V001 cards DDL)
ALTER TABLE mushe.cards
    ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(255);
