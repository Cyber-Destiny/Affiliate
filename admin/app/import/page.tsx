'use client'

import Papa from 'papaparse'
import { useEffect, useMemo, useState, type ChangeEvent, type DragEvent } from 'react'
import { normaliseHeader, planImport, type CsvRow, type PlanItem } from '@/lib/csv'
import { probeVideo, runPool } from '@/lib/media'
import { BUCKET, supabase } from '@/lib/supabase'

type ItemState = { status: 'waiting' | 'working' | 'done' | 'failed'; message?: string }

export default function ImportPage() {
  const [files, setFiles] = useState<File[]>([])
  const [rows, setRows] = useState<CsvRow[] | null>(null)
  const [csvError, setCsvError] = useState<string | null>(null)
  const [categories, setCategories] = useState<string[]>([])
  const [progress, setProgress] = useState<Record<string, ItemState>>({})
  const [running, setRunning] = useState(false)

  useEffect(() => {
    supabase()
      .from('categories')
      .select('name')
      .order('sort_order')
      .then(({ data }) => setCategories((data ?? []).map((c: { name: string }) => c.name)))
  }, [])

  const csvFile = files.find((f) => f.name.toLowerCase().endsWith('.csv'))

  useEffect(() => {
    setRows(null)
    setCsvError(null)
    setProgress({})
    if (!csvFile) return
    Papa.parse<CsvRow>(csvFile, {
      header: true,
      skipEmptyLines: 'greedy',
      transformHeader: normaliseHeader,
      complete: (result) => {
        const fields = result.meta.fields ?? []
        const missing = ['filename', 'title', 'category'].filter((f) => !fields.includes(f))
        if (missing.length) setCsvError(`clips.csv needs these columns: ${missing.join(', ')}`)
        else setRows(result.data)
      },
      error: (err) => setCsvError(err.message),
    })
  }, [csvFile])

  const plan = useMemo(
    () => (rows ? planImport(rows, files.map(pathOf), categories) : null),
    [rows, files, categories],
  )
  const ready = plan?.items.filter((item) => item.problems.length === 0) ?? []
  const byPath = useMemo(() => new Map(files.map((f) => [pathOf(f), f])), [files])

  const addFiles = (list: FileList | null) => {
    if (!list) return
    const incoming = Array.from(list).filter((f) => /\.(mp4|mp3|csv)$/i.test(f.name))
    setFiles((current) => {
      const seen = new Map(current.map((f) => [pathOf(f), f]))
      incoming.forEach((f) => seen.set(pathOf(f), f))
      return [...seen.values()]
    })
  }

  const onPick = (e: ChangeEvent<HTMLInputElement>) => {
    addFiles(e.target.files)
    e.target.value = ''
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    addFiles(e.dataTransfer.files)
  }

  const importAll = async () => {
    setRunning(true)
    const set = (slug: string, state: ItemState) => setProgress((p) => ({ ...p, [slug]: state }))
    ready.forEach((item) => set(item.slug, { status: 'waiting' }))
    await runPool(ready, 3, async (item) => {
      set(item.slug, { status: 'working', message: 'Uploading…' })
      try {
        await importOne(item, byPath)
        set(item.slug, { status: 'done' })
      } catch (e) {
        set(item.slug, { status: 'failed', message: e instanceof Error ? e.message : String(e) })
      }
    })
    setRunning(false)
  }

  const done = Object.values(progress).filter((p) => p.status === 'done').length
  const failed = Object.values(progress).filter((p) => p.status === 'failed').length

  return (
    <>
      <div className="page-head">
        <h1>Import clips</h1>
      </div>
      <p className="muted">
        Pick the folder with your <code>mp4/</code> and <code>mp3/</code> files and <code>clips.csv</code> (columns:
        filename, title, category, tags). Clips with a file name that already exists are updated, not duplicated.
      </p>

      <div className="dropzone" onDragOver={(e) => e.preventDefault()} onDrop={onDrop}>
        <p>Drop files here, or</p>
        <div className="row">
          <label className="button">
            Choose folder
            <input type="file" hidden onChange={onPick} {...{ webkitdirectory: '', directory: '' }} />
          </label>
          <label className="button ghost">
            Choose files
            <input type="file" hidden multiple accept=".mp4,.mp3,.csv" onChange={onPick} />
          </label>
          {files.length > 0 && (
            <button className="ghost" disabled={running} onClick={() => setFiles([])}>
              Clear
            </button>
          )}
        </div>
        <p className="muted small">
          {files.length} files · {files.filter((f) => /\.mp4$/i.test(f.name)).length} MP4 ·{' '}
          {files.filter((f) => /\.mp3$/i.test(f.name)).length} MP3 · {csvFile ? csvFile.name : 'no CSV yet'}
        </p>
      </div>

      {csvError && <p className="error">{csvError}</p>}

      {plan && (
        <>
          <div className="page-head">
            <h2>
              {ready.length} ready · {plan.items.length - ready.length} with problems
            </h2>
            <button disabled={running || ready.length === 0} onClick={importAll}>
              {running ? `Importing… ${done + failed}/${ready.length}` : `Import ${ready.length} clips`}
            </button>
          </div>
          {(done > 0 || failed > 0) && !running && (
            <p className={failed ? 'error' : 'success'}>
              Imported {done} clip{done === 1 ? '' : 's'}
              {failed > 0 && `, ${failed} failed — see below and try again`}.
            </p>
          )}
          {plan.unusedFiles.length > 0 && (
            <p className="muted small">Not in the CSV (skipped): {plan.unusedFiles.join(', ')}</p>
          )}
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Line</th>
                  <th>File</th>
                  <th>Title</th>
                  <th>Category</th>
                  <th>Tags</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {plan.items.map((item) => {
                  const state = progress[item.slug]
                  return (
                    <tr key={`${item.line}-${item.slug}`} className={item.problems.length ? 'problem' : undefined}>
                      <td>{item.line}</td>
                      <td>{item.slug || '—'}</td>
                      <td>{item.title}</td>
                      <td>{item.category}</td>
                      <td className="small">{item.tags.join(', ')}</td>
                      <td className="small">
                        {item.problems.length > 0
                          ? item.problems.join('; ')
                          : state
                            ? `${label(state.status)}${state.message && state.status !== 'done' ? ` — ${state.message}` : ''}`
                            : 'Ready'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  )
}

function label(status: ItemState['status']) {
  return { waiting: 'Waiting', working: 'Working', done: '✅ Imported', failed: '❌ Failed' }[status]
}

/** Folder picks include the sub-folder ("mp4/leave-me-alone.mp4"); plain picks just the name. */
function pathOf(file: File): string {
  return file.webkitRelativePath || file.name
}

async function importOne(item: PlanItem, files: Map<string, File>) {
  const client = supabase()
  const mp4 = files.get(item.mp4 ?? '')
  const mp3 = files.get(item.mp3 ?? '')
  if (!mp4 || !mp3) throw new Error('File went missing — pick the folder again')

  const { duration, thumbnail } = await probeVideo(mp4)
  // A new ?v= on every upload makes phones fetch the new file instead of a cached one.
  const version = Date.now()

  const upload = async (path: string, file: Blob, contentType: string) => {
    // Browsers label MP3s inconsistently ("audio/mp3", ""), so set the type ourselves.
    const body = file.type === contentType ? file : new Blob([file], { type: contentType })
    const { error } = await client.storage.from(BUCKET).upload(path, body, {
      contentType,
      upsert: true,
      cacheControl: '86400',
    })
    if (error) throw new Error(`${path}: ${error.message}`)
    return `${client.storage.from(BUCKET).getPublicUrl(path).data.publicUrl}?v=${version}`
  }

  const mp4Url = await upload(`mp4/${item.slug}.mp4`, mp4, 'video/mp4')
  const mp3Url = await upload(`mp3/${item.slug}.mp3`, mp3, 'audio/mpeg')
  const thumbUrl = thumbnail ? await upload(`thumbs/${item.slug}.jpg`, thumbnail, 'image/jpeg') : null

  const { error } = await client.from('clips').upsert(
    {
      slug: item.slug,
      title: item.title,
      category: item.category,
      tags: item.tags,
      mp4_url: mp4Url,
      mp3_url: mp3Url,
      thumbnail_url: thumbUrl,
      duration_sec: Math.round(duration * 10) / 10,
    },
    { onConflict: 'slug' },
  )
  if (error) throw new Error(error.message)
}
