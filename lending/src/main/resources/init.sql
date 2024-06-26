CREATE TABLE IF NOT EXISTS book (
  id SERIAL PRIMARY KEY,
  book_id VARCHAR(100) UNIQUE,
  book_type VARCHAR(100) NOT NULL,
  book_state VARCHAR(100) NOT NULL,
  by_patron VARCHAR(100),
  on_hold_till TIMESTAMPTZ,
  version INTEGER);

CREATE TABLE IF NOT EXISTS patron (
  id SERIAL PRIMARY KEY,
  patron_type VARCHAR(100) NOT NULL,
  patron_id VARCHAR(100) UNIQUE);

CREATE TABLE IF NOT EXISTS hold (
  id SERIAL PRIMARY KEY,
  book_id VARCHAR(100) NOT NULL,
  patron_id VARCHAR(100) NOT NULL,
  patron INTEGER NOT NULL,
  till TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_hold_patron FOREIGN KEY(patron) REFERENCES patron(id));

CREATE TABLE IF NOT EXISTS overdue_checkout (
  id SERIAL PRIMARY KEY,
  book_id VARCHAR(100) NOT NULL,
  patron_id VARCHAR(100) NOT NULL,
  patron INTEGER NOT NULL,
  CONSTRAINT fk_overdue_checkout_patron FOREIGN KEY(patron) REFERENCES patron(id));

CREATE TABLE IF NOT EXISTS checkouts_sheet (
  id SERIAL PRIMARY KEY,
  book_id VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  checkout_event_id VARCHAR(100) UNIQUE,
  checked_out_by_patron_id VARCHAR(100),
  checked_out_at TIMESTAMPTZ,
  returned_at TIMESTAMPTZ,
  checkout_till TIMESTAMPTZ);


CREATE TABLE IF NOT EXISTS holds_sheet (
  id SERIAL PRIMARY KEY,
  book_id VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  hold_event_id VARCHAR(100) UNIQUE,
  hold_by_patron_id VARCHAR(100),
  hold_at TIMESTAMPTZ,
  hold_till TIMESTAMPTZ,
  expired_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  checked_out_at TIMESTAMPTZ);
