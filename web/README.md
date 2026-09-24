# ASL alphabet prototype

This is the alphabet menu of Handspell: a Vite frontend that is built and bundled into the Android app (`scripts/sync-ionic-frontend.sh`). The Capacitor Android shell it was prototyped with is not part of this repo. It requires Node.js 22.12 or newer. The letter cards and phone placement screen are a prototype. The card artwork slots are placeholders, and **Mark practice** records a self-reported completion; there is no sign recognition.

## Run in a browser

```bash
npm ci
npm run start -- --host 0.0.0.0
```

Open `http://localhost:5173/` on the same computer. To use another device, open the network URL printed by Vite. Progress is stored in that browser's local storage.

## Put a build into the app

```bash
npm ci
npm run build -- --base=./
../scripts/sync-ionic-frontend.sh dist     # rsyncs into android/app/src/main/assets/web
```

Then rebuild the Android app. Wireframes for the letter cards come from `../scripts/gen-web-wireframes.py`
(or `../scripts/refresh-letters.sh` to do all of it).
