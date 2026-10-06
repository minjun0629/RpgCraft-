-- VersaEra V1: 기본 스키마. 이미 적용된 이 파일은 고치지 않는다 (변경은 V2 이후 새 파일로).

CREATE TABLE player_profile (
    uuid          TEXT PRIMARY KEY,
    name          TEXT NOT NULL,
    first_seen    INTEGER NOT NULL,
    last_seen     INTEGER NOT NULL,
    level         INTEGER NOT NULL DEFAULT 1 CHECK (level >= 1),
    exp           INTEGER NOT NULL DEFAULT 0 CHECK (exp >= 0),
    version       INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE wallet (
    uuid     TEXT PRIMARY KEY,
    balance  INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0)
);

CREATE TABLE ledger (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    from_uuid        TEXT,
    to_uuid          TEXT,
    amount           INTEGER NOT NULL CHECK (amount > 0),
    reason           TEXT NOT NULL,
    idempotency_key  TEXT UNIQUE,
    created_at       INTEGER NOT NULL
);

CREATE TABLE item_instance (
    id              TEXT PRIMARY KEY,
    type_id         TEXT NOT NULL,
    quality         INTEGER NOT NULL CHECK (quality BETWEEN 0 AND 1000),
    durability      INTEGER NOT NULL CHECK (durability >= 0),
    max_durability  INTEGER NOT NULL CHECK (max_durability >= 0),
    weight          INTEGER NOT NULL CHECK (weight >= 0),
    creator_uuid    TEXT,
    creator_name    TEXT,
    method          TEXT NOT NULL,
    props           TEXT NOT NULL DEFAULT '{}',
    custody_kind    TEXT NOT NULL CHECK (custody_kind IN ('PLAYER', 'ESCROW', 'DELIVERY', 'DESTROYED')),
    custody_ref     TEXT,
    created_at      INTEGER NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_item_custody ON item_instance (custody_kind, custody_ref);

CREATE TABLE item_history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    item_id     TEXT NOT NULL REFERENCES item_instance (id),
    event       TEXT NOT NULL,
    actor_uuid  TEXT,
    detail      TEXT,
    created_at  INTEGER NOT NULL
);
CREATE INDEX idx_item_history_item ON item_history (item_id);

CREATE TABLE mastery (
    uuid        TEXT NOT NULL,
    discipline  TEXT NOT NULL,
    xp          INTEGER NOT NULL DEFAULT 0 CHECK (xp >= 0),
    PRIMARY KEY (uuid, discipline)
);

CREATE TABLE action_counter (
    uuid   TEXT NOT NULL,
    key    TEXT NOT NULL,
    value  INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, key)
);

CREATE TABLE discovery (
    uuid        TEXT NOT NULL,
    kind        TEXT NOT NULL,
    ref         TEXT NOT NULL,
    created_at  INTEGER NOT NULL,
    PRIMARY KEY (uuid, kind, ref)
);

CREATE TABLE world_first (
    kind        TEXT NOT NULL,
    ref         TEXT NOT NULL,
    uuid        TEXT NOT NULL,
    name        TEXT NOT NULL,
    created_at  INTEGER NOT NULL,
    PRIMARY KEY (kind, ref)
);

CREATE TABLE npc_relation (
    uuid        TEXT NOT NULL,
    npc_id      TEXT NOT NULL,
    affinity    INTEGER NOT NULL DEFAULT 0,
    last_talk   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, npc_id)
);

CREATE TABLE trade (
    id          TEXT PRIMARY KEY,
    a_uuid      TEXT NOT NULL,
    b_uuid      TEXT NOT NULL,
    state       TEXT NOT NULL CHECK (state IN ('OPEN', 'COMMITTED', 'CANCELLED')),
    a_money     INTEGER NOT NULL DEFAULT 0 CHECK (a_money >= 0),
    b_money     INTEGER NOT NULL DEFAULT 0 CHECK (b_money >= 0),
    created_at  INTEGER NOT NULL,
    closed_at   INTEGER
);

-- 거래에 올린(ESCROW) 아이템의 원래 주인 — 서버가 꺼져도 돌려줄 수 있게
CREATE TABLE trade_offer (
    item_id     TEXT PRIMARY KEY REFERENCES item_instance (id),
    trade_id    TEXT NOT NULL REFERENCES trade (id),
    owner_uuid  TEXT NOT NULL
);

-- 인벤토리로 배달할 묶음 재료 (제작 실패 환불 · 채집 보상 등). 고유 아이템은 item_instance.custody = DELIVERY 로 배달
CREATE TABLE delivery_bulk (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid        TEXT NOT NULL,
    type_id     TEXT NOT NULL,
    quality     INTEGER NOT NULL CHECK (quality BETWEEN 0 AND 1000),
    amount      INTEGER NOT NULL CHECK (amount > 0),
    reason      TEXT NOT NULL,
    created_at  INTEGER NOT NULL
);
CREATE INDEX idx_delivery_bulk_uuid ON delivery_bulk (uuid);

CREATE TABLE hidden_unlock (
    uuid        TEXT NOT NULL,
    rule_id     TEXT NOT NULL,
    created_at  INTEGER NOT NULL,
    PRIMARY KEY (uuid, rule_id)
);

CREATE TABLE audit_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    action      TEXT NOT NULL,
    actor_uuid  TEXT,
    target      TEXT,
    detail      TEXT,
    request_id  TEXT,
    created_at  INTEGER NOT NULL
);
CREATE INDEX idx_audit_action ON audit_log (action, created_at);
