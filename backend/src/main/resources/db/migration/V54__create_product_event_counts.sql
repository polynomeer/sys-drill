-- docs/CODECRAFTERS_BENCHMARK.md §6 (PLAN.md Round B17) — success metrics that need a page
-- event (overview → start conversion, visits to formerly hidden pages). Deliberately just
-- one counter per (day, event name): no user id, no session id, no path parameters, no IP.
-- It answers "how many" without ever building a per-person behaviour log. Event names are
-- an allow-list in ProductEventService.
create table product_event_counts (
    day date not null,
    name varchar(64) not null,
    count bigint not null default 0,
    primary key (day, name)
);
