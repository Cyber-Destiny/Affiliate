// Turns clips.csv plus a pile of MP4/MP3 files into a list of clips to import.
// Kept free of browser and Supabase code so it can be tested with `npm test`.

export type CsvRow = Record<string, string | undefined>

export interface PlanItem {
  /** CSV line number (header is line 1). */
  line: number
  slug: string
  title: string
  category: string
  tags: string[]
  mp4?: string
  mp3?: string
  problems: string[]
}

export interface ImportPlan {
  items: PlanItem[]
  /** Media files with no CSV row. */
  unusedFiles: string[]
}

/** "Leave Me Alone (1).MP4" → "leave-me-alone-1". */
export function slugFromFilename(name: string): string {
  const base = name.split(/[\\/]/).pop() ?? ''
  return base
    .replace(/\.(mp4|mp3|m4a|wav|csv)$/i, '')
    .toLowerCase()
    .replace(/['’]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

/** "leave me alone, comot; go away | abeg" → four tags, no duplicates. */
export function splitTags(raw: string | undefined): string[] {
  const seen = new Set<string>()
  const tags: string[] = []
  for (const part of (raw ?? '').split(/[,;|]/)) {
    const tag = part.trim().replace(/\s+/g, ' ')
    const key = tag.toLowerCase()
    if (!tag || seen.has(key)) continue
    seen.add(key)
    tags.push(tag)
  }
  return tags.slice(0, 20)
}

/** Finds the real category name, ignoring case, spaces and hyphens. */
export function matchCategory(raw: string | undefined, categories: string[]): string | null {
  const squash = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, '')
  const wanted = squash(raw ?? '')
  if (!wanted) return null
  return categories.find((c) => squash(c) === wanted) ?? null
}

function titleFromSlug(slug: string): string {
  const words = slug.split('-').filter(Boolean).join(' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export function planImport(rows: CsvRow[], files: string[], categories: string[]): ImportPlan {
  const media = new Map<string, { mp4?: string; mp3?: string }>()
  for (const file of files) {
    const ext = file.toLowerCase().split('.').pop()
    if (ext !== 'mp4' && ext !== 'mp3') continue
    const slug = slugFromFilename(file)
    const entry = media.get(slug) ?? {}
    entry[ext] = file
    media.set(slug, entry)
  }

  const used = new Set<string>()
  const items = rows.map((row, index): PlanItem => {
    const problems: string[] = []
    const slug = slugFromFilename(row.filename ?? '')
    if (!slug) problems.push('No filename')

    const category = matchCategory(row.category, categories)
    if (!category) problems.push(row.category?.trim() ? `Unknown category “${row.category.trim()}”` : 'No category')

    if (slug && used.has(slug)) problems.push('Same filename used twice in the CSV')
    const files = media.get(slug)
    if (slug && !files?.mp4) problems.push(`Missing ${slug}.mp4`)
    if (slug && !files?.mp3) problems.push(`Missing ${slug}.mp3`)
    if (slug) used.add(slug)

    return {
      line: index + 2,
      slug,
      title: (row.title ?? '').trim() || titleFromSlug(slug),
      category: category ?? (row.category ?? '').trim(),
      tags: splitTags(row.tags),
      mp4: files?.mp4,
      mp3: files?.mp3,
      problems,
    }
  })

  const unusedFiles = [...media.entries()]
    .filter(([slug]) => !used.has(slug))
    .flatMap(([, f]) => [f.mp4, f.mp3].filter((x): x is string => Boolean(x)))
    .sort()

  return { items, unusedFiles }
}

/** Lowercases headers and accepts "file"/"name" for filename. */
export function normaliseHeader(header: string): string {
  const h = header.trim().toLowerCase().replace(/\s+/g, '_')
  if (h === 'file' || h === 'file_name' || h === 'name') return 'filename'
  return h
}
