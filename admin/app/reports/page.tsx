'use client'

import { useCallback, useEffect, useState } from 'react'
import { supabase } from '@/lib/supabase'
import type { Report } from '@/lib/types'

export default function ReportsPage() {
  const [reports, setReports] = useState<Report[]>([])
  const [showResolved, setShowResolved] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    const { data, error } = await supabase()
      .from('reports')
      .select('id, clip_id, reason, contact_email, created_at, resolved, clips(id, title, status, mp3_url, slug)')
      .eq('resolved', showResolved)
      .order('created_at', { ascending: false })
      .limit(500)
    if (error) setError(error.message)
    else setReports((data ?? []) as unknown as Report[])
    setLoading(false)
  }, [showResolved])

  useEffect(() => {
    load()
  }, [load])

  const run = async (report: Report, action: () => Promise<{ error: { message: string } | null }>) => {
    setBusyId(report.id)
    setError(null)
    const { error } = await action()
    setBusyId(null)
    if (error) setError(error.message)
    else load()
  }

  // Takes the clip down everywhere and closes every report about it.
  const takeDown = (report: Report) =>
    run(report, async () => {
      const client = supabase()
      const removed = await client.from('clips').update({ status: 'removed', pinned: false }).eq('id', report.clip_id)
      if (removed.error) return removed
      return client.from('reports').update({ resolved: true }).eq('clip_id', report.clip_id)
    })

  const dismiss = (report: Report) =>
    run(report, async () => supabase().from('reports').update({ resolved: true }).eq('id', report.id))

  const reopen = (report: Report) =>
    run(report, async () => supabase().from('reports').update({ resolved: false }).eq('id', report.id))

  return (
    <>
      <div className="page-head">
        <h1>Reports</h1>
        <div className="tabs">
          <button className={showResolved ? 'ghost' : undefined} onClick={() => setShowResolved(false)}>
            Open
          </button>
          <button className={showResolved ? undefined : 'ghost'} onClick={() => setShowResolved(true)}>
            Resolved
          </button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : reports.length === 0 ? (
        <p className="muted">{showResolved ? 'No resolved reports.' : 'No open reports. 🎉'}</p>
      ) : (
        <ul className="cards">
          {reports.map((report) => (
            <li key={report.id} className="card">
              <div className="card-main">
                <strong>{report.clips?.title ?? 'Deleted clip'}</strong>
                {report.clips?.status === 'removed' && <span className="badge">removed</span>}
                <p>{report.reason}</p>
                <p className="muted small">
                  {new Date(report.created_at).toLocaleString()}
                  {report.contact_email && (
                    <>
                      {' · '}
                      <a href={`mailto:${report.contact_email}`}>{report.contact_email}</a>
                    </>
                  )}
                </p>
                {report.clips?.mp3_url && <audio controls preload="none" src={report.clips.mp3_url} />}
              </div>
              <div className="actions">
                {report.resolved ? (
                  <button className="ghost" disabled={busyId === report.id} onClick={() => reopen(report)}>
                    Reopen
                  </button>
                ) : (
                  <>
                    {report.clips?.status !== 'removed' && (
                      <button className="danger" disabled={busyId === report.id} onClick={() => takeDown(report)}>
                        Remove clip
                      </button>
                    )}
                    <button className="ghost" disabled={busyId === report.id} onClick={() => dismiss(report)}>
                      {report.clips?.status === 'removed' ? 'Mark done' : 'Dismiss'}
                    </button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
