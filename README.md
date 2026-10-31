# News Reader / Page to Markdown

Compose Multiplatform と Miuix で作ったニュースリーダーと、HTML→Markdown変換ライブラリです。

## 構成

- `page-markdown`: HTML文字列またはURLをMarkdownに変換するKotlin Multiplatformライブラリ
- `app`: AndroidとiOSで動くニュースリーダー。記事一覧、保存、記事詳細、URLからのMarkdown変換を提供

画面UIには [Miuix](https://github.com/compose-miuix-ui/miuix) を使用しています。現在の記事一覧は画面確認用のサンプルデータです。

見出し、段落、リンク、通常画像と遅延読み込み画像、強調、コード、チェックリスト、入れ子リスト、引用、定義リスト、音声・動画リンク、iframe埋め込みへのリンク、水平線、表などに対応しています。QiitaやYahoo!ニュースに加え、WordPressの一般的な投稿構造と、SWELL、ブロックテーマ、Astra/GeneratePress系、Divi、Elementor、Newspaperなどで使われる本文コンテナを優先して抽出します。広告、目次、関連記事、コメント、ナビゲーション、共有欄などの要素を除外してから変換します。

記事URLの読み込みはKtorで行い、取得できたHTMLから本文を抽出します。JavaScript実行後に初めて本文が表示されるページでは、URL変換で本文を取得できないことがあります。

## ライブラリの利用

```kotlin
import com.yunfie.pagemarkdown.PageToMarkdown

val markdown = PageToMarkdown.convert(html, "https://example.com/article")
// URLから取得する場合
val fromUrl = PageToMarkdown.convertUrl("https://example.com/article")
```

## アプリの起動

Android Studioでこのフォルダーを開き、Gradle同期後に`app`構成を実行してください。Android SDK 35とJava 17を使います。iOS向けには `iosArm64`、`iosSimulatorArm64`、`iosX64` の `NewsReader` framework と `MainViewController()` を用意しています。アプリとして実行するには、これを呼ぶiOSホストプロジェクトが必要です。
# markdowns
