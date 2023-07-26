create table prices (
    id serial,
    commodity_id int not null references commodities (id),
    trade_date int not null,
    value  numeric(12, 4) not null,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    primary key (id)
);

create index uk_prices_commodity_id_trade_date on prices (commodity_id, trade_date);
