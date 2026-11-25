package com.noveltoon.app.data.parser

enum class NovelAccessKind {
    SUCCESS,
    NO_RESULT,
    RULE_ERROR,
    NETWORK_ERROR,
    NO_SOURCE
}

data class NovelAccess<T>(
    val kind: NovelAccessKind,
    val value: T,
    val detail: String = ""
) {
    companion object {
        fun <T> ok(value: T) = NovelAccess(NovelAccessKind.SUCCESS, value)

        fun <T> noResult(value: T, detail: String) =
            NovelAccess(NovelAccessKind.NO_RESULT, value, detail)

        fun <T> rule(value: T, detail: String) =
            NovelAccess(NovelAccessKind.RULE_ERROR, value, detail)

        fun <T> network(value: T, detail: String) =
            NovelAccess(NovelAccessKind.NETWORK_ERROR, value, detail)

        fun <T> noSource(value: T, detail: String) =
            NovelAccess(NovelAccessKind.NO_SOURCE, value, detail)
    }
}

class SourceAccessException(
    val kind: NovelAccessKind,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/** Supplies an already-decoded HTML page. Tests use this to avoid the network. */
interface PageSource {
    suspend fun load(url: String): LoadedPage
}

data class LoadedPage(
    val finalUrl: String,
    val body: String
)
