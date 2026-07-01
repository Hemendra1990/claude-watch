# Depth Wallpaper

A depth-effect **live wallpaper for Android** (built for the Samsung Galaxy S26
Ultra, works on any Android 8.0+ device). Pick a photo and the app cuts out the
subject on-device, then renders it **in front of the lock-screen clock** with
subtle gyroscopic parallax — the same "pops out of the screen" effect made
popular by iOS 16 depth wallpapers.

> Inspired by the *Depth Live Wallpaper* concept. This is a clean-room,
> from-scratch implementation — no affiliation with any existing app.

## How it works

The wallpaper is composed of three planes, drawn back-to-front every frame:

| Plane | Layer | Motion on tilt |
|-------|-------|----------------|
| Far | Background photo | slides *most* |
| Middle | Clock (time + date) | fixed |
| Near | Subject cut-out | slides *opposite* the background |

Because the subject is painted **last**, it overlaps the clock exactly where the
subject's pixels are — that overlap is the depth illusion. The opposing parallax
between the background and the subject exaggerates the separation as you move the
phone.

The subject cut-out is produced entirely on-device by **ML Kit Subject
Segmentation** — no photos leave the phone.

## Architecture

```
com.hemendra.depthwallpaper
├── MainActivity                 Compose host
├── data/
│   ├── WallpaperConfig          immutable user settings
│   └── WallpaperRepository      layers (PNG) + config (SharedPreferences), shared
│                                by the UI and the wallpaper engine
├── segmentation/
│   └── SubjectSegmenter         ML Kit foreground-bitmap wrapper (coroutines)
├── wallpaper/
│   ├── DepthWallpaperService    WallpaperService + render thread + lifecycle
│   ├── DepthRenderer            composites the three planes with overscan/parallax
│   ├── ClockRenderer            allocation-free clock drawing
│   └── ParallaxController       accelerometer → smoothed, relative tilt offset
└── ui/
    ├── DepthViewModel           pick → segment → persist; exposes UI state
    ├── HomeScreen               preview + photo picker + controls
    └── components/DepthPreview  live, phone-shaped preview of the composition
```

The UI and the running wallpaper communicate through `WallpaperRepository`:
settings are written to `SharedPreferences`, and the engine listens for changes
so tweaks (clock colour, parallax strength, a new photo) apply **live**.

## Build & run

Requirements: Android Studio (Ladybug or newer), JDK 17, Android SDK 35.

```bash
./gradlew :app:assembleDebug        # build the APK
./gradlew installDebug              # install on a connected device
```

Then in the app:

1. **Choose photo** — pick an image with a clear subject.
2. Wait for the subject to be cut out (first run downloads the ~few-MB ML Kit
   model).
3. Tune the clock colour, date, and parallax/pop sliders.
4. **Set as wallpaper** — confirm in the system preview. Choose *Lock screen*
   (or both) to get the clock-overlap effect.

## Roadmap

- [ ] EXIF-aware orientation handling for picked photos
- [ ] Manual mask touch-up (brush add/erase)
- [ ] Multiple clock fonts / styles and free clock positioning
- [ ] Depth blur on the background plane
- [ ] Optional depth-of-field / breathing animation when idle

## License

Apache-2.0. See [`LICENSE`](LICENSE).
