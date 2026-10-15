# Naija Sound Keyboard

A keyboard for Android that lets people type a vibe like “leave me alone” and send a matching Naija meme sound into WhatsApp, Snapchat, Telegram, Instagram or any chat. Each sound goes out as a short video, so the person receiving it doesn't need the app.

| Folder | What it is |
| --- | --- |
| `android/` | The Android app (Kotlin + Jetpack Compose) and the keyboard itself |
| `supabase/` | The database: tables, search, access rules and file storage (`schema.sql`) |
| `admin/` | Your private admin page (Next.js): add clips in bulk, edit, pin to Trending, handle reports |
| `tools/` | Scripts that turn downloaded sounds into the MP4 + MP3 files the app needs |

```
 You ──► admin page ──► Supabase (database + file storage) ◄── app + keyboard on people's phones
```

Users can't upload clips; only admins add sounds. There are no user accounts. Favourites and recently sent sounds live on the phone.

---

## Setup, step by step

You need a free [Supabase](https://supabase.com) account and a GitHub account. To run the admin page on your computer you also need [Node.js](https://nodejs.org) 20 or newer.

### 1. Create the database

1. In Supabase, create a new project and wait for it to finish setting up.
2. Open **SQL Editor → New query**, paste everything from [`supabase/schema.sql`](supabase/schema.sql), and click **Run**.
   This creates the tables, the search, the access rules, the `clips` file bucket and the 8 starter categories. It's safe to run again later.

### 2. Create your admin login

1. **Authentication → Users → Add user → Create new user.** Enter your email and a password, and tick *Auto confirm user*.
2. Back in the **SQL Editor**, run this with your email:

   ```sql
   insert into public.admins (user_id)
   select id from auth.users where email = 'you@example.com';
   ```

3. Under **Authentication → Sign In / Providers**, turn off **Allow new users to sign up** so nobody else can create a login.

### 3. Copy your project keys

Under **Project Settings → API** (or **API Keys**), copy:

- the **Project URL**, e.g. `https://abcd1234.supabase.co`
- the **anon** key, or the newer **publishable** key (`sb_publishable_…`)

Both are safe to put in the app; the database rules decide what they can do. Never use the `service_role` or secret key anywhere in this project.

### 4. Run the admin page

```bash
cd admin
cp .env.example .env.local     # then paste your URL and key into .env.local
npm install
npm run dev                    # open http://localhost:3000 and sign in
```

To put it online instead, import the repo into [Vercel](https://vercel.com) or Netlify with **root directory** `admin`, build command `npm run build` and output folder `out`. Add `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY` as environment variables.

### 5. Add sounds

**To try everything first**, make 8 placeholder clips (beeps on coloured cards). This needs [ffmpeg](https://ffmpeg.org):

```bash
tools/make-test-clips.sh       # creates naija-sounds-test/
```

On the admin page, go to **Import → Choose folder**, pick `naija-sounds-test`, then click **Import 8 clips**.

**For the real library**, use the “Getting the sounds” prompt from the build document with Claude Code on your own computer. It produces a `naija-sounds/` folder with `mp4/`, `mp3/` and `clips.csv`. Import that folder the same way.

`tools/prepare-clip.sh` turns any single downloaded file into the right format, in case you want to add clips by hand:

```bash
tools/prepare-clip.sh downloaded.mp4 leave-me-alone 12.5 4    # keep 4 s starting at 12.5 s
tools/prepare-clip.sh audio.mp3 god-abeg 0 6 "God abeg"       # audio only → coloured title card
```

It cuts the clip to at most 10 s, adds a tiny fade, evens out the volume, and writes `mp4/<name>.mp4` (480×480, H.264 + AAC) and `mp3/<name>.mp3` (128 kbps).

`clips.csv` needs the columns `filename, title, category, tags` (an extra `source_url` column is ignored). Tags go in one cell, separated by commas: `"leave me alone, comot, go away, abeg leave me"`. Importing a file name that already exists updates that clip and keeps its play and send counts.

### 6. Build the test APK

**Option A: GitHub builds it for you (no Android Studio needed)**

1. In this repo on GitHub, go to **Settings → Secrets and variables → Actions → New repository secret** and add:
   - `SUPABASE_URL`: your Project URL
   - `SUPABASE_ANON_KEY`: your anon or publishable key
   - `CONTACT_EMAIL`: the address for takedown requests, shown in the app
2. Open the **Actions** tab, choose **Build and test**, and click **Run workflow**. Every push also runs it.
3. When it's green, open the run, download **naija-sound-apk** under *Artifacts*, and unzip it.
   - `naija-sound-test.apk` is the fast, optimised build to install.
   - `naija-sound-debug.apk` is for troubleshooting.

**Option B: Android Studio**

1. Open the `android/` folder in Android Studio.
2. Create `android/local.properties` (it stays off GitHub) with:

   ```properties
   SUPABASE_URL=https://abcd1234.supabase.co
   SUPABASE_ANON_KEY=your-anon-or-publishable-key
   CONTACT_EMAIL=you@example.com
   ```

3. Run **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, or run `./gradlew assembleRelease` from `android/`.
   The APK is written to `android/app/build/outputs/apk/`.

### 7. Install it on a phone

Send the APK to an Android phone (Android 7 or newer), open it, and allow *Install unknown apps* when asked. Then open **Naija Sound**. The welcome cards lead into keyboard setup:

1. **Turn on Naija Sound**: switch it on in the keyboard list. Android shows the same privacy warning for every keyboard.
2. **Switch to it** using the keyboard picker.
3. **Try it** in any chat.

---

## How the keyboard works

- **Top:** an **ABC** key (back to your normal keyboard; hold it to pick a keyboard) and a search bar.
- **Search:** tapping the search bar brings up a small letter keyboard. Results update as you type, and **Search** shows them full size.
- **Tabs:** Recent, Favourites, Trending, then each category. The keyboard opens on Favourites when you have some, otherwise on Recent, otherwise on Trending.
- **First tap** on a sound plays it quietly. **Tap again**, or tap **Send**, to send it. If *Auto-preview* is off in Settings, the first tap only selects the sound.
- **Sending:** if the chat box accepts videos straight from a keyboard, the clip is inserted directly. If not, the keyboard opens that app's own share screen, or Android's share sheet if it can't.
- **Offline:** Favourites, Recent and the last Trending list are kept on the phone. Favourited sounds are downloaded, so they work without data.

Search matches titles and tags and ignores case and punctuation. It also handles partial words (“leav”), any word order, stretched spellings (“gehhhh”) and close spellings (“commot” finds “comot”). Exact tag matches come first, then the most-sent clips.

## Admin page

- **Clips:** search, filter, edit title, category and tags, **Pin** to the top of Trending, **Remove** or **Restore**.
- **Import:** bulk upload a folder of MP4 + MP3 files with `clips.csv`. It checks every row first and lists problems such as a missing file or an unknown category.
- **Reports:** reports sent from the app's **Report sound** button. **Remove clip** takes the clip down and closes every report about it. Removed clips also disappear from people's favourites the next time they're online.

## Changing the name

- The app name is in `android/app/src/main/res/values/strings.xml`.
- The package ID (`com.naijasound.keyboard`) is `applicationId` in `android/app/build.gradle.kts`. Change it before your first Play Store upload; it can't change after that.

## Tests

```bash
cd supabase/test && npm install && npm test    # database: search ranking, counters, access rules
cd admin && npm test                           # CSV import rules
cd android && ./gradlew testDebugUnitTest      # app: offline search, favourites, number formatting
```

GitHub Actions runs all three, plus both builds, on every push.

## Before publishing on the Play Store

- Sign with your own upload key. The test build uses the debug key: see `signingConfig` in `android/app/build.gradle.kts`.
- Raise `targetSdk` and `compileSdk` to whatever Google Play currently requires.
- Publish a privacy policy page. The in-app **Terms & privacy** screen is a starting point.
- Have a clear takedown process for sound owners. The Report button and `CONTACT_EMAIL` are already wired up.
