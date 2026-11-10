package io.github.abhishekkaisar.glassos.personal

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build

/** One external input of the TV (HDMI, AV, component) that can be switched to. */
class TvInput(val id: String, val label: String)

/** The TV's external inputs, and switching to one through the Live TV app, which handles the passthrough URI. */
object TvInputs {
    /** HDMI first, then the others, each group by name; empty on TVs with no passthrough input (Fire TV). */
    fun list(ctx: Context): List<TvInput> = try {
        val tm = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        tm.tvInputList.filter { it.isPassthroughInput }
            .map { TvInput(it.id, labelOf(ctx, it)) }
            .sortedWith(compareBy({ !it.label.startsWith("HDMI", true) }, { it.label.lowercase() }))
    } catch (e: Throwable) { emptyList() }

    private fun labelOf(ctx: Context, info: TvInputInfo): String {
        // loadCustomLabel (the name the user gave the input) needs API 24; older TVs show the standard label.
        val custom = if (Build.VERSION.SDK_INT >= 24) try { info.loadCustomLabel(ctx)?.toString() } catch (e: Throwable) { null } else null
        val label = custom?.takeIf { it.isNotBlank() } ?: try { info.loadLabel(ctx)?.toString() } catch (e: Throwable) { null }
        return label?.takeIf { it.isNotBlank() } ?: fallbackLabel(info.id)
    }

    /** "com.x/.services.Hdmi2InputService/HW6" -> "HDMI 2", "AV1InputService" -> "AV 1". */
    private fun fallbackLabel(id: String): String {
        val m = Regex("(Hdmi|AV|Component)(\\d+)", RegexOption.IGNORE_CASE).find(id) ?: return id.substringAfterLast('/')
        val name = if (m.groupValues[1].equals("hdmi", true)) "HDMI" else m.groupValues[1]
        return name + " " + m.groupValues[2]
    }

    fun intent(input: TvInput): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
