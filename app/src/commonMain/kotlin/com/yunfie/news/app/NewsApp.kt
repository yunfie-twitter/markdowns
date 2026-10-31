package com.yunfie.news.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import com.yunfie.pagemarkdown.PageToMarkdown
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private val Blue = Color(0xFF145D78)
private val Teal = Color(0xFF1B796F)

private data class NewsStory(
    val id: String,
    val title: String,
    val source: String,
    val category: String,
    val time: String,
    val summary: String,
    val url: String,
    val featured: Boolean = false
)

private val stories = listOf(
    NewsStory(
        id = "zagitova",
        title = "「ほとんど隠れてない」ザギトワのモードなドレス姿に反響",
        source = "デイリースポーツ",
        category = "スポーツ",
        time = "6:00",
        summary = "2018年平昌五輪フィギュアスケート女子金メダリストのアリーナ・ザギトワさんが、ブラックワントーンのドレス姿を公開。記事では写真とともに反響を紹介しています。",
        url = "https://news.yahoo.co.jp/articles/189597f43857ace3cd8fabbf15d39d06730bcdbf",
        featured = true
    ),
    NewsStory(
        id = "codex-tools",
        title = "Codex のいいところだけを、Claude Code や Muse から使う",
        source = "Qiita · tatsuya582",
        category = "テクノロジー",
        time = "昨日",
        summary = "codex app-server の MCP 呼び出しを使い、Computer Use や Meetings などの機能を別の AI エージェントから利用する方法を、コード例と注意点とともに解説しています。",
        url = "https://qiita.com/tatsuya582/items/4d79021c9f78f4015992"
    )
)

@Composable
fun NewsApp() {
    val themeController = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = themeController) {
        var selectedStory by remember { mutableStateOf<NewsStory?>(null) }
        var markdownResult by remember { mutableStateOf<Pair<String, String>?>(null) }
        var selectedTab by remember { mutableStateOf(0) }
        var category by remember { mutableStateOf("すべて") }
        var search by remember { mutableStateOf("") }
        var articleUrl by remember { mutableStateOf("") }
        var urlLoading by remember { mutableStateOf(false) }
        var urlError by remember { mutableStateOf<String?>(null) }
        val saved = remember { mutableStateOf(setOf<String>()) }
        val scope = rememberCoroutineScope()

        if (markdownResult != null) {
            MarkdownResult(markdownResult!!.first, markdownResult!!.second) { markdownResult = null }
        } else if (selectedStory != null) {
            StoryDetail(
                story = selectedStory!!,
                isSaved = selectedStory!!.id in saved.value,
                onBack = { selectedStory = null },
                onToggleSaved = { id ->
                    saved.value = if (id in saved.value) saved.value - id else saved.value + id
                }
            )
        } else {
            val visibleStories = stories.filter { story ->
                (selectedTab == 0 || story.id in saved.value) &&
                    (category == "すべて" || story.category == category) &&
                    (search.isBlank() || story.title.contains(search, true) || story.source.contains(search, true))
            }
            Scaffold(
                containerColor = MiuixTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(color = MiuixTheme.colorScheme.surface) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = MiuixIcons.VerticalSplit,
                            label = "ホーム"
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = MiuixIcons.Favorites,
                            label = "保存"
                        )
                    }
                }
            ) { inset ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(inset),
                    contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SATURDAY, OCT 31", color = MiuixTheme.colorScheme.onSurfaceContainerVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                                Text("今日のニュース", color = MiuixTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                            Surface(shape = CircleShape, color = Color(0xFFE2EDF1), modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("N", color = Blue, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                    item {
                        TextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = "ニュースを検索",
                            useLabelAsPlaceholder = true
                        )
                    }
                    item {
                        val categories = listOf("すべて", "スポーツ", "テクノロジー")
                        TabRow(
                            tabs = categories,
                            selectedTabIndex = categories.indexOf(category).coerceAtLeast(0),
                            onTabSelected = { category = categories[it] },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Card(
                            cornerRadius = 20.dp,
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("URLから記事を読む", color = MiuixTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("ニュース記事やWebページをMarkdownに変換できます。", color = MiuixTheme.colorScheme.onSurfaceContainerVariant, fontSize = 13.sp)
                                TextField(
                                    value = articleUrl,
                                    onValueChange = { articleUrl = it; urlError = null },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = "記事のURL",
                                    useLabelAsPlaceholder = true
                                )
                                Button(
                                    onClick = {
                                        val input = articleUrl.trim().let { if ("://" in it) it else "https://$it" }
                                        if (input == "https://" || !(input.startsWith("https://") || input.startsWith("http://"))) {
                                            urlError = "有効な記事URLを入力してください。"
                                        } else {
                                            scope.launch {
                                                urlLoading = true
                                                urlError = null
                                                runCatching { PageToMarkdown.convertUrl(input) }
                                                    .onSuccess { content -> markdownResult = input to content }
                                                    .onFailure { urlError = "ページを取得できませんでした。URLや接続を確認してください。" }
                                                urlLoading = false
                                            }
                                        }
                                    },
                                    enabled = !urlLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (urlLoading) "記事を取得しています…" else "Markdownに変換") }
                                urlError?.let { Text(it, color = Color(0xFFB3261E), fontSize = 13.sp) }
                            }
                        }
                    }
                    if (selectedTab == 0 && category == "すべて" && search.isBlank() && visibleStories.isNotEmpty()) {
                        item {
                            Text("EDITOR'S PICK", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        }
                        item {
                            FeaturedStoryCard(story = visibleStories.firstOrNull { it.featured } ?: visibleStories.first()) {
                                selectedStory = it
                            }
                        }
                        item {
                            Text("最新記事", color = MiuixTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        items(visibleStories.filterNot { it.featured }, key = { it.id }) { story ->
                            StoryCard(story) { selectedStory = story }
                        }
                    } else {
                        item {
                            Text(if (selectedTab == 1) "保存した記事" else "記事一覧", color = MiuixTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        items(visibleStories, key = { it.id }) { story ->
                            StoryCard(story) { selectedStory = story }
                        }
                    }
                    if (visibleStories.isEmpty()) {
                        item {
                            Text("記事が見つかりません。検索語やカテゴリを変えてください。", color = MiuixTheme.colorScheme.onSurfaceContainerVariant, modifier = Modifier.padding(vertical = 28.dp))
                        }
                    }
                    item {
                        Text("記事データはサンプルです。", color = MiuixTheme.colorScheme.onSurfaceContainerVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownResult(url: String, markdown: String, onBack: () -> Unit) {
    Scaffold(containerColor = MiuixTheme.colorScheme.background) { inset ->
        Column(modifier = Modifier.fillMaxSize().padding(inset).padding(horizontal = 20.dp)) {
            TextButton(text = "←  ニュース一覧", onClick = onBack, modifier = Modifier.padding(vertical = 12.dp))
            Text("Markdown", color = MiuixTheme.colorScheme.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(url, color = MiuixTheme.colorScheme.onSurfaceContainerVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Surface(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MiuixTheme.colorScheme.surface) {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    item {
                        SelectionContainer {
                            Text(markdown, color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedStoryCard(story: NewsStory, onClick: (NewsStory) -> Unit) {
    Card(
        onClick = { onClick(story) },
        cornerRadius = 24.dp,
        colors = CardDefaults.defaultColors(color = Color(0xFF173849)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(story.category.uppercase(), color = Color(0xFF9BD6CE), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f))
                Text(story.time, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(40.dp))
            Text("FEATURED STORY", color = Color(0xFF9BD6CE), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))
            Text(story.title, color = Color.White, fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(story.summary, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Text("${story.source}  ·  続きを読む →", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StoryCard(story: NewsStory, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(if (story.category == "スポーツ") Color(0xFFE3EEF2) else Color(0xFFE3F0EA)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (story.category == "スポーツ") "氷" else "AI", color = if (story.category == "スポーツ") Blue else Teal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${story.category}  ·  ${story.time}", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(story.title, color = MiuixTheme.colorScheme.onBackground, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Text(story.source, color = MiuixTheme.colorScheme.onSurfaceContainerVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StoryDetail(
    story: NewsStory,
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleSaved: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var markdown by remember(story.id) { mutableStateOf<String?>(null) }
    var loading by remember(story.id) { mutableStateOf(false) }
    var error by remember(story.id) { mutableStateOf<String?>(null) }

    Scaffold(containerColor = MiuixTheme.colorScheme.background) { inset ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inset),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                TextButton(text = "←  ニュース一覧", onClick = onBack)
            }
            item {
                Text("${story.category}  ·  ${story.source}  ·  ${story.time}", color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(story.title, color = MiuixTheme.colorScheme.onBackground, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            }
            item {
                Card(cornerRadius = 20.dp, colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)) {
                    Text(story.summary, modifier = Modifier.padding(20.dp), color = MiuixTheme.colorScheme.onBackground, fontSize = 16.sp, lineHeight = 27.sp)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { uriHandler.openUri(story.url) }) { Text("元記事を開く") }
                    TextButton(text = if (isSaved) "✓ 保存済み" else "☆ 保存", onClick = { onToggleSaved(story.id) })
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            markdown = null
                            runCatching { PageToMarkdown.convertUrl(story.url) }
                                .onSuccess { markdown = it }
                                .onFailure { error = "記事を取得できませんでした。このサイトへの接続を確認してください。" }
                            loading = false
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "記事を取得しています…" else "記事をMarkdownに変換")
                }
            }
            error?.let { message -> item { Text(message, color = Color(0xFFB3261E)) } }
            markdown?.let { converted ->
                item {
                    Text("Markdown", color = MiuixTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = MiuixTheme.colorScheme.surface) {
                        SelectionContainer {
                            Text(converted, modifier = Modifier.padding(16.dp), color = MiuixTheme.colorScheme.onBackground, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                }
            }
        }
    }
}
