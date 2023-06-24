CREATE TABLE entities (
    id serial,
    name character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    primary key (id)
);
create unique index uk_entities_name on entities(name);
