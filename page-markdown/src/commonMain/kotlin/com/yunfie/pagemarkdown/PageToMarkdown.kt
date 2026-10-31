package com.yunfie.pagemarkdown

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLQueryComponent

/** HTMLページをMarkdownに変換するKotlin Multiplatformライブラリ。 */
object PageToMarkdown {
    private val nonContentSelector = listOf(
        "script", "style", "noscript", "template", "iframe", "svg", "canvas",
        "nav", "footer", "form", "button", "input:not([type=checkbox])", "select", "textarea",
        "[hidden]", "[aria-hidden=true]", "[role=navigation]", "[role=banner]",
        "[role=contentinfo]", "[role=dialog]", "[role=alert]", "[aria-modal=true]",
        ".p-article-google-news", ".ev-article-manual-related", ".c-square-arrow-btn",
        ".inline-gptAd", ".gptAd", ".OUTBRAIN", ".ranking", ".story-card-feed",
        "#post-fav", "#post-wpulike", ".fav-toggle", ".wpulike",
        ".p-toc", ".toc", ".table-of-contents", ".ez-toc-container",
        "ins.adsbygoogle", ".widget_swell_ad_widget", ".c-shareBtns", ".sharedaddy",
        ".jp-relatedposts", ".related-posts", ".related-articles", ".post-navigation",
        ".wp-block-post-navigation-link", ".wp-block-query", ".comments-area", "#comments"
    ).joinToString(", ")

    private val mainContentSelector = listOf(
        "article", "main", "[role=main]", "[itemprop=articleBody]",
        ".article_body", ".article-content", ".article-body", ".article__body",
        ".article__content", ".article__main", ".single-content", ".post-content", ".post_content",
        ".post__content", ".post-body", ".entry-content", ".entry-body", ".entry__content",
        ".wp-block-post-content", ".wp-block-post-content__content", ".td-post-content",
        ".et_pb_post_content", ".elementor-widget-theme-post-content", ".story-body",
        ".page-content", ".page__content", ".main-content", ".content-area", ".site-main",
        ".content-inner", ".article-container", "#primary", "#articleBody", "#article-body", "#article", "#content"
    ).joinToString(", ")

    private val preferredContentSelector = listOf(
        "[itemprop=articleBody]", "#personal-public-article-body", ".mdContent-inner",
        ".it-MdContent", ".article_body", ".article-content", ".article-body",
        ".article__body", ".article__content", ".p-main-contents", ".post_content",
        ".post-content", ".post__content", ".post-body", ".entry-content", ".entry-body",
        ".entry__content", ".wp-block-post-content", ".wp-block-post-content__content",
        ".td-post-content", ".et_pb_post_content", ".elementor-widget-theme-post-content",
        ".story-body", ".article-container", "#post-data", "#post-hentai", "#articleBody", "#article-body"
    ).joinToString(", ")

    private val clutterPattern = Regex(
        "(^|[-_\\s])(ad|ads|advert|advertisement|cookie|consent|popup|modal|breadcrumb|" +
            "navigation|navbar|sidebar|related|recommendations|comments|social|share|subscribe|promo)([-_\\s]|$)",
        RegexOption.IGNORE_CASE
    )

    /** HTML文字列をMarkdownに変換します。baseUrlを指定すると相対URLも絶対URLになります。 */
    fun convert(html: String, baseUrl: String = ""): String {
        val document = Ksoup.parse(html, baseUrl)
        replaceEmbedsWithLinks(document)
        document.select("p:has(ins.adsbygoogle)").remove()
        document.select(nonContentSelector).remove()
        removeLikelyClutter(document)

        val body = document.body() ?: document
        val content = chooseMainContent(
            body,
            document.select(preferredContentSelector),
            document.select(mainContentSelector)
        )
        val rendered = renderChildren(content, 0).trim()
        val pageTitle = document.selectFirst("article h1, main h1, [itemprop=headline]")?.text()?.trim().orEmpty()
            .ifBlank { document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty() }
            .ifBlank { document.selectFirst("h1")?.text()?.trim().orEmpty() }
            .ifBlank { document.title().substringBefore(" - ").trim() }
        val subtitle = document.selectFirst(".article-subheadline, .article-header h2")
            ?.text()?.trim().orEmpty()
        val publicationTime = document.selectFirst("article time[datetime], main time[datetime], time[datetime]")
            ?.text()?.trim().orEmpty()
            .ifBlank { document.selectFirst("meta[property=article:published_time]")?.attr("content")?.trim().orEmpty() }
        val hasHeading = content.selectFirst("h1") != null
        val hasSubtitle = content.selectFirst(".article-subheadline, .article-header h2") != null
        val hasTime = content.selectFirst("time[datetime]") != null
        val withTitle = buildString {
            if (pageTitle.isNotBlank() && !hasHeading) append("# $pageTitle\n\n")
            if (subtitle.isNotBlank() && !hasSubtitle) append("## $subtitle\n\n")
            if (publicationTime.isNotBlank() && !hasTime) append("$publicationTime\n\n")
            append(rendered)
        }

        return normalizeMarkdown(withTitle)
    }

    /** URLからHTMLを取得してMarkdownに変換します。 */
    suspend fun convertUrl(url: String): String {
        val client = HttpClient()
        return try {
            convert(client.get(url).bodyAsText(), url)
        } finally {
            client.close()
        }
    }

    private fun removeLikelyClutter(document: com.fleeksoft.ksoup.nodes.Document) {
        val elements = document.select("[id], [class]").toList()
        elements.asReversed().forEach { element ->
            val descriptor = "${element.id()} ${element.className()}"
            val hiddenByStyle = Regex("(?:^|;)\\s*(?:display\\s*:\\s*none|visibility\\s*:\\s*hidden)", RegexOption.IGNORE_CASE)
                .containsMatchIn(element.attr("style"))
            if (hiddenByStyle || clutterPattern.containsMatchIn(descriptor)) element.remove()
        }
        document.select("body > header, aside").remove()
        document.select(".site-header, .site-footer, .entry-footer, .post-footer, .article-footer, .author-box, .post-author, .wp-block-post-author").remove()
    }

    private fun replaceEmbedsWithLinks(document: com.fleeksoft.ksoup.nodes.Document) {
        document.select("iframe").toList().forEach { frame ->
            val embeddedUrl = frame.attr("data-content").takeIf { it.isNotBlank() }
                ?.let { encoded ->
                    runCatching { encoded.replace("+", "%2B").decodeURLQueryComponent() }.getOrDefault(encoded)
                }
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: frame.absUrl("src").takeIf { it.startsWith("https://") || it.startsWith("http://") }
            val label = frame.attr("title").ifBlank { "埋め込みコンテンツ" }
            if (embeddedUrl.isNullOrBlank()) frame.remove()
            else frame.replaceWith(TextNode("[$label]($embeddedUrl)"))
        }
    }

    private fun chooseMainContent(
        body: Element,
        preferredCandidates: List<Element>,
        generalCandidates: List<Element>
    ): Element {
        val preferred = preferredCandidates
            .distinct()
            .filter { it !== body && it.text().length >= 80 }
        preferred.firstOrNull { it.id() == "personal-public-article-body" }?.let { return it }
        preferred.firstOrNull { it.id() == "post-data" }?.let { return it }
        preferred.firstOrNull { it.hasClass("p-main-contents") }?.let { return it }
        preferred.firstOrNull { it.hasClass("post_content") }?.let { return it }
        preferred.firstOrNull { it.hasClass("wp-block-post-content") }?.let { return it }
        preferred.firstOrNull { it.hasClass("entry-content") }?.let { return it }
        preferred.firstOrNull { it.hasClass("td-post-content") || it.hasClass("et_pb_post_content") }?.let { return it }
        preferred.firstOrNull { it.hasClass("article-body") }?.let { return it }
        if (preferred.isNotEmpty()) {
            val containers = preferred.filterNot {
                it.normalName() in setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "figure")
            }
            return (containers.ifEmpty { preferred }).maxBy(::contentScore)
        }

        val general = generalCandidates
            .distinct()
            .filter { it !== body && it.text().length >= 80 }
        return general.maxByOrNull(::contentScore) ?: body
    }

    private fun contentScore(element: Element): Double {
        val textLength = element.text().length
        if (textLength == 0) return 0.0
        val linkTextLength = element.select("a").sumOf { it.text().length }
        val linkDensity = (linkTextLength.toDouble() / textLength).coerceIn(0.0, 1.0)
        return textLength * (1.0 - linkDensity * 0.9) +
            element.select("p").size * 100.0 +
            element.select("h1, h2, h3, h4").size * 80.0 +
            element.select("pre, blockquote").size * 60.0
    }

    private fun renderChildren(node: Node, listDepth: Int): String =
        node.childNodes().joinToString("") { render(it, listDepth) }

    private fun render(node: Node, listDepth: Int): String {
        if (node is TextNode) return node.wholeText.replace(Regex("[\\s\\u00a0]+"), " ")
        if (node !is Element) return ""

        return when (val tag = node.normalName()) {
            in setOf("h1", "h2", "h3", "h4", "h5", "h6") -> {
                val level = tag.drop(1).toInt()
                "\n\n${"#".repeat(level)} ${inlineChildren(node).trim()}\n\n"
            }
            "p" -> "\n\n${inlineChildren(node).trim()}\n\n"
            "br" -> "\\\n"
            "hr" -> "\n\n---\n\n"
            "strong", "b" -> wrapInline("**", inlineChildren(node))
            "em", "i", "cite", "dfn" -> wrapInline("*", inlineChildren(node))
            "del", "s", "strike" -> wrapInline("~~", inlineChildren(node))
            "mark" -> wrapInline("==", inlineChildren(node))
            "sup" -> "<sup>${inlineChildren(node).trim()}</sup>"
            "sub" -> "<sub>${inlineChildren(node).trim()}</sub>"
            "abbr" -> {
                val label = inlineChildren(node).trim()
                val title = node.attr("title")
                if (title.isBlank()) label else "$label ($title)"
            }
            "summary" -> wrapInline("**", inlineChildren(node))
            "q" -> "“${inlineChildren(node).trim()}”"
            "kbd" -> "`${inlineChildren(node).trim()}`"
            "code" -> inlineCode(node.text())
            "pre" -> fencedCode(node)
            "a" -> renderLink(node)
            "img" -> renderImage(node)
            "picture" -> {
                val image = node.selectFirst("img")
                if (image == null) renderChildren(node, listDepth) else renderImage(image)
            }
            "ul", "ol" -> renderList(node, listDepth)
            "li" -> "\n${"  ".repeat(listDepth)}- ${inlineChildren(node).trim()}"
            "blockquote" -> renderQuote(node, listDepth)
            "table" -> renderTable(node)
            "figure" -> renderFigure(node, listDepth)
            "figcaption" -> "\n\n*${inlineChildren(node).trim()}*\n\n"
            "dl" -> renderDefinitionList(node, listDepth)
            "dt" -> "\n\n**${inlineChildren(node).trim()}**\n"
            "dd" -> "\n: ${inlineChildren(node).trim()}\n"
            "video", "audio" -> renderMedia(node)
            "div", "main", "article", "section", "header", "aside" ->
                "\n${renderChildren(node, listDepth)}\n"
            else -> renderChildren(node, listDepth)
        }
    }

    private fun wrapInline(marker: String, content: String): String {
        val value = content.trim()
        return if (value.isBlank()) "" else "$marker$value$marker"
    }

    private fun inlineCode(value: String): String {
        val longestTicks = Regex("`+").findAll(value).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(longestTicks + 1)
        val pad = if (value.startsWith(" ") || value.endsWith(" ")) " " else ""
        return "$fence$pad$value$pad$fence"
    }

    private fun renderLink(link: Element): String {
        val label = inlineChildren(link).trim()
        val href = link.absUrl("href").ifBlank { link.attr("href") }.trim()
        if (href.isBlank() || href.startsWith("javascript:", ignoreCase = true)) return label
        if (label.isBlank()) return ""
        val safeHref = href.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
        return "[${label.replace("]", "\\]")}]($safeHref)"
    }

    private fun renderImage(image: Element): String {
        val alt = image.attr("alt").replace("]", "\\]").trim()
        val sourceAttributes = listOf("data-luminous", "src", "data-src", "data-original", "data-lazy-src", "data-url")
        val imageSource = sourceAttributes.asSequence()
            .map { attribute -> image.attr(attribute).trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("data:", ignoreCase = true) }
        val pictureSource = image.parent()?.takeIf { it.normalName() == "picture" }
            ?.selectFirst("source[srcset], source[data-srcset]")
        val srcset = image.attr("srcset")
            .ifBlank { image.attr("data-srcset") }
            .ifBlank { pictureSource?.attr("srcset").orEmpty() }
            .ifBlank { pictureSource?.attr("data-srcset").orEmpty() }
        val source = imageSource?.let { resolveUrl(image.baseUri(), it) }
            ?: firstSrcsetUrl(srcset)?.let { resolveUrl(image.baseUri(), it) }
        val safeSource = source?.replace(" ", "%20")?.replace("(", "%28")?.replace(")", "%29")
        if (safeSource.isNullOrBlank()) return alt
        val markdownImage = "![$alt]($safeSource)"
        return if (image.parents().any { it.id() == "post-hentai" }) {
            "\n\n$markdownImage\n\n"
        } else markdownImage
    }

    private fun firstSrcsetUrl(srcset: String): String? =
        srcset.split(',').firstOrNull()?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }

    private fun resolveUrl(baseUrl: String, value: String): String {
        if (value.startsWith("https://") || value.startsWith("http://")) return value
        val origin = Regex("^(https?://[^/]+)").find(baseUrl)?.groupValues?.get(1) ?: return value
        if (value.startsWith("//")) return "${baseUrl.substringBefore(':')}:$value"
        if (value.startsWith('/')) return origin + value
        val directory = baseUrl.substringBefore('#').substringBefore('?').substringBeforeLast('/', origin)
        val segments = (directory.removePrefix(origin).split('/') + value.split('/')).toMutableList()
        val normalized = mutableListOf<String>()
        segments.forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
                else -> normalized += segment
            }
        }
        return "$origin/${normalized.joinToString("/")}"
    }

    private fun renderList(list: Element, depth: Int): String {
        val ordered = list.normalName() == "ol"
        var number = list.attr("start").toIntOrNull() ?: 1
        val indent = "  ".repeat(depth)
        val rows = list.children().filter { it.normalName() == "li" }.map { item ->
            val nested = item.children().filter { it.normalName() in setOf("ul", "ol") }
            val checkbox = item.children().firstOrNull {
                it.normalName() == "input" && it.attr("type").equals("checkbox", ignoreCase = true)
            }
            val checkPrefix = checkbox?.let { if (it.hasAttr("checked")) "[x] " else "[ ] " }.orEmpty()
            val content = item.childNodes()
                .filterNot { it is Element && (it.normalName() in setOf("ul", "ol") || it === checkbox) }
                .joinToString("") { render(it, depth + 1) }.trim()
            val marker = if (ordered) "${number++}." else "-"
            buildString {
                append(indent).append(marker).append(' ').append(checkPrefix).append(content)
                nested.forEach { append('\n').append(renderList(it, depth + 1).trimEnd()) }
            }
        }
        return if (rows.isEmpty()) "" else "\n\n${rows.joinToString("\n")}\n\n"
    }

    private fun renderQuote(quote: Element, depth: Int): String {
        val text = renderChildren(quote, depth).trim()
        return if (text.isBlank()) "" else "\n\n${text.lines().joinToString("\n") { "> $it"}}\n\n"
    }

    private fun renderFigure(figure: Element, depth: Int): String {
        val caption = figure.selectFirst("figcaption")
        val content = figure.childNodes().filterNot { it === caption }.joinToString("") { render(it, depth) }
        val captionText = caption?.text()?.trim().orEmpty()
        return buildString {
            append("\n\n").append(content.trim())
            if (captionText.isNotBlank()) append("\n\n*${captionText}*")
            append("\n\n")
        }
    }

    private fun renderDefinitionList(list: Element, depth: Int): String =
        "\n\n" + list.children().joinToString("\n") { render(it, depth).trim() } + "\n\n"

    private fun renderMedia(media: Element): String {
        val sourceElement = media.selectFirst("source[src]")
        val source = media.attr("src").ifBlank { sourceElement?.attr("src").orEmpty() }
        val absolute = media.absUrl("src").ifBlank {
            sourceElement?.absUrl("src")?.ifBlank { source } ?: source
        }
        val label = if (media.normalName() == "video") "Video" else "Audio"
        return if (absolute.isBlank()) "" else "\n\n[$label]($absolute)\n\n"
    }

    private fun fencedCode(pre: Element): String {
        val codeElement = pre.selectFirst("code")
        val code = (codeElement?.wholeText() ?: pre.wholeText()).trimEnd()
        val language = pre.parents().firstOrNull { it.hasAttr("data-lang") }?.attr("data-lang")
            ?.takeIf { it.isNotBlank() }
            ?: (codeElement ?: pre).classNames()
            .firstOrNull { it.startsWith("language-") || it.startsWith("lang-") }
            ?.removePrefix("language-")?.removePrefix("lang-").orEmpty()
        val longestTicks = Regex("`+").findAll(code).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longestTicks + 1))
        return "\n\n$fence$language\n$code\n$fence\n\n"
    }

    private fun renderTable(table: Element): String {
        val rows = table.select("tr").filter { it.closest("table") == table }.map { row ->
            row.children().filter { it.normalName() in setOf("th", "td") }
                .map { cell ->
                    cell.text().replace("|", "\\|").replace(Regex("\\s+"), " ").trim()
                        .ifBlank { cell.selectFirst("img")?.attr("alt").orEmpty() }
                }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return ""
        val width = rows.maxOf { it.size }
        val normalized = rows.map { it + List(width - it.size) { "" } }
        val header = normalized.first()
        val separator = List(width) { "---" }
        val body = normalized.drop(1)
        return "\n\n| ${header.joinToString(" | ")} |\n| ${separator.joinToString(" | ")} |" +
            body.joinToString("") { "\n| ${it.joinToString(" | ")} |" } + "\n\n"
    }

    private fun inlineChildren(element: Element): String = element.childNodes().joinToString("") { child ->
        if (child is Element && child.normalName() in setOf("ul", "ol")) render(child, 0)
        else render(child, 0)
    }

    private fun normalizeMarkdown(markdown: String): String = markdown
        .replace('\u00a0', ' ')
        .replace(Regex("[ \\t]+(?=\\n)"), "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
