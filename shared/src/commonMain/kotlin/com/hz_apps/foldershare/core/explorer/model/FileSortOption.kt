package com.hz_apps.foldershare.core.explorer.model

enum class SortField {
    NAME,
    SIZE,
    DATE,
    TYPE
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

data class FileSortOption(
    val field: SortField = SortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING,
    val directoriesFirst: Boolean = true,
    val showHiddenFiles: Boolean = false
)
