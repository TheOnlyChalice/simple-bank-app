-- =====================================================================
-- Simple Bank Application - database schema (MySQL 8.4)
--
-- One-time setup, run as root:
--   CREATE DATABASE simple_bank;
--   CREATE USER 'bankapp'@'localhost' IDENTIFIED BY '<password>';
--   GRANT ALL PRIVILEGES ON simple_bank.* TO 'bankapp'@'localhost';
--
-- Then run this script as bankapp, from the mysql prompt:
--   SOURCE database/schema.sql;
--
-- WARNING: this drops and recreates the tables, deleting all data.
-- =====================================================================

USE simple_bank;

-- Drop in reverse order of dependencies (children before parents)
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS users;

-- ---------------------------------------------------------------------
-- USERS: one row per customer
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id     BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    email       VARCHAR(100)  NOT NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---------------------------------------------------------------------
-- ACCOUNTS: one user can have many accounts
-- ---------------------------------------------------------------------
CREATE TABLE accounts (
    account_id    BIGINT         NOT NULL AUTO_INCREMENT,
    user_id       BIGINT         NOT NULL,
    balance       DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    account_type  VARCHAR(50)    NOT NULL,
    created_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_accounts PRIMARY KEY (account_id),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_accounts_type CHECK (account_type IN ('SAVINGS', 'CHECKING'))
);

-- ---------------------------------------------------------------------
-- TRANSACTIONS: one row per deposit or withdrawal
-- ---------------------------------------------------------------------
CREATE TABLE transactions (
    txn_id      BIGINT         NOT NULL AUTO_INCREMENT,
    account_id  BIGINT         NOT NULL,
    txn_type    VARCHAR(20)    NOT NULL,
    amount      DECIMAL(10,2)  NOT NULL,
    created_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_transactions PRIMARY KEY (txn_id),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (txn_type IN ('DEPOSIT', 'WITHDRAW'))
);

-- Speeds up "transaction history for account X, newest first"
CREATE INDEX idx_transactions_account ON transactions (account_id, txn_id);
