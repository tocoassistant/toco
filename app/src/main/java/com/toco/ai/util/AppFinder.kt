package com.toco.ai.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Resolves a spoken app name ("open whatsapp") to a launchable package.
 * Only queries launcher apps, so no QUERY_ALL_PACKAGES permission is needed.
 */
object AppFinder {

    data class Entry(val label: String, val packageName: String)

    fun launchableApps(context: Context): List<Entry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.loadLabel(pm).toString()
            Entry(label, pkg)
        }.distinctBy { it.packageName }
    }

    /** Best-effort match: exact label, then starts-with, then contains. */
    fun find(context: Context, query: String): Entry? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val apps = launchableApps(context)
        return apps.firstOrNull { it.label.lowercase() == q }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(q) }
            ?: apps.firstOrNull { it.label.lowercase().contains(q) }
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
}
