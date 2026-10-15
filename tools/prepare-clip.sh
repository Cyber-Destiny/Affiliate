#!/usr/bin/env bash
# Turns one downloaded sound or video into the two files the app needs:
#   <out>/mp4/<name>.mp4  480x480, H.264 + AAC, at most 10 s — what gets sent in chats
#   <out>/mp3/<name>.mp3  128 kbps — what the keyboard plays as a preview
#
# Usage:
#   tools/prepare-clip.sh SOURCE NAME [START] [LENGTH] [TITLE]
#
#   SOURCE  any audio or video file ffmpeg can read
#   NAME    lowercase with hyphens, e.g. leave-me-alone
#   START   where the funny part starts, in seconds (default 0)
#   LENGTH  how many seconds to keep, max 10 (default 10)
#   TITLE   text shown on the plain card used when the source has no video (default: NAME)
#
# Output goes to $OUT_DIR (default: ./naija-sounds). Needs ffmpeg and ffprobe.
# Loudness is normalised so every clip plays at about the same volume.
set -euo pipefail

if [ $# -lt 2 ]; then
  sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
fi

SOURCE=$1
NAME=$2
START=${3:-0}
LENGTH=${4:-10}
TITLE=${5:-$NAME}
OUT_DIR=${OUT_DIR:-naija-sounds}

if ! [[ $NAME =~ ^[a-z0-9]+(-[a-z0-9]+)*$ ]]; then
  echo "NAME must be lowercase letters/numbers with hyphens, e.g. leave-me-alone" >&2
  exit 1
fi
[ -f "$SOURCE" ] || { echo "Can't find $SOURCE" >&2; exit 1; }

mkdir -p "$OUT_DIR/mp4" "$OUT_DIR/mp3"

# How much audio is actually left after START (never more than 10 s).
SOURCE_LENGTH=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$SOURCE")
LENGTH=$(awk -v l="$LENGTH" -v s="$START" -v d="$SOURCE_LENGTH" 'BEGIN {
  if (l > 10) l = 10; if (d != "" && d - s < l) l = d - s; if (l < 0.5) l = 0; printf "%.3f", l }')
if [ "$LENGTH" = "0.000" ]; then
  echo "Nothing left to keep after ${START}s in $SOURCE" >&2
  exit 1
fi
FADE_OUT=$(awk -v l="$LENGTH" 'BEGIN { f = l - 0.3; if (f < 0) f = 0; printf "%.3f", f }')
AUDIO_FILTER="loudnorm=I=-16:TP=-1.5:LRA=11,afade=t=in:d=0.05,afade=t=out:st=${FADE_OUT}:d=0.3,aresample=44100"

# A real picture track (not just album art) means we keep the video.
HAS_VIDEO=$(ffprobe -v error -select_streams v -show_entries stream=index:stream_disposition=attached_pic \
  -of csv=p=0 "$SOURCE" | awk -F, '$2 != 1 { found = 1 } END { print found ? "yes" : "no" }')

find_font() {
  if [ -n "${FONT:-}" ]; then echo "$FONT"; return; fi
  for f in \
    /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf \
    /usr/share/fonts/TTF/DejaVuSans-Bold.ttf \
    /usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf \
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf" \
    /Library/Fonts/Arial\ Bold.ttf \
    /c/Windows/Fonts/arialbd.ttf \
    C:/Windows/Fonts/arialbd.ttf; do
    if [ -f "$f" ]; then echo "$f"; return; fi
  done
}

VIDEO_SETTINGS=(-c:v libx264 -preset veryfast -crf 26 -profile:v main -pix_fmt yuv420p -r 30 -movflags +faststart)
AUDIO_SETTINGS=(-c:a aac -b:a 96k -ac 2)

if [ "$HAS_VIDEO" = "yes" ]; then
  ffmpeg -v error -y -ss "$START" -t "$LENGTH" -i "$SOURCE" \
    -vf "scale=480:480:force_original_aspect_ratio=increase,crop=480:480,setsar=1" \
    -af "$AUDIO_FILTER" "${VIDEO_SETTINGS[@]}" "${AUDIO_SETTINGS[@]}" \
    "$OUT_DIR/mp4/$NAME.mp4"
else
  # No video: a bold coloured card with the title and a moving sound wave.
  COLOURS=(0x0A8F4D 0xFF5A4E 0x3A86FF 0x9B5DE5 0xFF4F9A 0x00A9C7 0xE89F00 0x8D6E63)
  COLOUR=${COLOURS[$(( $(printf '%s' "$NAME" | cksum | cut -d' ' -f1) % ${#COLOURS[@]} ))]}
  TEXT_FILE=$(mktemp)
  trap 'rm -f "$TEXT_FILE"' EXIT
  printf '%s' "$TITLE" | fold -s -w 14 | head -4 > "$TEXT_FILE"
  FONT_FILE=$(find_font)
  if [ -n "$FONT_FILE" ]; then FONT_OPT="fontfile='$FONT_FILE'"; else FONT_OPT="font='Sans:style=Bold'"; fi
  ffmpeg -v error -y -ss "$START" -t "$LENGTH" -i "$SOURCE" \
    -f lavfi -i "color=c=$COLOUR:s=480x480:r=30" \
    -filter_complex "[0:a]$AUDIO_FILTER,asplit[a][w];\
[w]showwaves=s=440x120:mode=cline:colors=white@0.85:rate=30[wave];\
[1:v][wave]overlay=20:330:shortest=1,\
drawtext=$FONT_OPT:textfile='$TEXT_FILE':fontcolor=white:fontsize=52:line_spacing=10:x=(w-text_w)/2:y=(300-text_h)/2+20[v]" \
    -map "[v]" -map "[a]" -t "$LENGTH" "${VIDEO_SETTINGS[@]}" "${AUDIO_SETTINGS[@]}" \
    "$OUT_DIR/mp4/$NAME.mp4"
fi

ffmpeg -v error -y -ss "$START" -t "$LENGTH" -i "$SOURCE" -vn -af "$AUDIO_FILTER" \
  -c:a libmp3lame -b:a 128k "$OUT_DIR/mp3/$NAME.mp3"

echo "✓ $NAME  ($(printf '%.1f' "$LENGTH")s, video: $HAS_VIDEO) → $OUT_DIR/mp4/$NAME.mp4, $OUT_DIR/mp3/$NAME.mp3"
