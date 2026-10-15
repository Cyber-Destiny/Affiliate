#!/usr/bin/env bash
# Makes 8 placeholder clips (a beep on a coloured card, one per category) plus
# clips.csv, so you can try the admin import and the app before you have real
# sounds. Output: ./naija-sounds-test (upload it on the admin Import page).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT_DIR=${OUT_DIR:-naija-sounds-test}
export OUT_DIR
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$OUT_DIR"
CSV="$OUT_DIR/clips.csv"
echo 'filename,title,category,tags,source_url' > "$CSV"

make() { # name, title, category, tags, tone Hz
  ffmpeg -v error -y -f lavfi -i "sine=frequency=$5:duration=3" -f lavfi -i "anoisesrc=d=3:a=0.02" \
    -filter_complex "[0:a][1:a]amix=inputs=2,volume=2" "$TMP/$1.wav"
  tools/prepare-clip.sh "$TMP/$1.wav" "$1" 0 3 "$2"
  echo "$1,\"$2\",$3,\"$4\",test" >> "$CSV"
}

make test-leave-me-alone "Leave me alone (test)" "Leave me alone" "leave me alone, comot, go away, abeg leave me" 330
make test-i-dont-vex "I don vex (test)" "Vex" "vex, i don vex, angry, vexing" 392
make test-chai "Chai! (test)" "Shock" "chai, shock, wahala, omg" 440
make test-e-choke "E choke (test)" "Laughing" "e choke, laugh, lmao, lol" 494
make test-thank-god "Thank God (test)" "Praise" "thank god, praise, hallelujah, god abeg" 523
make test-sapa "Sapa don hold me (test)" "Broke" "sapa, broke, no money, i'm broke" 587
make test-my-baby "My baby (test)" "Love" "love, my baby, i love you, baby" 659
make test-who-ask-you "Who ask you? (test)" "Sarcasm" "who ask you, sarcasm, ehen, ok o" 698

echo
echo "Done: $(ls "$OUT_DIR/mp4" | wc -l | tr -d ' ') clips in $OUT_DIR. Import that folder on the admin Import page."
