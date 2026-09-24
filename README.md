# GameVault

**A local-first, offline, universal game-library manager for Android.** GameVault scans folders you choose via the Android Storage Access Framework, identifies game platforms using layered file-structure analysis — never the extension alone — and presents your collection as a premium 3D-cover library.

> **GameVault is not an emulator.** It never executes, patches, or modifies game files. Opening a file always goes through Android's own app chooser.

---

## Why GameVault

Most "library" apps are either ROM-manager front-ends for a single emulator, or they classify every `.iso` as the same console. GameVault is a genuine *library tool*:

- **Layered detection engine** — magic bytes, disc-structure sniffing, container formats, folder layout, filename serial patterns, and size heuristics combine into a confidence score. Every entry is labelled **Detected / Probably / Unknown / Unreadable / Manual**, and a `.cue` sheet is never silently labelled "PS1".
- **Memory-safe by construction** — identification reads bounded windows (≤ 8 MiB) via positional I/O on the SAF descriptor. A 4.5 GB Wii ISO is never loaded into RAM.
- **Incremental, cancellable scanning** — unchanged files are skipped using `(uri, size, mtime)`; scans run on background threads and record history.
- **Local-first** — no account, no analytics, no bundled network service. The optional metadata-provider interface ships disabled.
- **Scoped storage, done right** — zero storage permissions requested. Everything flows through `ACTION_OPEN_DOCUMENT_TREE` with persisted, revocable grants.

## Features

| Area | Details |
|---|---|
| Library | Grid / List / Shelf views, platform filters, favorites, search, sort (title, platform, size, recently added) |
| 3D covers | Front + spine + back, perspective tilt (drag, spring settle), soft shadow, subtle reflection, carousel-friendly sizing, cover-size control |
| Custom covers | System image picker → crop / rotate / fit / fill / preview → saved privately; original file untouched; one-tap reset |
| Metadata per game | Title + normalized title, platform, region, SAF URI, filename, size, mtime, detected format, confidence, detection reasons, favorite, hidden, collections, notes, scan status, last-viewed |
| Organization | Multi-select batch ops (favorite / hide / add-to-collection / remove), custom collections, rename metadata only, hide/unhide |
| Health | Duplicate detection (content sampling for large images), missing/broken-file detection on rescan, manual "Identify Game" override |
| Storage stats | Game/file counts, total bytes per library |
| i18n | English + Arabic with full RTL layout, dark/light/system themes |

## Supported platforms (35+)

Detection signals in parentheses — extension alone is never sufficient for a confident verdict.

| Vendor | Platforms |
|---|---|
| Nintendo | NES/FDS (iNES header), SNES (header/size heuristics), N64 (endianness magics), Game Boy / GBC (Nintendo logo + CGB flag), GBA (logo + header), DS (cartridge logo), 3DS (.3ds NCSD, CIA, NCCH), GameCube (disc magic `0xC2339F3D` + size), Wii (same magic + DVD scale / WBFS), Wii U (WUD/WUX/RPX), Switch (XCI/NSP/NRO/NCA) |
| Sony | PS1 (ISO9660 + licence string + CD scale, PBP/PSISOIMG), PS2 (BOOT2 SYSTEM.CNF + DVD scale), PSP (UMD `PSP_GAME`/`UMD_DATA`, CSO), PS3 (`PS3_GAME` tree, PARAM.SFO, PKG), PS4/PS5 (CUSA title-ids, `sce_sys`, PKG header), Vita (VPK, PCS* serials) |
| Microsoft | Xbox (XDVDFS `MICROSOFT*XBOX*MEDIA`, XBE), Xbox 360 (360 volume string, XEX) |
| Sega | Dreamcast (IP.BIN `SEGA SEGAKATANA`, GDI/CDI), Saturn (boot sector), Mega Drive (`SEGA @0x100`, MK-serials), Master System / Game Gear (`TMR SEGA`), 32X, Sega CD |
| Others | 3DO, Philips CD-i (`CD-RTOS`), PC Engine, Neo Geo / NGPC, WonderSwan, Atari 2600 / Lynx (`LYNX`) / Jaguar, Amiga (`DOS` disk header), Atari ST, ZX Spectrum (`ZXTape!`), ColecoVision, Intellivision, Vectrex, Game.com, PC |

PS3/PS4/PS5 share the `PKG` container header — GameVault reports it honestly as *Sony PlayStation (PKG)* rather than guessing a generation. RVZ/CHD report the container and, when possible, the console from content sniffing.

## Architecture

```
app/src/main/java/com/clxv/gamevault/
├── core/
│   ├── model/            Platform enum, Region, ScanStatus, DetectionResult
│   ├── detection/        DetectionEngine (7 signal layers, scoring), ByteUtils, SafByteReader
│   ├── scanner/          LibraryScanner (SAF walk, incremental, cancellable), DuplicateFinder, ScanWorker
│   ├── saf/              SafManager (tree picker, persisted permissions)
│   ├── covers/           CoverManager (private cover store, sampled decode, crop/rotate)
│   ├── settings/         SettingsManager (DataStore preferences)
│   └── metadata/         MetadataProvider interface — disabled by default
├── data/
│   ├── local/            Room: games, files, collections, library roots, scan history
│   └── repository/       GameRepository (manual identify, rename, duplicates, collections)
├── di/                   Hilt modules
└── ui/                   Compose Material 3: theme, Cover3D, library/detail/editor/
                          collections/settings/duplicates screens, navigation
```

**Data model:** a *game* (logical title) is separate from *file records* (physical copies). That split is what powers duplicate grouping, multi-file discs, and "rename metadata without touching the file".

**Detection confidence:** each rule contributes evidence to per-platform candidates; the max score maps to a status: `>= 0.85` Detected, `>= 0.45` Probably, otherwise Unknown. Empty/truncated disc images are reported **Unreadable**, never guessed.

## Build

### Android Studio
Open the project, let Gradle sync (JDK 17, AGP 8.5), then *Run ▶* or `Build > Build Bundle(s)/APK(s)`.

### GitHub Actions
A ready workflow lives at [`.github/workflows/build.yml`](.github/workflows/build.yml): it installs JDK 17 + Gradle 8.7, generates the wrapper, runs the JVM unit tests, assembles the debug APK and uploads it as an artifact on every push/PR to `main`.

### Termux (on-device build)
```bash
git clone https://github.com/<you>/GameVault && cd GameVault
chmod +x tools/termux-build.sh && ./tools/termux-build.sh
```
The script installs OpenJDK 17, Android cmdline-tools, platform-34 and builds the APK at `app/build/outputs/apk/debug/app-debug.apk`. No root required.

## Tests

```bash
./gradlew testDebugUnitTest
```

Pure-JVM tests cover the detection engine (NES/GB/GBC, GC-vs-Wii size split, WBFS, UMD, BOOT2-PS2, XDVDFS-Xbox, XEX, PFS0, PS3 folder structure, invalid-image handling, region extraction, title normalization — including the guarantee that a generic `.cue` is **not** classified as PS1) and the duplicate grouper (including the head/tail-only sampling guarantee for large files).

## Privacy & security

- No `READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` — SAF only, grants are per-folder and revocable.
- Game files are opened read-only, in bounded windows; the app never writes to library folders.
- Filenames and metadata are treated as untrusted input and rendered as plain text.
- No trackers, no crash-reporting SDKs, no bundled network endpoints.

## Roadmap

- [ ] Optional metadata provider: user-configured endpoint, DB-cached, fully overridable (interface already in place)
- [ ] Sensor-based parallax tilt (setting stub exists)
- [ ] Backup/restore of library DB

## License

MIT — see [LICENSE](LICENSE).
