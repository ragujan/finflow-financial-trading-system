CREATE TABLE orders_view (
    order_id        UUID PRIMARY KEY,
    trader_id       UUID NOT NULL,
    symbol          VARCHAR(20) NOT NULL,
    side            VARCHAR(4) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    price           NUMERIC(18,8),
    quantity        NUMERIC(18,8),
    filled_quantity NUMERIC(18,8) DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);
