DROP TABLE IF EXISTS it_users;
DROP TABLE IF EXISTS it_codes;
DROP TABLE IF EXISTS it_order_items;
DROP SCHEMA IF EXISTS it_shop CASCADE;

CREATE TABLE it_users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(100),
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP    NOT NULL
);

CREATE TABLE it_codes (
    code       VARCHAR(50) PRIMARY KEY,
    label      VARCHAR(100),
    sort_order INT
);

CREATE TABLE it_order_items (
    order_id BIGINT NOT NULL,
    item_no  INT    NOT NULL,
    qty      INT,
    memo     VARCHAR(100),
    PRIMARY KEY (order_id, item_no)
);

CREATE SCHEMA it_shop;
CREATE TABLE it_shop.it_products (
    id     SERIAL PRIMARY KEY,
    name   VARCHAR(100),
    price  INT,
    active BOOLEAN
);
