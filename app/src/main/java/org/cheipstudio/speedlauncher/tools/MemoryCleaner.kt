package org.cheipstudio.speedlauncher.tools

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import org.cheipstudio.speedlauncher.R

/**
 * v59: Pulitore memoria. Onestà tecnica:
 * - Android NON permette di "killare" tutte le app come fanno alcuni "task killer".
 * - Ciò che possiamo fare:
 *   1) System.gc() del nostro processo (rilascia RAM nostra)
 *   2) ActivityManager.killBackgroundProcesses(pkg) per ogni app installata.
 *      Richiede permission KILL_BACKGROUND_PROCESSES (normal permission, granted di default).
 *      Il sistema effettivamente killa solo i processi background non protetti.
 *   3) Riportiamo memoria libera prima/dopo come "memoria pulita" — valore reale.
 */
object MemoryCleaner {

    /**
     * @return MB liberati (può essere 0 o negativo se sistema riassegna velocemente)
     */
    fun clean(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val mi1 = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi1)
        val before = mi1.availMem / (1024 * 1024)

        // 1. GC nostro processo
        try { System.gc() } catch (_: Throwable) {}
        try { Runtime.getRuntime().gc() } catch (_: Throwable) {}

        // 2. Kill background di tutti i processi (eccetto noi e quelli di sistema critici)
        try {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(0)
            val ownPkg = context.packageName
            for (app in installed) {
                val pkg = app.packageName
                if (pkg == ownPkg) continue
                if (pkg.startsWith("android") && !pkg.contains("provider")) continue
                if (pkg.startsWith("com.android.systemui")) continue
                try { am.killBackgroundProcesses(pkg) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        // 3. Trim cache nostra
        try {
            context.cacheDir?.let { trim(it) }
            context.codeCacheDir?.let { trim(it) }
        } catch (_: Throwable) {}

        // 4. Misuro dopo
        val mi2 = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi2)
        val after = mi2.availMem / (1024 * 1024)

        return (after - before).coerceAtLeast(0L)
    }

    private fun trim(dir: java.io.File) {
        if (!dir.exists() || !dir.isDirectory) return
        for (f in dir.listFiles() ?: emptyArray()) {
            try {
                if (f.isDirectory) trim(f)
                else f.delete()
            } catch (_: Throwable) {}
        }
    }
}
