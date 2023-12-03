create table users (
    id serial,
    email character varying(100) NOT NULL,
    given_name character varying(100) NOT NULL,
    surname character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    primary key (id)
);
create unique index uk_users_email on users(email);

alter table entities add column user_id int not null references users(id) on delete cascade;
create index ix_entities_user_id on entities(user_id);
