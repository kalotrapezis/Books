package com.kalotrapezis.books.data

import org.json.JSONObject

/** The formats Foliate offers alongside JSON, written the same way it writes them. */
enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    JSON("JSON", "json", "application/json"),
    HTML("HTML", "html", "text/html"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    ORG("ORG", "org", "text/plain"),
}

object AnnotationExport {
    fun render(
        format: ExportFormat,
        title: String,
        annotations: List<JSONObject>,
        json: () -> String,
    ): String {
        val heading = "Annotations for “$title”"
        val total = if (annotations.size == 1) "1 Annotation" else "${annotations.size} Annotations"
        return when (format) {
            ExportFormat.JSON -> json()
            ExportFormat.HTML -> html(heading, total, annotations)
            ExportFormat.MARKDOWN -> markdown(heading, total, annotations)
            ExportFormat.ORG -> org(heading, total, annotations)
        }
    }

    private fun html(title: String, total: String, annotations: List<JSONObject>) = buildString {
        append("<!DOCTYPE html>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>${title.escapeHtml()}</title>\n")
        append(
            """
            <style>
                body { max-width: 36em; padding: 1em; margin: auto; }
                header { text-align: center; }
                section { border-top: 1px solid; }
                .cfi { font-size: small; opacity: 0.5; font-family: monospace; }
                blockquote { margin-inline-start: 0; padding-inline-start: 1em;
                    border-inline-start: .5em solid; }
                .note { white-space: pre-wrap; }
            </style>

            """.trimIndent(),
        )
        append("<header><h1>${title.escapeHtml()}</h1><p>$total</p></header>")
        for (item in annotations) {
            val color = item.optString("color").ifBlank { "yellow" }
            append("<section>\n    <p class=\"cfi\">${item.optString("value").escapeHtml()}</p>\n")
            append("    <blockquote style=\"border-color: ${color.escapeHtml()}\">\n")
            append("        <span class=\"${color.escapeHtml()}\">")
            append(item.optString("text").escapeHtml())
            append("</span>\n    </blockquote>\n")
            val note = item.optString("note")
            if (note.isNotBlank()) append("    <p class=\"note\">${note.escapeHtml()}</p>\n")
            append("</section>")
        }
    }

    private fun markdown(title: String, total: String, annotations: List<JSONObject>) =
        buildString {
            append("# $title\n\n$total")
            for (item in annotations) {
                append("\n\n---\n\n")
                append("**${item.optString("color").ifBlank { "yellow" }}** - ")
                append("`${item.optString("value")}`\n\n")
                append("> ${item.optString("text").escapeMarkdown()}")
                val note = item.optString("note")
                if (note.isNotBlank()) append("\n\n${note.escapeMarkdown()}")
            }
        }

    private fun org(title: String, total: String, annotations: List<JSONObject>) = buildString {
        append("* $title\n$total\n")
        for (item in annotations) {
            append("\n-----\n\n")
            append("*${item.optString("color").ifBlank { "yellow" }}* - ")
            append("`${item.optString("value")}`\n\n")
            append("#+begin_quote\n${item.optString("text")}\n#+end_quote\n")
            val note = item.optString("note")
            if (note.isNotBlank()) append("$note\n")
        }
    }
}

private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun String.escapeMarkdown() = replace(Regex("[<>&]")) { "\\${it.value}" }
