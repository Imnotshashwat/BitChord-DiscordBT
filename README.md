# BitChord Rickroll

> Never gonna give you FLAC,  
> Never gonna let you lag.  
> Hijacked your DAC,  
> Hi-Res badge just for you.

Xposed module that fakes a Hi-Res Lossless badge in BitChord for regular lossy tracks. Real FLAC, ALAC, and Dolby Atmos streams are untouched.

---

## What it hooks

- **Now playing screen** — lossy tracks (Opus/AAC/MP3) show a Hi-Res Lossless badge instead of their actual quality.
- **Discord Rich Presence** — shows `Hi-Res Lossless • FLAC • 2458 kbps • 24-bit • 96 kHz • Stereo` for lossy tracks only.

Genuine addon streams keep their real specs. No launcher icon, no settings.

---

## Setup

### Rooted

1. Install [Vector](https://github.com/JingMatrix/Vector) (LSPosed fork for newer Android).
2. Download `BitChord-Rickroll.apk` from [Releases](../../releases).
3. Install it, enable the module in Vector scoped to BitChord, then force-stop and relaunch BitChord.

### Non-rooted

1. Download `BitChord-Rickroll.apk` from [Releases](../../releases).
2. Install [LSPatch](https://github.com/JingMatrix/LSPatch).
3. Open LSPatch → New patch → select BitChord → Integrated → add the module → Patch → Install.

Re-patch after every BitChord update.

---

## License

MIT
