// Reads a video file in the browser to get its length and a square thumbnail.

function waitFor(el: HTMLMediaElement, event: string, ms: number): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup()
      reject(new Error(`Timed out waiting for ${event}`))
    }, ms)
    const onEvent = () => {
      cleanup()
      resolve()
    }
    const onError = () => {
      cleanup()
      reject(new Error('This browser could not read the video'))
    }
    const cleanup = () => {
      clearTimeout(timer)
      el.removeEventListener(event, onEvent)
      el.removeEventListener('error', onError)
    }
    el.addEventListener(event, onEvent)
    el.addEventListener('error', onError)
  })
}

export async function probeVideo(file: File, size = 240): Promise<{ duration: number; thumbnail: Blob | null }> {
  const url = URL.createObjectURL(file)
  const video = document.createElement('video')
  video.muted = true
  video.playsInline = true
  video.preload = 'auto'
  video.src = url
  try {
    await waitFor(video, 'loadeddata', 15000)
    const duration = Number.isFinite(video.duration) ? video.duration : 0
    video.currentTime = Math.min(0.5, duration / 2)
    await waitFor(video, 'seeked', 5000).catch(() => undefined)

    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    if (!ctx || !video.videoWidth) return { duration, thumbnail: null }
    const side = Math.min(video.videoWidth, video.videoHeight)
    ctx.drawImage(
      video,
      (video.videoWidth - side) / 2,
      (video.videoHeight - side) / 2,
      side,
      side,
      0,
      0,
      size,
      size,
    )
    const thumbnail = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.8))
    return { duration, thumbnail }
  } catch {
    return { duration: 0, thumbnail: null }
  } finally {
    URL.revokeObjectURL(url)
    video.removeAttribute('src')
    video.load()
  }
}

/** Runs `worker` over `items`, at most `limit` at a time. */
export async function runPool<T>(items: T[], limit: number, worker: (item: T) => Promise<void>): Promise<void> {
  let next = 0
  const lanes = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const item = items[next++]
      await worker(item)
    }
  })
  await Promise.all(lanes)
}
