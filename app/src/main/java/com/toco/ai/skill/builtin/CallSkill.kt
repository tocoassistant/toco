package com.toco.ai.skill.builtin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.toco.ai.skill.Skill
import com.toco.ai.skill.SkillResult
import com.toco.ai.util.CommandText
import com.toco.ai.util.Contacts
import com.toco.ai.util.Permissions
import com.toco.ai.util.PhoneNumbers

/**
 * "call 03001234567", "call mom", "dial ammi", "phone my brother"
 *
 * Two-tier dialing, which is why this now works where it used to fail:
 *
 *   ACTION_CALL  places the call directly, but needs CALL_PHONE and is blocked
 *                outright by some ROMs.
 *   ACTION_DIAL  opens the dialer with the number filled in, needs NO
 *                permission, and cannot be blocked.
 *
 * So TOCO tries the direct call and quietly falls back to the dialer instead of
 * dead-ending. It does not declare CALL_PHONE as required, because the skill is
 * useful without it — declaring it meant the engine refused to run at all.
 */
class CallSkill : Skill {

    override val id = "core.call"
    override val name = "Phone Call"

    private val triggers = listOf("call", "dial", "phone", "ring")

    private companion object {
        const val SPEAKER_WAIT_MS = 20_000L
        const val SPEAKER_POLL_MS = 700L
    }

    // Highest, because "call mom" must never be read as anything else.
    override val priority = 95

    override fun canHandle(command: String): Boolean {
        if (CommandText.hasAnyPhrase(command, listOf("whatsapp", "imo", "message"))) {
            return false
        }

        // Anything referring to the call log belongs to CallHistorySkill.
        // Without this, "call last caller" was read as calling someone named
        // "last caller" and fell through to a contact search.
        if (CommandText.hasAnyPhrase(
                command,
                listOf("last caller", "last call", "last missed", "missed caller",
                       "recent caller", "recent call", "previous caller")
            )
        ) {
            return false
        }

        // A bare "call back" with nobody named means the last caller, which
        // is CallHistorySkill's job. With a name after it, it is ours.
        val c = CommandText.normalize(command)
        if (c == "call back" || c == "call again" || c == "redial") return false

        return CommandText.startsWithVerb(command, triggers)
    }

    override fun execute(context: Context, command: String): SkillResult {
        if (CommandText.hasAnyPhrase(command, listOf("speaker", "speakerphone", "loudspeaker"))) {
            armSpeaker(context)
        }

        val literal = PhoneNumbers.extract(command)
        if (literal != null) {
            return dial(context, PhoneNumbers.toDialable(literal), literal)
        }

        val name = Contacts.nameFrom(command, triggers)
        if (name.isEmpty()) return SkillResult.Failed("Who should I call?")

        if (!Permissions.has(context, Manifest.permission.READ_CONTACTS)) {
            return SkillResult.NeedsPermission(
                Manifest.permission.READ_CONTACTS,
                "To call \"$name\" I need your contacts."
            )
        }

        val matches = Contacts.findAll(context, name)

        if (matches.isEmpty()) {
            return SkillResult.Failed("No contact matching \"$name\".")
        }

        // One saved name, several numbers: ask rather than guess wrong.
        if (matches.size > 1) {
            return SkillResult.Choose(
                prompt = "Which $name?",
                options = matches.map { match ->
                    SkillResult.Choose.Option(
                        label = match.name,
                        detail = match.number,
                        // Dialling by number skips the lookup second time round.
                        command = "call " + match.number
                    )
                }
            )
        }

        val match = matches.first()
        return dial(context, PhoneNumbers.toDialable(match.number), match.name)
    }

    /**
     * "call mom on speaker" — the speaker cannot be switched on before the
     * call exists, so the request is remembered and applied once the audio
     * mode shows a call in progress. Giving up after a few seconds avoids
     * flipping the speaker on during some later, unrelated call.
     */
    private fun armSpeaker(context: Context) {
        val app = context.applicationContext
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + SPEAKER_WAIT_MS

        fun attempt() {
            val audio = app.getSystemService(Context.AUDIO_SERVICE)
                as? android.media.AudioManager ?: return

            val inCall = try {
                audio.mode == android.media.AudioManager.MODE_IN_CALL ||
                    audio.mode == android.media.AudioManager.MODE_IN_COMMUNICATION
            } catch (e: Exception) {
                false
            }

            if (inCall) {
                try {
                    audio.isSpeakerphoneOn = true
                } catch (e: Exception) {
                    // Some ROMs refuse; the call still connects normally.
                }
                return
            }

            if (System.currentTimeMillis() < deadline) {
                handler.postDelayed({ attempt() }, SPEAKER_POLL_MS)
            }
        }

        handler.postDelayed({ attempt() }, SPEAKER_POLL_MS)
    }

    private fun dial(context: Context, number: String, label: String): SkillResult {
        // Direct call, if we're allowed.
        if (Permissions.has(context, Manifest.permission.CALL_PHONE)) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return SkillResult.Ok("Calling $label")
            } catch (e: Exception) {
                // ROM blocked it — fall through to the dialer.
            }
        }

        return try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            SkillResult.Ok("Dialer ready for $label — press call")
        } catch (e: Exception) {
            SkillResult.Failed("No dialer app on this phone.")
        }
    }
}
