CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    address_line TEXT NOT NULL,
    date_of_birth DATE NOT NULL,
    state VARCHAR(32) NOT NULL,
    verification_code VARCHAR(64) NOT NULL,
    verification_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS stocks (
    id UUID PRIMARY KEY,
    symbol VARCHAR(16) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    last_price NUMERIC(19, 4) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS account_followed_stocks (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    stock_id UUID NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    followed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, stock_id)
);

CREATE TABLE IF NOT EXISTS account_audit (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    action VARCHAR(64) NOT NULL,
    metadata TEXT NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO stocks (id, symbol, name, description, last_price)
SELECT '11111111-1111-1111-1111-111111111111', 'AAPL', 'Apple Inc.', 'Technologie grand public et services numeriques.', 185.32
WHERE NOT EXISTS (SELECT 1 FROM stocks WHERE symbol = 'AAPL');

INSERT INTO stocks (id, symbol, name, description, last_price)
SELECT '22222222-2222-2222-2222-222222222222', 'GOOGL', 'Alphabet Inc.', 'Maison mere de Google, specialisee dans la recherche et le cloud.', 142.11
WHERE NOT EXISTS (SELECT 1 FROM stocks WHERE symbol = 'GOOGL');

INSERT INTO stocks (id, symbol, name, description, last_price)
SELECT '33333333-3333-3333-3333-333333333333', 'TSLA', 'Tesla, Inc.', 'Constructeur de vehicules electriques et solutions energetiques.', 209.45
WHERE NOT EXISTS (SELECT 1 FROM stocks WHERE symbol = 'TSLA');

INSERT INTO stocks (id, symbol, name, description, last_price)
SELECT '44444444-4444-4444-4444-444444444444', 'AMZN', 'Amazon.com, Inc.', 'Plateforme e-commerce et services cloud (AWS).', 129.63
WHERE NOT EXISTS (SELECT 1 FROM stocks WHERE symbol = 'AMZN');

INSERT INTO stocks (id, symbol, name, description, last_price)
SELECT '55555555-5555-5555-5555-555555555555', 'SHOP', 'Shopify Inc.', 'Solutions SaaS pour commerçants et vente en ligne (Canada).', 68.27
WHERE NOT EXISTS (SELECT 1 FROM stocks WHERE symbol = 'SHOP');

CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount NUMERIC(19, 4) NOT NULL,
    type VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    stock_id UUID NOT NULL REFERENCES stocks(id),
    symbol VARCHAR(16) NOT NULL,
    side VARCHAR(16) NOT NULL,
    type VARCHAR(16) NOT NULL,
    quantity INTEGER NOT NULL,
    limit_price NUMERIC(19, 4),
    executed_price NUMERIC(19, 4),
    notional NUMERIC(19, 4),
    client_order_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    executed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_client_unique
    ON orders (account_id, client_order_id);

CREATE INDEX IF NOT EXISTS idx_orders_account_created
    ON orders (account_id, created_at);

CREATE INDEX IF NOT EXISTS idx_orders_stock_pending
    ON orders (stock_id, status);

CREATE TABLE IF NOT EXISTS positions (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    stock_id UUID NOT NULL REFERENCES stocks(id),
    quantity NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    average_price NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (average_price >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, stock_id)
);

CREATE INDEX IF NOT EXISTS idx_positions_account
    ON positions (account_id);

CREATE TABLE IF NOT EXISTS order_audit (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_audit_order
    ON order_audit (order_id, created_at);


