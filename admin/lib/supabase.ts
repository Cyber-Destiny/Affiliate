import { createClient, type SupabaseClient } from '@supabase/supabase-js'

const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? ''
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? ''

export const isConfigured = Boolean(url && key)

let client: SupabaseClient | null = null

/** One shared browser client. Every write is checked by the database's admin rules. */
export function supabase(): SupabaseClient {
  if (!client) client = createClient(url, key)
  return client
}

export const BUCKET = 'clips'

/** Supabase returns at most 1000 rows per request, so fetch in pages. */
export async function fetchAll<T>(
  page: (from: number, to: number) => PromiseLike<{ data: T[] | null; error: { message: string } | null }>,
): Promise<T[]> {
  const size = 1000
  const rows: T[] = []
  for (let from = 0; ; from += size) {
    const { data, error } = await page(from, from + size - 1)
    if (error) throw new Error(error.message)
    rows.push(...(data ?? []))
    if (!data || data.length < size) return rows
  }
}
