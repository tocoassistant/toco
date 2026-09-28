-- TOCO Beta 1.5 account/profile foundation.
-- Apply through the Supabase migration system, not from the Android client.

create table if not exists public.toco_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  toco_id text unique,
  display_name text,
  phone text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint toco_id_format check (
    toco_id is null or toco_id ~ '^[a-z0-9._-]{3,32}@toco\.io$'
  )
);

alter table public.toco_profiles enable row level security;
drop policy if exists "users own profile" on public.toco_profiles;
create policy "users own profile"
on public.toco_profiles for all
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create table if not exists public.toco_admin_users (
  user_id uuid primary key references auth.users(id) on delete cascade,
  role text not null check (role in ('developer','security_admin','support_admin')),
  created_at timestamptz not null default now()
);

alter table public.toco_admin_users enable row level security;
revoke all on table public.toco_admin_users from anon, authenticated;

create or replace function public.is_toco_admin()
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (select 1 from public.toco_admin_users a where a.user_id = auth.uid());
$$;
revoke all on function public.is_toco_admin() from public;
grant execute on function public.is_toco_admin() to authenticated;
