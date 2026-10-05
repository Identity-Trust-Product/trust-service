-- Create the database separately before starting trust-service:
--   CREATE DATABASE trustdb;
--
-- This schema.sql is executed after trust-service connects to trustdb.
-- Do not include CREATE DATABASE here, because Spring SQL init runs inside
-- an already-selected database connection.

create extension if not exists pgcrypto;

create table if not exists trust_signal_event (
  id uuid primary key default gen_random_uuid(),
  subject_type varchar(64) not null,
  subject_id varchar(256) not null,
  organization_id varchar(128),
  application_id varchar(128),
  session_id varchar(128),
  signal_type varchar(128) not null,
  outcome varchar(64),
  weight numeric(8,2),
  source varchar(128),
  metadata jsonb not null default '{}'::jsonb,
  occurred_at timestamp not null default current_timestamp,
  created_at timestamp not null default current_timestamp
);

create index if not exists idx_trust_signal_subject_time
  on trust_signal_event(subject_type, subject_id, occurred_at desc);

create index if not exists idx_trust_signal_application_subject
  on trust_signal_event(application_id, subject_id, occurred_at desc);

create table if not exists trust_score_snapshot (
  id uuid primary key default gen_random_uuid(),
  subject_type varchar(64) not null,
  subject_id varchar(256) not null,
  organization_id varchar(128),
  application_id varchar(128),
  identity_score numeric(8,2) not null,
  behaviour_score numeric(8,2) not null,
  risk_penalty numeric(8,2) not null,
  final_score numeric(8,2) not null,
  trust_level varchar(64) not null,
  decision varchar(64) not null,
  reasons jsonb not null default '[]'::jsonb,
  calculated_at timestamp not null default current_timestamp
);

create index if not exists idx_trust_snapshot_subject_time
  on trust_score_snapshot(subject_type, subject_id, calculated_at desc);

create index if not exists idx_trust_snapshot_application_subject
  on trust_score_snapshot(application_id, subject_id, calculated_at desc);

create table if not exists trust_decision_history (
  id uuid primary key default gen_random_uuid(),
  subject_type varchar(64) not null,
  subject_id varchar(256) not null,
  organization_id varchar(128),
  application_id varchar(128),
  decision varchar(64) not null,
  final_score numeric(8,2) not null,
  reasons jsonb not null default '[]'::jsonb,
  created_at timestamp not null default current_timestamp
);

create index if not exists idx_trust_decision_subject_time
  on trust_decision_history(subject_type, subject_id, created_at desc);

create index if not exists idx_trust_decision_application_subject
  on trust_decision_history(application_id, subject_id, created_at desc);
