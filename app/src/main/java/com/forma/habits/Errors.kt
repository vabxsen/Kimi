package com.forma.habits

import android.content.Context

/**
 * A failure that already knows which piece of user-facing copy explains it.
 *
 * Carrying the string resource id rather than the finished sentence keeps validation in
 * [BackupCodec] and [HabitStore] free of any Android [Context] — so the JVM unit tests can keep
 * exercising them directly — while still letting the UI render the message in the user's language.
 */
class KimiMessage(val resId: Int, vararg val args: Any) : Exception("Kimi message $resId")

/** [require], but the failure survives translation. */
internal fun demand(condition: Boolean, resId: Int, vararg args: Any) {
    if (!condition) throw KimiMessage(resId, *args)
}

/** Resolves any failure into something worth showing a person. */
fun Context.kimiMessage(error: Throwable, fallback: Int = R.string.err_generic_save): String = when (error) {
    is KimiMessage -> getString(error.resId, *error.args)
    else -> getString(fallback)
}
