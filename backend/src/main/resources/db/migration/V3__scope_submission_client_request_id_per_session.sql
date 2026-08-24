-- The idempotency boundary for a resubmitted answer is "the same client
-- retrying the same session's submission", not "this string has never been
-- used by anyone, ever". A global UNIQUE(client_request_id) makes unrelated
-- sessions collide if they happen to reuse the same key.

alter table submissions drop constraint submissions_client_request_id_key;

create unique index uk_submissions_session_client_request_id
    on submissions (session_id, client_request_id)
    where client_request_id is not null;
