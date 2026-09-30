# BitChord Rickroll

> Never gonna give you FLAC,  
> Never gonna let you lag.  
> Hijacked your DAC,  
> Hi-Res badge just for you.

LSPosed / LSPatch module for BitChord. Fakes a Hi-Res Lossless badge on lossy tracks without touching real FLAC or Dolby Atmos streams.

---

## What it does

- **Now Playing screen** — shows *Hi-Res Lossless* badge on Opus/AAC/MP3 tracks.
- **Discord Rich Presence** — shows `Hi-Res Lossless • FLAC • 2458 kbps • 24-bit • 96 kHz • Stereo` for lossy tracks.
- Real addon FLAC, ALAC, WAV, and Dolby Atmos streams are always left untouched.
- No launcher icon. No settings. Runs silently.

---

## Setup

### Rooted — LSPosed

1. Install [LSPosed](https://github.com/LSPosed/LSPosed) via Magisk or KernelSU.
2. Download `BitChord-Rickroll.apk` from [Releases](../../releases).
3. Install it, enable the module in LSPosed scoped to **BitChord**, then force-stop and relaunch BitChord.

### Non-rooted — LSPatch

1. Extract your installed BitChord APK using any APK extractor app.
2. Install [LSPatch](https://github.com/LSPosed/LSPatch/releases) and (optionally) [Shizuku](https://shizuku.rikka.app/) for seamless install.
3. Open LSPatch → **＋** → select BitChord APK → embed `BitChord-Rickroll.apk` as a module → patch → install.

> Re-patch after every BitChord update since the patched APK won't auto-update.

---

## License

MIT
