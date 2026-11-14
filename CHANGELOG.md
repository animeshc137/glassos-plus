# Changes from upstream v1.0.2-beta

## Unreleased

- Settings text fields (name, TMDB key, subreddits, painters) use the system keyboard, so the Android TV remote app on a phone can type into them; pasted text works too.
- New Top Shelf sources: Reddit wallpapers (tick or add subreddits, Hot/New/Top/Rising, period, count, minimum size, refresh interval) and Art (Google Art Project scans of museum paintings from Wikimedia Commons, filtered by painter, movement, age and subject). Both read public feeds that work without an account.
- Settings > Wallpaper interval sets how long each Top Shelf wallpaper stays (5 seconds to 1 hour; 8 seconds as before), also on the Reddit and Art pages.
- New art screensaver (Android DreamService): paintings full screen with title and painter (optional frame), configurable interval, optional clock, the same filters as the Art wallpaper (its own or shared); can be set as the screensaver over the TV's own ADB.

## 1.1.2

- Recognize official Nuvio Full (`com.nuvio.tv`), Play Store/legacy (`com.nuvio.app`) and official debug package IDs; route exact titles to an installed compatible distribution.
- Preserve the verified Nuvio navigation-task workaround across supported distributions, without clearing app data.
- Distinguish a missing/disabled app from an installed app without a compatible title handler.
- Keep the selected Apps dock at its original bottom position. Down reveals Continue Watching; Up restores the dock and selected app. Hero presentation and carousel auto-scroll controls remain available.

## 1.1.1

- Expanded the Cinemeta Hero selection from 24 to up to 120 titles, using Popular and Featured pages and deduplicating exact identifiers.
- Prioritized titles absent from the preceding cached selection; kept offline artwork, high-quality backgrounds, original layout, timing and three-entry decoded cache.
- Added catalog-selection JVM checks and a one-time cache-format marker so existing installations fetch the expanded selection.

## 1.1.0

- Added the native, D-pad-focusable Hero refresh icon beside Open. Cached titles shuffle without a consecutive repeat, using the existing loader/crossfade; last committed selection survives restart.
- Kept the distinct Settings catalog-refresh action with a clearer name.
- Added WuPlay IMDb movie/series links and a third preferred-player setting; missing apps/unsupported exact routes never fall back to a generic player home.
- Preserved compact Continue Watching, original Hero expansion, app customization and the pending opt-in Direct Home key experiment. Physical HOME interception is firmware dependent and not certified.
- Kept the application ID while branding the installed app GlassOS+. Added dedicated release signing, tag release automation, bundled license notices, stable download URL and recovery guidance.
- Retained targetSdk 28/minSdk 22 for inherited device compatibility; direct APK distribution only. WuPlay positive-case device testing is pending an installed compatible build.


## GlassOS+ initial source publication — 2026-11-04

- Separate Personal namespace/application ID, retaining coexistence with upstream GlassOS.
- Public Watch Next / Preview Channel reader with permission flow, filtering, deduplication, recent-activity ordering, original resume links and bounded private caches.
- Compact reusable Continue Watching carousel aligned to app dock metrics; progress and episode/source labels use published metadata.
- Persistent auto-scroll switch, default Off, with focus/interaction pause and idle resumption.
- Restore original Hero dock geometry and transitions; handle cached/warm Activity startup without moving focus.
- Layout configuration for app columns, margins, spacing and size; immediate hidden-app persistence.
- Larger app artwork rendering and Cinemeta backdrops, varied catalog selection, movie/series controls and exact IMDb routes to Nuvio/Stremio.
- Optional event-driven Fast Home accessibility helper without window-content access or key filtering; usage watcher fallback.
- Dependency-free checks for layout, routing, Home matching, progress, ordering, carousel wrapping and idle rules.
- Portable build helper, public documentation, upstream attribution and GPL v3 license retained.
