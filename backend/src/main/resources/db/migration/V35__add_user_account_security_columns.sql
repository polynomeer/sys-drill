-- docs/COMMERCIALIZATION.md — email verification badge and signup consent
-- record. Both default to values that keep every existing row's semantics
-- unchanged (unverified, no recorded consent) rather than retroactively
-- claiming either happened.
alter table users add column email_verified boolean not null default false;
alter table users add column terms_accepted_at timestamptz;
