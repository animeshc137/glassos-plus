import io.github.abhishekkaisar.glassos.personal.LayoutMath;
import io.github.abhishekkaisar.glassos.personal.TitleLinks;
import io.github.abhishekkaisar.glassos.personal.HomeRedirectPolicy;
import java.util.Collections;
import java.util.Set;
import io.github.abhishekkaisar.glassos.personal.DirectHomeKeyPolicy;
import io.github.abhishekkaisar.glassos.personal.ContinueLogic;
import io.github.abhishekkaisar.glassos.personal.RunningParse;

/** Runs with the existing JDK; no Android device or new test dependency required. */
public final class PersonalizationCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        require("wuplay://movie/tt0371746".equals(TitleLinks.build(TitleLinks.WUPLAY, "movie", "tt0371746")), "WuPlay movie route");
        require("wuplay://series/tt0944947".equals(TitleLinks.build(TitleLinks.WUPLAY, "series", "tt0944947")), "WuPlay series route");
        require(TitleLinks.build(TitleLinks.WUPLAY, "movie", "1234") == null, "TMDB guessed as IMDb");
        io.github.abhishekkaisar.glassos.personal.HeroShuffleBag bag = new io.github.abhishekkaisar.glassos.personal.HeroShuffleBag(new java.util.Random(17));
        java.util.List<String> keys = java.util.Arrays.asList("a", "b", "c", "d", "a");
        String current = "a";
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 3; i++) {
            String next = bag.draw(keys, current);
            require(next != null && !next.equals(current), "Shuffle repeated current");
            require(seen.add(next), "Shuffle repeated within bag");
            current = next;
        }
        require(bag.draw(Collections.singletonList(current), current) == null, "Singleton shuffle repeats");
        require(bag.draw(Collections.emptyList(), current) == null, "Empty shuffle invents title");
        java.util.List<String> catalog = io.github.abhishekkaisar.glassos.personal.HeroCatalogSelection.choose(
            java.util.Arrays.asList("old", "fresh", "fresh", "other", null, ""), Collections.singleton("old"), 2, new java.util.Random(3));
        require(catalog.size() == 2 && !catalog.contains("old"), "Catalog reused previous batch before fresh titles");
        catalog = io.github.abhishekkaisar.glassos.personal.HeroCatalogSelection.choose(
            java.util.Arrays.asList("old", "fresh", "fresh"), Collections.singleton("old"), 120, new java.util.Random(3));
        require(catalog.size() == 2 && catalog.get(0).equals("fresh"), "Sparse catalog loses fallback or duplicates titles");
        require(io.github.abhishekkaisar.glassos.personal.HeroCatalogSelection.choose(Collections.emptyList(), Collections.emptySet(), 120, new java.util.Random(3)).isEmpty(), "Empty catalog fabricated entries");
        require(TitleLinks.isNuvio("com.nuvio.tv") && TitleLinks.isNuvio("com.nuvio.app"), "Nuvio distribution missing");
        require(TitleLinks.isNuvio("com.nuviodebug.com") && TitleLinks.isNuvio("com.nuvio.appdebug"), "Official debug distribution missing");
        require(!TitleLinks.isNuvio("com.nuvio.fake"), "Untrusted package classified as Nuvio");
        require("nuvio://movie/tt0371746".equals(TitleLinks.build(TitleLinks.NUVIO_TV, "movie", "tt0371746")), "Native Nuvio route changed");
        require(java.util.Arrays.asList(TitleLinks.packages(TitleLinks.NUVIO)).contains(TitleLinks.NUVIO_TV), "Package fallback missing");
        require(TitleLinks.needsTaskReset(TitleLinks.NUVIO), "Working legacy navigation lost");
        require(TitleLinks.needsTaskReset(TitleLinks.NUVIO_TV) && !TitleLinks.needsTaskReset(TitleLinks.STREMIO), "Nuvio task workaround scope incorrect");
        require(LayoutMath.homeTranslation(false, 260) == 0, "Selected dock moved from bottom");
        require(LayoutMath.homeTranslation(true, 260) == -260, "Continue Watching not revealed");
        Set<String> homes = Collections.singleton("stock/stock.HomeActivity");
        require(HomeRedirectPolicy.accepts(true, true, "stock", "stock.HomeActivity", homes), "Home ignored");
        require(!HomeRedirectPolicy.accepts(true, true, "stock", "stock.SettingsActivity", homes), "Settings hijacked");
        require(!HomeRedirectPolicy.accepts(true, true, "com.nuvio.app", "stock.HomeActivity", homes), "Streaming app hijacked");
        require(!HomeRedirectPolicy.accepts(false, true, "stock", "stock.HomeActivity", homes), "Disabled preference ignored");
        require(!HomeRedirectPolicy.accepts(true, false, "stock", "stock.HomeActivity", homes), "Explicit stock visit intercepted");
        require(!HomeRedirectPolicy.accepts(true, true, null, null, homes), "Null event accepted");
        require(ContinueLogic.progress(-1, 60000) == -1, "Unknown progress fabricated");
        require(ContinueLogic.progress(30000, 60000) == .5f, "Progress ratio wrong");
        require(ContinueLogic.progress(10000, 0) == -1, "Unknown runtime fabricated");
        require(!ContinueLogic.unfinished(60000, 60000), "Completed item retained");
        require(ContinueLogic.rank(0, true) == 0, "Unknown activity fabricated");
        require(ContinueLogic.rank(1000000, true) < ContinueLogic.rank(2000000, false), "Nuvio overrides much newer activity");
        require(ContinueLogic.identity("CAFÉ: Film", "1", "2", "2024").equals(ContinueLogic.identity("cafe film", "1", "2", "2024")), "Title dedup normalization");
        require(!ContinueLogic.identity("Show", "1", "2", "").equals(ContinueLogic.identity("Show", "1", "3", "")), "Different episodes merged");
        require(ContinueLogic.wrap(-1, 3) == 2 && ContinueLogic.wrap(3, 3) == 0, "Carousel wrap wrong");
        require(!ContinueLogic.mayAdvance(true, true, 4, 15000), "Focused row auto-advanced");
        require(!ContinueLogic.mayAdvance(true, false, 4, 8000), "Idle pause too short");
        require(ContinueLogic.mayAdvance(true, false, 4, 9000), "Idle resume failed");
        require(!ContinueLogic.mayAdvance(false, false, 4, 15000), "Paused home auto-advanced");
        require(LayoutMath.dockTop(1080, 169, 40, 50, 22) == 799, "Original Hero dock moved");
        require(LayoutMath.continueHeight(1920, 283) == 259, "Compact row geometry changed");
        require(DirectHomeKeyPolicy.accepts(true, 3), "Home filter ignores Home");
        require(!DirectHomeKeyPolicy.accepts(false, 3), "Disabled filter handles Home");
        for (int code : new int[]{4, 19, 20, 21, 22, 23, 24, 25, 26, 85, 219, 284}) {
            require(!DirectHomeKeyPolicy.accepts(true, code), "Non-Home key consumed");
        }
        require(DirectHomeKeyPolicy.flags(16, true) == 48, "Existing flags lost");
        require(DirectHomeKeyPolicy.flags(48, false) == 16, "Filter flag not removed");
        int layouts = 0;
        for (int width : new int[]{960, 1280, 1920, 3840}) {
            for (int columns = 4; columns <= 6; columns++) {
                for (int edge : new int[]{24, 48, 64, 80, 104, 128}) {
                    for (int gapReference : new int[]{24, 40, 52, 64}) {
                        for (int size : new int[]{80, 90, 100}) {
                            int margin = width * edge / 1920 + width * 38 / 1920;
                            int gap = width * gapReference / 1920;
                            int tile = LayoutMath.tileWidth(width, columns, margin, gap, size);
                            int screenHeight = width * 9 / 16;
                            int tileHeight = tile * 3 / 5;
                            int pad = screenHeight * 40 / 1080;
                            int peekHeight = tileHeight * 30 / 100;
                            int peekGapHeight = screenHeight * 22 / 1080;
                            int originalDock = LayoutMath.dockTop(screenHeight, tileHeight, pad, peekHeight, peekGapHeight);
                            int continueHeight = LayoutMath.continueHeight(width, tile);
                            int continueTop = originalDock + tileHeight + pad + peekGapHeight;
                            require(continueTop >= screenHeight - peekHeight, "Continue occupies expanded Hero space");
                            require(continueTop + continueHeight - continueHeight <= screenHeight, "Compact row below viewport");
                            require(originalDock - continueHeight > screenHeight / 4, "Compact rows crowd Hero title");
                            require(tile > 0, "Non-positive tile");
                            for (int count = 1; count <= columns; count++) {
                                int left = LayoutMath.rowInset(width, count, tile, gap, margin);
                                int right = left + count * tile + (count - 1) * gap;
                                require(left >= margin && right <= width - margin, "Row leaves safe margin");
                                require(left - tile * .1 >= 0 && right + tile * .1 <= width, "Focused tile clipped");
                            }
                            layouts++;
                        }
                    }
                }
            }
        }
        require(LayoutMath.columns(1) == 4 && LayoutMath.columns(20) == 6, "Column range");
        require("nuvio://movie/tt1375666".equals(TitleLinks.fromKey(TitleLinks.NUVIO, "t:cinemeta_movie_tt1375666")), "Wrong movie link");
        require("nuvio://series/tt0903747".equals(TitleLinks.fromKey(TitleLinks.NUVIO, "t:cinemeta_series_tt0903747")), "Wrong series link");
        require("stremio:///detail/movie/tt1375666/tt1375666".equals(TitleLinks.build(TitleLinks.STREMIO, "movie", "tt1375666")), "Wrong Stremio link");
        require(TitleLinks.fromKey(TitleLinks.NUVIO, "t:tmdb_movie_27205") == null, "TMDB id guessed as IMDb");
        require(TitleLinks.build(TitleLinks.NUVIO, "movie", "tt1375666/../../other") == null, "Malformed id accepted");
        require(TitleLinks.build(TitleLinks.NUVIO, "other", "tt1375666") == null, "Unsupported type accepted");
        require(TitleLinks.build("com.netflix.ninja", "movie", "tt1375666") == null, "Wrong app fallback");
        runningParseChecks();
        System.out.println("Passed: " + layouts + " layout combinations and exact movie/series link checks.");
    }

    /** Real `dumpsys` shapes from an Android 9 TV (trimmed). */
    private static void runningParseChecks() {
        String mem = "Applications Memory Usage (in Kilobytes):\nUptime: 1 Realtime: 1\n\nTotal PSS by process:\n"
            + "    117,339K: com.google.android.gms (pid 3849)\n"
            + "     65,591K: io.github.abhishekkaisar.glassos.personal (pid 11894 / activities)\n"
            + "     54,791K: system (pid 3412)\n"
            + "     54,380K: com.android.vending (pid 11793)\n"
            + "     37,316K: com.android.vending:background (pid 12001)\n\n"
            + "Total PSS by OOM adjustment:\n    123,456K: Native\n       10K: com.never.counted (pid 1)\n";
        String svc = "ACTIVITY MANAGER SERVICES (dumpsys activity services)\n"
            + "  * ServiceRecord{835fe9b u0 com.google.android.gms/.play.integrity.api.service.IntegrityApiPersistentService}\n"
            + "    isForeground=false\n"
            + "  * ServiceRecord{36805ad u0 com.google.android.tv.remote.service/.DiscoveryService}\n"
            + "    isForeground=true foregroundId=8765 foregroundNoti=Notification(channel=atv_remote_service)\n"
            + "  * ServiceRecord{8606eca u0 com.vasmarfas.UniversalAmbientLight/.common.ScreenGrabberService}\n"
            + "    isForeground=true foregroundId=1\n"
            + "  * ServiceRecord{a1b2c3d u0 com.vasmarfas.UniversalAmbientLight/.Other}\n"
            + "  * ServiceRecord{d4e5f60 u10 other.user/.Svc}\n";
        java.util.Map<String, RunningParse.Usage> u = RunningParse.parse(mem, svc);
        require(u.get("com.android.vending").pssKb == 91696L, "Process figures of one package are summed");
        require(u.get("com.google.android.gms").pssKb == 117339L && u.get("com.google.android.gms").services == 1, "Package with one service");
        require(u.get("io.github.abhishekkaisar.glassos.personal").hasActivity, "Foreground app detected");
        require(!u.get("com.android.vending").hasActivity, "Background app is not foreground");
        require(u.get("com.google.android.tv.remote.service").foregroundServices == 1, "Foreground service counted");
        require(u.get("com.vasmarfas.UniversalAmbientLight").services == 2 && u.get("com.vasmarfas.UniversalAmbientLight").foregroundServices == 1, "Services counted per package");
        require(!u.containsKey("other.user"), "Other users' services leaked in");
        require(!u.containsKey("com.never.counted"), "Read past the process section");
        require(u.containsKey("system"), "Non-package processes are kept for the caller to filter");
        require(RunningParse.packageOf("com.android.vending:background").equals("com.android.vending"), "Process suffix");
        require(RunningParse.isPackageName("com.netflix.ninja") && RunningParse.isPackageName("tv.nekko.to"), "Real package names");
        require(!RunningParse.isPackageName("system") && !RunningParse.isPackageName("com.x; reboot") && !RunningParse.isPackageName("com.x$(id)") && !RunningParse.isPackageName(null), "Unsafe name accepted");
    }
}
