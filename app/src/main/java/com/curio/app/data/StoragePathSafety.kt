package com.curio.app.data

import java.io.File

/**
 * Validates a value before it is used as one app-private storage path segment.
 * Curio capture IDs are generated identifiers, not user-selected paths.
 */
internal fun isSafeStorageSegment(value: String): Boolean =
    value.isNotBlank() &&
        value.length <= 128 &&
        !value.contains('/') &&
        !value.contains('\\') &&
        value != "." &&
        value != ".."

/** Returns true only when [candidate] resolves below [root]. */
internal fun isContainedFile(root: File, candidate: File): Boolean {
    val rootPath = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
    return candidate.canonicalPath.startsWith(rootPath)
}
