CREATE TABLE transactions (
    id serial,
    entity_id int NOT NULL references entities (id),
    correlation_id varchar(40),
    transaction_date date,
    description varchar(100),
    quantity numeric(12, 4),
    debit_account_id int NOT NULL references accounts (id),
    debit_index int NOT NULL,
    debit_balance numeric(12, 4) NOT NULL,
    credit_account_id int NOT NULL references accounts (id),
    credit_index int NOT NULL,
    credit_balance numeric(12, 4) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    primary key (id)
);
create index ix_transactions_entity_id on transactions(entity_id);
create index ix_transactions_debit_account_id on transactions(debit_account_id);
create index ix_transactions_credit_account_id on transactions(credit_account_id);
