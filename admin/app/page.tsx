'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchAll, supabase } from '@/lib/supabase'
import { splitTags } from '@/lib/csv'
import type { Category, Clip } from '@/lib/types'

type StatusFilter = 'live' | 'removed' | 'all'

export default function ClipsPage() {
  const [clips, setClips] = useState<Clip[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [text, setText] = useState('')
  const [category, setCategory] = useState('')
  const [status, setStatus] = useState<StatusFilter>('live')
  const [pinnedOnly, setPinnedOnly] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const client = supabase()
      const [rows, cats] = await Promise.all([
        fetchAll<Clip>((from, to) =>
          client
            .from('clips')
            .select('*')
            .order('pinned', { ascending: false })
            .order('created_at', { ascending: false })
            .range(from, to),
        ),
        client.from('categories').select('*').order('sort_order'),
      ])
      if (cats.error) throw new Error(cats.error.message)
      setClips(rows)
      setCategories(cats.data ?? [])
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const visible = useMemo(() => {
    const needle = text.trim().toLowerCase()
    return clips.filter(
      (clip) =>
        (status === 'all' || clip.status === status) &&
        (!category || clip.category === category) &&
        (!pinnedOnly || clip.pinned) &&
        (!needle ||
          clip.title.toLowerCase().includes(needle) ||
          clip.slug.includes(needle) ||
          clip.tags.some((tag) => tag.toLowerCase().includes(needle))),
    )
  }, [clips, text, category, status, pinnedOnly])

  const replace = (updated: Clip) => setClips((all) => all.map((c) => (c.id === updated.id ? updated : c)))

  const live = clips.filter((c) => c.status === 'live').length

  return (
    <>
      <div className="page-head">
        <h1>Clips</h1>
        <span className="muted">
          {live} live · {clips.length - live} removed · {clips.filter((c) => c.pinned).length} pinned to Trending
        </span>
      </div>

      <div className="filters">
        <input placeholder="Search title, tags or file name" value={text} onChange={(e) => setText(e.target.value)} />
        <select value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.name}>
              {c.icon} {c.name}
            </option>
          ))}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value as StatusFilter)}>
          <option value="live">Live</option>
          <option value="removed">Removed</option>
          <option value="all">All</option>
        </select>
        <label className="inline">
          <input type="checkbox" checked={pinnedOnly} onChange={(e) => setPinnedOnly(e.target.checked)} /> Pinned only
        </label>
        <button className="ghost" onClick={load}>
          Refresh
        </button>
      </div>

      {error && <p className="error">{error}</p>}
      {loading ? (
        <p className="muted">Loading clips…</p>
      ) : visible.length === 0 ? (
        <p className="muted">No clips match. Use Import to add some.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th />
                <th>Title</th>
                <th>Category</th>
                <th>Tags</th>
                <th>Plays / sends</th>
                <th>Preview</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {visible.map((clip) => (
                <ClipRow key={clip.id} clip={clip} categories={categories} onChange={replace} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

function ClipRow({
  clip,
  categories,
  onChange,
}: {
  clip: Clip
  categories: Category[]
  onChange: (clip: Clip) => void
}) {
  const [title, setTitle] = useState(clip.title)
  const [category, setCategory] = useState(clip.category)
  const [tags, setTags] = useState(clip.tags.join(', '))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setTitle(clip.title)
    setCategory(clip.category)
    setTags(clip.tags.join(', '))
  }, [clip])

  const dirty = title !== clip.title || category !== clip.category || tags !== clip.tags.join(', ')

  const save = async (changes: Partial<Clip>) => {
    setBusy(true)
    setError(null)
    const { data, error } = await supabase().from('clips').update(changes).eq('id', clip.id).select('*').single()
    setBusy(false)
    if (error) setError(error.message)
    else onChange(data as Clip)
  }

  return (
    <tr className={clip.status === 'removed' ? 'removed' : undefined}>
      <td>
        {clip.thumbnail_url ? (
          <img
            src={clip.thumbnail_url}
            alt=""
            className="thumb"
            loading="lazy"
            onError={(e) => (e.currentTarget.style.visibility = 'hidden')}
          />
        ) : (
          <div className="thumb" />
        )}
      </td>
      <td>
        <input value={title} onChange={(e) => setTitle(e.target.value)} aria-label="Title" />
        <div className="muted small">
          {clip.slug} · {clip.duration_sec ? `${clip.duration_sec.toFixed(1)}s` : '—'}
          {clip.pinned && ' · 📌 pinned'}
          {clip.status === 'removed' && ' · removed'}
        </div>
        {error && <div className="error small">{error}</div>}
      </td>
      <td>
        <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="Category">
          {categories.map((c) => (
            <option key={c.id} value={c.name}>
              {c.name}
            </option>
          ))}
        </select>
      </td>
      <td>
        <input value={tags} onChange={(e) => setTags(e.target.value)} aria-label="Tags, comma separated" />
      </td>
      <td className="nowrap">
        {clip.play_count} / {clip.send_count}
      </td>
      <td>
        <audio controls preload="none" src={clip.mp3_url} />
      </td>
      <td className="actions">
        {dirty && (
          <button disabled={busy} onClick={() => save({ title: title.trim(), category, tags: splitTags(tags) })}>
            Save
          </button>
        )}
        <button className="ghost" disabled={busy} onClick={() => save({ pinned: !clip.pinned })}>
          {clip.pinned ? 'Unpin' : 'Pin'}
        </button>
        {clip.status === 'live' ? (
          <button className="danger" disabled={busy} onClick={() => save({ status: 'removed' })}>
            Remove
          </button>
        ) : (
          <button className="ghost" disabled={busy} onClick={() => save({ status: 'live' })}>
            Restore
          </button>
        )}
      </td>
    </tr>
  )
}
