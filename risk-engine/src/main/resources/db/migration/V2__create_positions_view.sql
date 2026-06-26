CREATE TABLE positions_view (
    trader_id       UUID NOT NULL,
    symbol          VARCHAR(20) NOT NULL,
    quantity        NUMERIC(18,8) NOT NULL,
    avg_cost        NUMERIC(18,8) NOT NULL,
    unrealised_pnl  NUMERIC(18,8),
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (trader_id, symbol)
);
