CREATE TABLE accounts (
    id serial,
    entity_id int NOT NULL references entities (id) on delete cascade,
    type character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    balance numeric(12, 4) NOT NULL,
    first_trx_date date,
    last_trx_date date,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    primary key (id)
);
create unique index uk_accounts_name on accounts(entity_id, name);
