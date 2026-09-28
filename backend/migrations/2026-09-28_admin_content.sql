CREATE TABLE IF NOT EXISTS arpeggios (
    id VARCHAR PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    chord VARCHAR(50),
    notes JSONB NOT NULL,
    difficulty VARCHAR(20) NOT NULL DEFAULT 'easy',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_arpeggios_difficulty CHECK (difficulty IN ('easy', 'medium', 'hard'))
);

CREATE INDEX IF NOT EXISTS idx_arpeggios_active_order
    ON arpeggios(active, sort_order, created_at);

INSERT INTO arpeggios (id, title, chord, notes, difficulty, active, sort_order)
VALUES
    ('arp_c_major', 'Dó maior (C)', 'C', '[{"string":5,"fret":3},{"string":3,"fret":0},{"string":2,"fret":1},{"string":1,"fret":0}]'::jsonb, 'easy', TRUE, 1),
    ('arp_d_major', 'Ré maior (D)', 'D', '[{"string":4,"fret":0},{"string":3,"fret":2},{"string":2,"fret":3},{"string":1,"fret":2}]'::jsonb, 'easy', TRUE, 2),
    ('arp_e_major', 'Mi maior (E)', 'E', '[{"string":6,"fret":0},{"string":3,"fret":1},{"string":2,"fret":0},{"string":1,"fret":0}]'::jsonb, 'easy', TRUE, 3),
    ('arp_f_major', 'Fá maior (F)', 'F', '[{"string":6,"fret":1},{"string":3,"fret":2},{"string":2,"fret":1},{"string":1,"fret":1}]'::jsonb, 'easy', TRUE, 4),
    ('arp_g_major', 'Sol maior (G)', 'G', '[{"string":6,"fret":3},{"string":3,"fret":0},{"string":2,"fret":0},{"string":1,"fret":3}]'::jsonb, 'easy', TRUE, 5),
    ('arp_a_major', 'Lá maior (A)', 'A', '[{"string":5,"fret":0},{"string":3,"fret":2},{"string":2,"fret":2},{"string":1,"fret":0}]'::jsonb, 'easy', TRUE, 6),
    ('arp_b_major', 'Si maior (B)', 'B', '[{"string":5,"fret":2},{"string":3,"fret":4},{"string":2,"fret":4},{"string":1,"fret":2}]'::jsonb, 'easy', TRUE, 7)
ON CONFLICT (id) DO NOTHING;
