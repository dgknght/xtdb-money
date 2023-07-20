alter table accounts
    add column parent_id int references accounts(id);
