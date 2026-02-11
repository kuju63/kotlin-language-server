# Kotlin Language Server

Kotlin 2.0 K2 Analysis APIを活用した高性能Language Server実装（ベースプロジェクト）

## 🎯 プロジェクト目標

開発者が求める主要機能を備えた実用的なKotlin Language Serverの実装：

### 必須機能（実装済みスケルトン）

| 機能 | 優先度 | 目標レイテンシ | 状態 |
|-----|--------|-------------|------|
| **Code Completion** | Critical | < 100ms | ✅ スケルトン実装 |
| **Go-to-Definition** | High | < 50ms | ✅ スケルトン実装 |
| **Find References** | High | < 200ms | ✅ スケルトン実装 |
| **Hover** | High | < 50ms | ✅ スケルトン実装 |
| **Signature Help** | Medium | < 100ms | ✅ スケルトン実装 |
| **Document Symbol** | Medium | < 300ms | ✅ スケルトン実装 |
| **Workspace Symbol** | Low | < 1s | ✅ スケルトン実装 |
| **Diagnostics** | High | < 500ms | ✅ スケルトン実装 |
| **Formatting** | Medium | < 1s | ✅ スケルトン実装 |
| **Code Action** | Medium | < 300ms | ✅ スケルトン実装 |
| **Rename** | High | < 500ms | ✅ スケルトン実装 |

### 高度な機能（将来実装予定）

- Semantic Tokens（セマンティックハイライト）
- Inlay Hints（型ヒント、パラメータ名）
- Call Hierarchy
- Type Hierarchy
- Document Links

## 🏗️ プロジェクト構造

```
kotlin-language-server/
├── src/
│   ├── main/kotlin/com/kotlinls/
│   │   ├── server/              # サーバー本体
│   │   │   ├── Main.kt          # エントリーポイント
│   │   │   └── KotlinLanguageServer.kt
│   │   ├── lsp/                 # LSP機能実装
│   │   │   ├── KotlinTextDocumentService.kt
│   │   │   └── KotlinWorkspaceService.kt
│   │   ├── analysis/            # K2 Analysis API連携
│   │   │   └── K2AnalysisProvider.kt
│   │   ├── persistence/         # データベース層
│   │   │   └── DatabaseSchema.kt
│   │   └── utils/               # ユーティリティ
│   │       └── LspUtils.kt
│   └── test/kotlin/com/kotlinls/
│       ├── server/
│       ├── lsp/
│       ├── analysis/
│       └── utils/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 🚀 クイックスタート

### 前提条件

- JDK 17以上
- Gradle 8.5以上（Gradle Wrapper付属）

### ビルド

```bash
# プロジェクトのビルド
./gradlew assemble

# テスト実行
./gradlew test

# ビルドとテストを同時実行
./gradlew build
```

### 実行

```bash
# 開発モードで実行
./gradlew run

# Fat JARを生成
./gradlew shadowJar

# JARから実行
java -Xmx2G -jar build/libs/kotlin-language-server-0.1.0-SNAPSHOT.jar
```

## 🧪 テスト

```bash
# 全テスト実行
./gradlew test

# 特定のテストクラスのみ実行
./gradlew test --tests KotlinLanguageServerTest

# テストレポート確認
open build/reports/tests/test/index.html
```

## 📦 技術スタック

- **言語**: Kotlin 2.0.21
- **ビルドツール**: Gradle 8.5 (Kotlin DSL)
- **JDK**: 17
- **LSPフレームワーク**: Eclipse LSP4J 0.21.2
- **コンパイラAPI**: Kotlin K2 Analysis API
- **データベース**: SQLite 3.45 (WALモード)
- **ロギング**: Logback + kotlin-logging
- **テスト**: JUnit 5 + MockK

## 🔧 開発

### IntelliJ IDEAでの開発

1. プロジェクトを開く: `File > Open` → `kotlin-language-server`
2. Gradleプロジェクトとして認識される
3. `Main.kt` を実行/デバッグ

### 機能の追加

スケルトン実装をベースに、実際の機能を実装：

1. **K2 Analysis API統合** (`K2AnalysisProvider.kt`)
   - スタンドアロンセッションの構築
   - シンボル解決、型推論の実装

2. **SQLite永続化** (`DatabaseSchema.kt`)
   - シンボルインデックスの保存
   - 参照情報の管理

3. **補完機能** (`KotlinTextDocumentService.kt`)
   - スコープ内シンボルの取得
   - スマート補完の実装

## 📋 実装ロードマップ

### Phase 1: 基盤構築 ✅ 完了

- [x] LSP4Jサーバー骨格
- [x] 主要機能のスケルトン実装
- [x] テストフレームワークのセットアップ
- [x] ビルド・テスト環境の整備

### Phase 2: コア機能実装（次のステップ）

- [ ] K2 Analysis API統合
  - [ ] スタンドアロンセッション構築
  - [ ] 基本的なシンボル解決
  - [ ] 型推論の実装
- [ ] 補完機能の実装
  - [ ] スコープ内シンボルの取得
  - [ ] トリガー文字対応
- [ ] 定義ジャンプの実装
- [ ] ホバー情報の実装

### Phase 3: 最適化

- [ ] SQLite永続化
- [ ] インクリメンタル更新
- [ ] キャッシュ層の実装
- [ ] パフォーマンステスト

## 📊 パフォーマンス目標

| メトリクス | 目標値 | 現状 |
|-----------|-------|------|
| 補完レイテンシ | < 100ms | - |
| 定義ジャンプ | < 300ms | - |
| 初期インデックス (10k LOC) | < 10s | - |
| メモリ使用量 (10k LOC) | < 500MB | - |
| テスト実行時間 | < 10s | ✅ < 5s |

## 🧩 依存関係

主要な依存関係：

```kotlin
// Kotlin & Compiler
kotlin-stdlib: 2.0.21
kotlin-compiler-embeddable: 2.0.21
analysis-api-standalone: 2.0.21

// LSP
lsp4j: 0.21.2

// Database
sqlite-jdbc: 3.45.0.0

// Logging
logback-classic: 1.4.14
kotlin-logging-jvm: 3.0.5

// Testing
junit-jupiter: 5.10.1
mockk: 1.13.8
```

## 🤝 コントリビューション

このプロジェクトはベースプロジェクトです。Phase 2以降の実装にご協力いただける方を募集しています。

## 📄 ライセンス

MIT License

## 📚 参考資料

- [Kotlin Analysis API Documentation](https://kotlin.github.io/analysis-api/)
- [Eclipse LSP4J](https://github.com/eclipse/lsp4j)
- [Language Server Protocol Specification](https://microsoft.github.io/language-server-protocol/)
- [SQLite Documentation](https://www.sqlite.org/docs.html)

---

**現在の状態**: ✅ ビルド・テストが正常に完了するベースプロジェクト

次のステップ: Phase 2 - K2 Analysis API統合とコア機能実装
