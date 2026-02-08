CREATE TABLE IF NOT EXISTS users (
    username TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    pass_hash TEXT NOT NULL,
    played INTEGER NOT NULL DEFAULT 0,
    won INTEGER NOT NULL DEFAULT 0,
    drawn INTEGER NOT NULL DEFAULT 0,
    lost INTEGER NOT NULL DEFAULT 0,
    rating INTEGER NOT NULL DEFAULT 1200,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS games (
    id TEXT PRIMARY KEY,
    white_user TEXT NOT NULL,
    black_user TEXT NOT NULL,
    created_at_ms BIGINT NOT NULL,
    last_update_ms BIGINT NOT NULL,
    result TEXT NOT NULL,
    rated BOOLEAN NOT NULL,
    game_json JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_games_white_user ON games (white_user);
CREATE INDEX IF NOT EXISTS idx_games_black_user ON games (black_user);
CREATE INDEX IF NOT EXISTS idx_games_last_update ON games (last_update_ms);
