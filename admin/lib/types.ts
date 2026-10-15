export type ClipStatus = 'live' | 'removed'

export interface Clip {
  id: string
  slug: string
  title: string
  category: string
  tags: string[]
  mp4_url: string
  mp3_url: string
  thumbnail_url: string | null
  duration_sec: number
  play_count: number
  send_count: number
  status: ClipStatus
  pinned: boolean
  created_at: string
}

export interface Category {
  id: number
  name: string
  icon: string
  sort_order: number
}

export interface Report {
  id: string
  clip_id: string
  reason: string
  contact_email: string | null
  created_at: string
  resolved: boolean
  clips: Pick<Clip, 'id' | 'title' | 'status' | 'mp3_url' | 'slug'> | null
}
