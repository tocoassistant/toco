package com.toco.ai.skill.builtin

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.AppFinder

/**
 * "search youtube for lofi", "youtube minecraft", "search google for X",
 * "find headphones on amazon", "search maps for petrol pump"
 *
 * Uses each app's official search deep link, which is far more reliable than
 * driving the UI: the app opens already showing results. What this CANNOT do is
 * then tap a result — that needs an AccessibilityService, and TOCO says so
 * rather than pretending.
 */
class SearchSkill : Skill {

    override val id = "core.search"
    override val name = "In-App Search"

    private data class Target(
        val keywords: List<String>,
        val label: String,
        val packageName: String?,
        val uri: (String) -> String
    )

    private val targets = listOf(
        Target(listOf("youtube", "yt"), "YouTube", "com.google.android.youtube") {
            "https://www.youtube.com/results?search_query=" + Uri.encode(it)
        },
        Target(listOf("play store", "playstore", "play"), "Play Store", null) {
            "market://search?q=" + Uri.encode(it)
        },
        Target(listOf("maps", "map", "google maps"), "Maps", null) {
            "geo:0,0?q=" + Uri.encode(it)
        },
        Target(listOf("amazon"), "Amazon", null) {
            "https://www.amazon.com/s?k=" + Uri.encode(it)
        },
        Target(listOf("spotify"), "Spotify", "com.spotify.music") {
            "https://open.spotify.com/search/" + Uri.encode(it)
        },
        Target(listOf("google", "web", "internet"), "Google", null) {
            "https://www.google.com/search?q=" + Uri.encode(it)
        }
    )

    override fun canHandle(command: String): Boolean {
        val c = command.lowercase()
        val searching = c.contains("search") || c.contains("look up") ||
            c.contains("find") || c.contains("look for")
        if (!searching) return false
        return targets.any { t -> t.keywords.any { c.contains(it) } }
    }

    override fun execute(context: Context, command: String): SkillResult {
        val c = command.lowercase()
        val target = targets.firstOrNull { t -> t.keywords.any { c.contains(it) } }
            ?: return SkillResult.Failed("Search where?")

        val query = extractQuery(command, target.keywords)
        if (query.isEmpty()) return SkillResult.Failed("Search ${target.label} for what?")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target.uri(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Prefer the real app when it's installed, so results open in-app.
        if (target.packageName != null && AppFinder.isInstalled(context, target.packageName)) {
            intent.setPackage(target.packageName)
        }

        return try {
            context.startActivity(intent)
            SkillResult.Ok("Searching ${target.label} for \"$query\"")
        } catch (e: Exception) {
            // setPackage can fail if the app can't handle the link; retry open.
            return try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(target.uri(query)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                SkillResult.Ok("Searching ${target.label} for \"$query\"")
            } catch (e2: Exception) {
                SkillResult.Failed("Couldn't open ${target.label}.")
            }
        }
    }

    /** Strips verbs, the app name and connecting words to leave the query. */
    private fun extractQuery(command: String, appWords: List<String>): String {
        var text = command.lowercase()

        for (verb in listOf("search for", "search", "look up", "look for", "find")) {
            text = text.replace(verb, " ")
        }
        for (word in appWords) {
            text = text.replace(word, " ")
        }
        for (word in listOf(" on ", " in ", " for ", " at ", " the ")) {
            text = text.replace(word, " ")
        }

        return text.trim().trim('.', ',', '?').trim()
    }
}
