package io.github.abhishekkaisar.glassos.personal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** An app that is running now: the memory of all its processes and how many services it keeps. */
class RunningApp(val pkg: String, val label: String, val pssKb: Long, val services: Int, val foregroundServices: Int)

/** [apps] biggest first; [systemKb] is what the system's own processes use together (never listed or stopped). */
class RunningSnapshot(val apps: List<RunningApp>, val systemKb: Long)

/**
 * What is running and how to stop it. The figures come from `dumpsys`, which only the shell may run, so they are
 * read over the TV's own ADB (the same self-connection the permission grants use).
 */
object Running {
    /** Reads the running apps over [adb]. Call off the UI thread. */
    fun load(ctx: Context, adb: AdbClient): RunningSnapshot {
        val usage = RunningParse.parse(adb.shell("dumpsys meminfo"), adb.shell("dumpsys activity services"))
        val pm = ctx.packageManager
        val launchable = HomeShare.apps?.map { it.pkg }?.toSet() ?: emptySet()
        val labels = HomeShare.apps?.associate { it.pkg to it.label } ?: emptyMap()
        val apps = ArrayList<RunningApp>()
        var systemKb = 0L
        for (u in usage.values) {
            if (u.pkg == ctx.packageName) continue
            // Process names that are not packages (system, media.codec, audioserver) are the system itself.
            val ai = try { pm.getApplicationInfo(u.pkg, 0) } catch (e: Exception) { null } ?: continue
            val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 && ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            // System apps you cannot open from the home (Play services, the input method, TV services) stay untouched.
            if (system && u.pkg !in launchable) { systemKb += u.pssKb; continue }
            if (u.pssKb == 0L && u.services == 0) continue
            val label = labels[u.pkg] ?: try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { u.pkg }
            apps.add(RunningApp(u.pkg, label, u.pssKb, u.services, u.foregroundServices))
        }
        apps.sortByDescending { it.pssKb }
        return RunningSnapshot(apps, systemKb)
    }

    /** Stops [pkgs] (they start again when opened or when they ask to run) and, with [killCached], every cached process. */
    fun clear(adb: AdbClient, pkgs: List<String>, killCached: Boolean) {
        val cmds = pkgs.filter { RunningParse.isPackageName(it) }.map { "am force-stop $it" }.toMutableList()
        if (killCached) cmds.add("am kill-all")
        if (cmds.isEmpty()) return
        WLog.i("running: ${cmds.size} commands -> ${adb.shell(cmds.joinToString("; ")).trim().take(160)}")
    }

    /** Available and total memory in kB, from /proc/meminfo. */
    fun memory(): Pair<Long, Long>? = try {
        val v = HashMap<String, Long>()
        java.io.File("/proc/meminfo").forEachLine { l ->
            val p = l.split(Regex("\\s+"))
            if (p.size >= 2) v[p[0].trimEnd(':')] = p[1].toLongOrNull() ?: 0L
        }
        val total = v["MemTotal"] ?: 0L
        val avail = v["MemAvailable"] ?: ((v["MemFree"] ?: 0L) + (v["Cached"] ?: 0L))
        if (total > 0) avail to total else null
    } catch (e: Exception) { null }

    fun mb(kb: Long) = maxOf(1L, kb / 1024)

    fun error(e: Throwable): String = when (e) {
        is java.net.ConnectException ->
            "Could not reach the TV's ADB. Turn on USB or Network debugging in Settings > Device Preferences > Developer options."
        is java.net.SocketTimeoutException -> "No answer from the TV. If a prompt is showing, choose Allow (tick Always allow), then retry."
        is AdbClient.Closed -> "The TV closed the ADB connection (${e.stage}). Turn debugging off and on, then retry."
        else -> "ADB failed (${e.javaClass.simpleName}: ${e.message}). Turn debugging off and on, then retry."
    }
}

/**
 * Settings > Running apps: what runs now with its memory, select apps to stop, stop them all, or free cached
 * memory. Shown inside the settings panel in place of the settings list.
 */
class RunningPage(private val home: HomeScreen) : ScrollView(home.ctx) {
    private val ui = Handler(Looper.getMainLooper())
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = true; clipToPadding = false }
    private val memLine = TextView(context).apply {
        textSize = 15f; typeface = Fonts.medium; setTextColor(0xCCFFFFFF.toInt()); setPadding(dpi(6f), 0, 0, dpi(10f))
    }
    private val status = TextView(context).apply {
        textSize = 14f; typeface = Fonts.regular; setTextColor(0x99FFFFFF.toInt()); setPadding(dpi(6f), dpi(4f), dpi(6f), dpi(8f))
    }
    private val freeRow = RowView(context, "Free cached memory")
    private val allRow = RowView(context, "Clear all")
    private val selRow = RowView(context, "Clear selected")
    private val appsBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = true; clipToPadding = false }

    private var snap: RunningSnapshot? = null
    private val selected = LinkedHashSet<String>()
    private var busy = false
    private var armedAll = false
    private var token = 0

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isSmoothScrollingEnabled = true
        val pad = dpi(22f)
        list.setPadding(pad, dpi(8f), pad, dpi(8f))
        addView(list, android.view.ViewGroup.LayoutParams(-1, -2))
        if (android.os.Build.VERSION.SDK_INT >= 23) setOnScrollChangeListener { _, _, _, _, _ -> invalidateDeep(list) }
        list.addView(TextView(context).apply {
            text = "Running apps"; textSize = 26f; typeface = Fonts.medium; setTextColor(0xFFFFFFFF.toInt()); setPadding(dpi(6f), 0, 0, dpi(6f))
        })
        list.addView(memLine)
        val rowLp = { LinearLayout.LayoutParams(-1, dpi(48f)).apply { bottomMargin = dpi(5f) } }
        list.addView(freeRow, rowLp())
        list.addView(allRow, rowLp())
        list.addView(selRow, rowLp())
        list.addView(status)
        list.addView(appsBox, LinearLayout.LayoutParams(-1, -2))
        freeRow.valueView.text = "Cached apps"
        freeRow.onClick = { run("Freeing cached memory…", emptyList(), killCached = true) }
        allRow.onClick = { onClearAll() }
        selRow.onClick = { onClearSelected() }
        updateActions()
    }

    /** Called when the page is shown: reads what runs and focuses the first action. */
    fun open() {
        selected.clear()
        armedAll = false
        snap = null
        appsBox.removeAllViews()
        updateActions()
        scrollTo(0, 0)
        refresh()
        post { freeRow.requestFocus() }
    }

    /** Called when the page is left: results of anything still running are dropped. */
    fun reset() { token++; busy = false; armedAll = false }

    private fun connect(): AdbClient = AdbClient.connect(context, onPrompt = {
        ui.post { status.text = "Choose Allow on the TV's prompt (tick Always allow)." }
    })

    private fun refresh() = run(null, emptyList(), killCached = false)

    /**
     * Optionally stops [pkgs] and/or cached processes, then (re)reads what runs, all on one ADB connection.
     * [message] is shown while it works; without it this is only a read.
     */
    private fun run(message: String?, pkgs: List<String>, killCached: Boolean) {
        if (busy) return
        busy = true
        armedAll = false
        val t = ++token
        val before = Running.memory()?.first
        status.text = message ?: "Reading what is running…"
        updateActions()
        Thread {
            var s: RunningSnapshot? = null
            var err: String? = null
            try {
                connect().use { adb ->
                    if (message != null) {
                        Running.clear(adb, pkgs, killCached)
                        Thread.sleep(1500)
                    }
                    s = Running.load(context, adb)
                }
            } catch (e: Throwable) { err = Running.error(e) }
            val after = Running.memory()?.first
            ui.post {
                if (t != token) return@post
                busy = false
                if (err != null) { status.text = err; snap = null; appsBox.removeAllViews(); updateActions(); return@post }
                snap = s
                selected.retainAll(s!!.apps.map { it.pkg }.toSet())
                if (message != null && before != null && after != null) {
                    val freed = (after - before) / 1024
                    Toast.makeText(context, if (freed > 0) "Freed about $freed MB" else "Done", Toast.LENGTH_SHORT).show()
                }
                showApps()
            }
        }.start()
    }

    private fun showApps() {
        val s = snap ?: return
        appsBox.removeAllViews()
        for (a in s.apps) {
            val row = RowView(context, a.label)
            row.titleView.textSize = 17f
            row.titleView.maxLines = 1
            row.titleView.ellipsize = TextUtils.TruncateAt.END
            row.valueView.textSize = 14f
            row.valueView.text = Running.mb(a.pssKb).toString() + " MB" + if (a.services > 0) " · ${a.services} svc" else ""
            row.onClick = { toggle(a, row) }
            paintRow(a, row)
            appsBox.addView(row, LinearLayout.LayoutParams(-1, dpi(48f)).apply { bottomMargin = dpi(5f) })
        }
        val m = Running.memory()
        memLine.text = (if (m != null) "${Running.mb(m.first)} MB free of ${Running.mb(m.second)} MB" else "Memory") +
            if (s.systemKb > 0) "  ·  System ${Running.mb(s.systemKb)} MB" else ""
        status.text = if (s.apps.isEmpty()) "Nothing besides the system is running." else "OK marks an app. Then choose Clear selected."
        updateActions()
        invalidateDeep(list)
    }

    private fun paintRow(a: RunningApp, row: RowView) {
        row.titleView.text = (if (a.pkg in selected) "✓  " else "") + a.label
    }

    private fun toggle(a: RunningApp, row: RowView) {
        if (busy) return
        if (!selected.remove(a.pkg)) selected.add(a.pkg)
        armedAll = false
        paintRow(a, row)
        updateActions()
    }

    private fun updateActions() {
        val n = snap?.apps?.size ?: 0
        selRow.valueView.text = if (selected.isEmpty()) "None marked" else "${selected.size} marked"
        allRow.titleView.text = if (armedAll) "Press OK again to stop all $n" else "Clear all"
        allRow.valueView.text = if (n == 0) "" else "$n apps"
    }

    private fun onClearSelected() {
        if (busy) return
        if (selected.isEmpty()) { Toast.makeText(context, "Mark apps with OK first", Toast.LENGTH_SHORT).show(); return }
        run("Stopping ${selected.size} apps…", selected.toList(), killCached = true)
    }

    /** Stopping every app listed asks for a second OK, since it ends playback and downloads in those apps. */
    private fun onClearAll() {
        val apps = snap?.apps ?: return
        if (busy || apps.isEmpty()) return
        if (!armedAll) {
            armedAll = true
            updateActions()
            ui.postDelayed({ if (armedAll) { armedAll = false; updateActions() } }, 4000)
            return
        }
        run("Stopping ${apps.size} apps…", apps.map { it.pkg }, killCached = true)
    }
}
