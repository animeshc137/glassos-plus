package io.github.abhishekkaisar.glassos.personal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the output of `dumpsys meminfo` and `dumpsys activity services` into per-package figures. Plain Java with
 * no Android types, so the JVM checks in CI can run it against captured output.
 */
public final class RunningParse {
    /** What one package is using: memory of all its processes, and its running services. */
    public static final class Usage {
        public final String pkg;
        public long pssKb;
        public boolean hasActivity;
        public int services;
        public int foregroundServices;
        Usage(String pkg) { this.pkg = pkg; }
    }

    // "    65,591K: io.github.x.personal (pid 11894 / activities)" or "    10,184K: com.xiaomi.mitv.updateservice (pid 10019)"
    private static final Pattern PROCESS = Pattern.compile("^\\s*([\\d,]+)K: (\\S+) \\(pid (\\d+)(?: / ([^)]*))?\\)\\s*$");
    // "  * ServiceRecord{835fe9b u0 com.google.android.gms/.play.SomeService}"
    private static final Pattern SERVICE = Pattern.compile("ServiceRecord\\{\\S+ u0 ([^/\\s]+)/");
    private static final String PSS_HEADER = "Total PSS by process:";

    private RunningParse() { }

    /** A process name ("com.android.vending:background") belongs to its package ("com.android.vending"). */
    public static String packageOf(String process) {
        int i = process.indexOf(':');
        return i > 0 ? process.substring(0, i) : process;
    }

    /** Adds the "Total PSS by process" section of `dumpsys meminfo` to [into]. */
    public static void meminfo(String text, Map<String, Usage> into) {
        boolean inSection = false;
        for (String line : text.split("\n")) {
            if (!inSection) {
                if (line.trim().equals(PSS_HEADER)) inSection = true;
                continue;
            }
            Matcher m = PROCESS.matcher(line.replace("\r", ""));
            if (!m.matches()) {
                if (line.trim().isEmpty()) break;
                continue;
            }
            String pkg = packageOf(m.group(2));
            Usage u = into.get(pkg);
            if (u == null) { u = new Usage(pkg); into.put(pkg, u); }
            u.pssKb += Long.parseLong(m.group(1).replace(",", ""));
            String state = m.group(4);
            if (state != null && state.contains("activities")) u.hasActivity = true;
        }
    }

    /** Adds the services of `dumpsys activity services` to [into] (user 0 only). */
    public static void services(String text, Map<String, Usage> into) {
        Usage current = null;
        for (String line : text.split("\n")) {
            Matcher m = SERVICE.matcher(line);
            if (m.find()) {
                String pkg = m.group(1);
                current = into.get(pkg);
                if (current == null) { current = new Usage(pkg); into.put(pkg, current); }
                current.services++;
            } else if (current != null && line.contains("isForeground=true")) {
                current.foregroundServices++;
            }
        }
    }

    public static Map<String, Usage> parse(String meminfo, String services) {
        Map<String, Usage> out = new LinkedHashMap<>();
        meminfo(meminfo, out);
        services(services, out);
        return out;
    }

    /** A package name that is safe to put in a shell command. */
    public static boolean isPackageName(String s) {
        return s != null && s.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }
}
