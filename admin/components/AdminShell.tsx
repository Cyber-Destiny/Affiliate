'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import type { Session } from '@supabase/supabase-js'
import { isConfigured, supabase } from '@/lib/supabase'

type AuthState = 'loading' | 'signed-out' | 'not-admin' | 'ready'

const trimSlash = (path: string) => path.replace(/\/+$/, '') || '/'

const links = [
  { href: '/', label: 'Clips' },
  { href: '/import/', label: 'Import' },
  { href: '/reports/', label: 'Reports' },
]

/** Only shows the admin pages to a signed-in account listed in the admins table. */
export function AdminShell({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>('loading')
  const [email, setEmail] = useState<string | null>(null)
  const pathname = usePathname()

  useEffect(() => {
    if (!isConfigured) return
    const client = supabase()
    let cancelled = false

    const check = async (session: Session | null) => {
      if (!session) {
        if (!cancelled) setState('signed-out')
        return
      }
      setEmail(session.user.email ?? null)
      const { data, error } = await client.rpc('is_admin')
      if (!cancelled) setState(!error && data === true ? 'ready' : 'not-admin')
    }

    client.auth.getSession().then(({ data }) => check(data.session))
    const { data: listener } = client.auth.onAuthStateChange((_event, session) => {
      // Supabase advises not calling it from inside this callback directly.
      setTimeout(() => check(session), 0)
    })
    return () => {
      cancelled = true
      listener.subscription.unsubscribe()
    }
  }, [])

  if (!isConfigured) {
    return (
      <main className="narrow">
        <h1>Almost there</h1>
        <p>
          Copy <code>.env.example</code> to <code>.env.local</code>, fill in your Supabase URL and anon key, then
          restart <code>npm run dev</code>.
        </p>
      </main>
    )
  }

  if (state === 'loading') return <main className="narrow muted">Loading…</main>
  if (state === 'signed-out') return <LoginForm />
  if (state === 'not-admin') {
    return (
      <main className="narrow">
        <h1>Not an admin</h1>
        <p>
          {email} is signed in but isn&apos;t in the <code>admins</code> table. Run the “add an admin” SQL from the
          README in Supabase, then reload.
        </p>
        <button onClick={() => supabase().auth.signOut()}>Sign out</button>
      </main>
    )
  }

  return (
    <>
      <header className="topbar">
        <strong className="brand">Naija Sound admin</strong>
        <nav>
          {links.map((link) => (
            <Link key={link.href} href={link.href} className={trimSlash(pathname) === trimSlash(link.href) ? 'active' : undefined}>
              {link.label}
            </Link>
          ))}
        </nav>
        <span className="spacer" />
        <span className="muted small">{email}</span>
        <button className="ghost" onClick={() => supabase().auth.signOut()}>
          Sign out
        </button>
      </header>
      <main>{children}</main>
    </>
  )
}

function LoginForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    const { error } = await supabase().auth.signInWithPassword({ email, password })
    if (error) setError(error.message)
    setBusy(false)
  }

  return (
    <main className="narrow">
      <h1>Naija Sound admin</h1>
      <form onSubmit={submit} className="stack">
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </main>
  )
}
