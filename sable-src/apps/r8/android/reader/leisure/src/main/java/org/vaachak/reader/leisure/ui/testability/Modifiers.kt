package org.vaachak.reader.leisure.ui.testability

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

fun Modifier.tid(id: String): Modifier {
    val trimmed = id.trim()
    if (trimmed.isBlank()) return this
    return testTag(trimmed).semantics {
        contentDescription = trimmed
    }
}

fun Modifier.tids(vararg ids: String): Modifier {
    val filtered = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (filtered.isEmpty()) return this
    return testTag(filtered.first()).semantics {
        this[SemanticsProperties.ContentDescription] = filtered
    }
}

fun Modifier.testState(state: String): Modifier = semantics {
    stateDescription = state
}
