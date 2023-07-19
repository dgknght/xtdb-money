create table commodities (
    id serial,
    entity_id not null references entities (id),
    name varchar(100),
    symbol varchar(10),
    type varchar(20),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    primary key (id)
);

create index uk_commodities_entity_id_type_symbol on commodities(entity_id, type, symbol);
