package app.mystery0.nodeflow.feature.replyeditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

fun insertFloorReference(
    value: TextFieldValue,
    username: String,
    floor: Int,
): TextFieldValue {
    val selection = value.selection.clamped(value.text.length)
    val token = "@${username.trim()} #$floor "
    if (selection.collapsed && value.text.substring(0, selection.start).endsWith(token)) {
        return value
    }
    return replaceSelection(value, selection, token)
}

fun insertImageUrl(value: TextFieldValue, url: String): TextFieldValue {
    val selection = value.selection.clamped(value.text.length)
    val prefix = if (selection.start > 0 && value.text[selection.start - 1] != '\n') "\n" else ""
    val suffix = if (selection.end < value.text.length && value.text[selection.end] != '\n') "\n" else ""
    return replaceSelection(value, selection, "$prefix$url$suffix")
}

private fun replaceSelection(
    value: TextFieldValue,
    selection: TextRange,
    replacement: String,
): TextFieldValue {
    val text = value.text.replaceRange(selection.start, selection.end, replacement)
    val cursor = selection.start + replacement.length
    return TextFieldValue(text, TextRange(cursor))
}

private fun TextRange.clamped(textLength: Int) = TextRange(
    start.coerceIn(0, textLength),
    end.coerceIn(0, textLength),
)
