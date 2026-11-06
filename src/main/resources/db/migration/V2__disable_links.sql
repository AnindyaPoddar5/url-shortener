-- Additive migration preserves all existing links and their counters.
ALTER TABLE links ADD COLUMN enabled BOOLEAN DEFAULT TRUE NOT NULL;
