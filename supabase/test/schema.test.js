// Runs supabase/schema.sql against an in-memory Postgres (PGlite) with small
// stand-ins for the parts Supabase provides (auth, storage, roles), then checks
// search ranking, counters and access rules.
//
//   cd supabase/test && npm install && npm test

import { test, before } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { PGlite } from '@electric-sql/pglite'
import { pg_trgm } from '@electric-sql/pglite/contrib/pg_trgm'

const schema = await readFile(new URL('../schema.sql', import.meta.url), 'utf8')
const db = new PGlite({ extensions: { pg_trgm } })

const ADMIN = '11111111-1111-1111-1111-111111111111'
const VISITOR = '22222222-2222-2222-2222-222222222222'

const SUPABASE_STANDINS = `
  create role anon nologin;
  create role authenticated nologin;
  create schema extensions;
  grant usage on schema extensions to anon, authenticated;
  create schema auth;
  grant usage on schema auth to anon, authenticated;
  create table auth.users (id uuid primary key, email text);
  create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
  $$;
  create schema storage;
  grant usage on schema storage to anon, authenticated;
  create table storage.buckets (
    id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]
  );
  create table storage.objects (id uuid primary key default gen_random_uuid(), bucket_id text, name text);
  alter table storage.objects enable row level security;
  grant select, insert, update, delete on storage.objects to authenticated;
`

async function as(role, userId, fn) {
  await db.exec(`set role ${role}; select set_config('request.jwt.claim.sub', '${userId ?? ''}', false);`)
  try {
    return await fn()
  } finally {
    await db.exec(`reset role; select set_config('request.jwt.claim.sub', '', false);`)
  }
}

async function search(q) {
  const { rows } = await as('anon', null, () =>
    db.query('select slug, search_rank from public.search_clips($1)', [q]),
  )
  return rows.map((r) => `${r.slug}:${r.search_rank}`)
}

before(async () => {
  await db.exec(SUPABASE_STANDINS)
  await db.exec(schema)
  await db.exec(schema) // must be safe to run twice

  await db.exec(`
    insert into auth.users (id, email) values
      ('${ADMIN}', 'admin@example.com'), ('${VISITOR}', 'visitor@example.com');
    insert into public.admins (user_id) values ('${ADMIN}');

    insert into public.clips (slug, title, category, tags, mp4_url, mp3_url, send_count, status) values
      ('leave-me-alone', 'Leave me alone', 'Leave me alone',
        '{comot,go away,abeg leave me,leave here}', 'u', 'u', 5, 'live'),
      ('comot-for-road', 'Comot for my road', 'Vex', '{comot,commot,move}', 'u', 'u', 50, 'live'),
      ('geh-geh', 'Geh geh geh', 'Shock', '{geh,gehgeh,shock}', 'u', 'u', 10, 'live'),
      ('abeg-laugh', 'Abeg make I laugh', 'Laughing', '{laugh,lmao,laff}', 'u', 'u', 100, 'live'),
      ('leave-me-remix', 'Leave me alone remix', 'Leave me alone', '{leave me alone}', 'u', 'u', 999, 'removed');
  `)
})

test('seeds the eight starter categories once', async () => {
  const { rows } = await db.query('select name from public.categories order by sort_order')
  assert.deepEqual(rows.map((r) => r.name), [
    'Laughing', 'Vex', 'Shock', 'Leave me alone', 'Praise', 'Broke', 'Love', 'Sarcasm',
  ])
})

test('normalize_search squashes punctuation, apostrophes and stretched letters', async () => {
  const cases = {
    'Gehhhh!!': 'geh',
    "Don't  VEX o": 'dont vex o',
    'Chaiii... wahala': 'chai wahala',
    'leave-me-alone': 'leave me alone',
    'good food': 'good food', // two of a letter stays
  }
  for (const [input, expected] of Object.entries(cases)) {
    const { rows } = await db.query('select public.normalize_search($1) as n', [input])
    assert.equal(rows[0].n, expected, input)
  }
})

test('exact tag match ranks first, then most sent', async () => {
  assert.deepEqual(await search('comot'), ['comot-for-road:0', 'leave-me-alone:0'])
})

test('exact title match counts, removed clips never show', async () => {
  assert.deepEqual(await search('Leave me alone'), ['leave-me-alone:0'])
})

test('partial words match', async () => {
  assert.deepEqual(await search('LEAVE ME'), ['leave-me-alone:1'])
  assert.deepEqual(await search('laug'), ['abeg-laugh:1'])
})

test('words in any order match', async () => {
  assert.deepEqual(await search('alone leave'), ['leave-me-alone:2'])
})

test('stretched and alternative spellings match', async () => {
  assert.deepEqual(await search('gehhhhh'), ['geh-geh:0'])
  assert.deepEqual((await search('commot')).slice(0, 1), ['comot-for-road:0'])
  assert.ok((await search('commot')).includes('leave-me-alone:3'))
  assert.deepEqual(await search('leve me alone'), ['leave-me-alone:3'])
})

test('empty query returns nothing', async () => {
  assert.deepEqual(await search('   '), [])
  assert.deepEqual(await search('!!!'), [])
})

test('the public app sees only live clips and cannot edit them', async () => {
  const visible = await as('anon', null, () => db.query('select slug from public.clips order by slug'))
  assert.deepEqual(visible.rows.map((r) => r.slug), ['abeg-laugh', 'comot-for-road', 'geh-geh', 'leave-me-alone'])

  const updated = await as('anon', null, () =>
    db.query("update public.clips set title = 'hacked' where slug = 'geh-geh' returning id"),
  ).catch((e) => e)
  assert.ok(updated instanceof Error || updated.rows.length === 0)
})

test('the public app can bump counters on live clips only', async () => {
  await as('anon', null, () =>
    db.query("select public.increment_send(id) from public.clips where slug = 'geh-geh'"),
  )
  await as('anon', null, () =>
    db.query("select public.increment_play(id) from public.clips where slug = 'geh-geh'"),
  )
  const removedId = (await db.query("select id from public.clips where slug = 'leave-me-remix'")).rows[0].id
  await as('anon', null, () => db.query('select public.increment_send($1)', [removedId]))

  const { rows } = await db.query(
    "select slug, send_count, play_count from public.clips where slug in ('geh-geh', 'leave-me-remix') order by slug",
  )
  assert.deepEqual(rows, [
    { slug: 'geh-geh', send_count: 11, play_count: 1 },
    { slug: 'leave-me-remix', send_count: 999, play_count: 0 },
  ])
})

test('anyone can file a report but only admins can read them', async () => {
  const clipId = (await db.query("select id from public.clips where slug = 'abeg-laugh'")).rows[0].id
  await as('anon', null, () =>
    db.query('insert into public.reports (clip_id, reason, contact_email) values ($1, $2, $3)', [
      clipId, 'I own this sound', 'owner@example.com',
    ]),
  )
  // Depending on grants this is either "permission denied" or zero rows.
  const anonRead = await as('anon', null, () => db.query('select * from public.reports')).catch(() => ({ rows: [] }))
  assert.equal(anonRead.rows.length, 0)

  const visitorRead = await as('authenticated', VISITOR, () => db.query('select * from public.reports'))
  assert.equal(visitorRead.rows.length, 0)

  const adminRead = await as('authenticated', ADMIN, () => db.query('select reason from public.reports'))
  assert.deepEqual(adminRead.rows, [{ reason: 'I own this sound' }])

  const sneaky = await as('anon', null, () =>
    db.query('insert into public.reports (clip_id, reason, resolved) values ($1, $2, true)', [clipId, 'x']),
  ).catch((e) => e)
  assert.ok(sneaky instanceof Error)
})

test('admins see removed clips, can edit, pin and upload files', async () => {
  const admin = await as('authenticated', ADMIN, async () => {
    const isAdmin = (await db.query('select public.is_admin() as ok')).rows[0].ok
    const all = (await db.query('select count(*)::int as n from public.clips')).rows[0].n
    await db.query("update public.clips set pinned = true, tags = '{geh,omg}' where slug = 'geh-geh'")
    await db.query("insert into storage.objects (bucket_id, name) values ('clips', 'mp4/geh-geh.mp4')")
    return { isAdmin, all }
  })
  assert.deepEqual(admin, { isAdmin: true, all: 5 })

  const { rows } = await db.query("select pinned, search_text from public.clips where slug = 'geh-geh'")
  assert.deepEqual(rows, [{ pinned: true, search_text: 'geh geh geh geh omg' }])
})

test('signed-in non-admins cannot change anything', async () => {
  const result = await as('authenticated', VISITOR, async () => {
    const isAdmin = (await db.query('select public.is_admin() as ok')).rows[0].ok
    const upd = await db.query("update public.clips set pinned = true where slug = 'abeg-laugh' returning id")
    const upload = await db
      .query("insert into storage.objects (bucket_id, name) values ('clips', 'mp4/evil.mp4')")
      .catch((e) => e)
    return { isAdmin, updated: upd.rows.length, uploadFailed: upload instanceof Error }
  })
  assert.deepEqual(result, { isAdmin: false, updated: 0, uploadFailed: true })
})

test('the clips bucket is public with size and type limits', async () => {
  const { rows } = await db.query('select public, file_size_limit, allowed_mime_types from storage.buckets')
  assert.deepEqual(rows, [
    { public: true, file_size_limit: 10485760, allowed_mime_types: ['video/mp4', 'audio/mpeg', 'image/jpeg'] },
  ])
})
