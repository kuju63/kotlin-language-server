# CLAUDE.md

This file provides guidance for AI assistants (like Claude Code) working on this project.

## プロジェクト概要

このプロジェクトは、**Kotlin 2.0 K2 Analysis API**を活用した高性能Language Serverの実装です。LSP（Language Server Protocol）を通じて、IDEやエディタに対してコード補完、定義ジャンプ、参照検索などの機能を提供します。

### 主要な技術スタック

- **Kotlin 2.0 + K2 Analysis API**: コンパイラの型解析機能を活用
- **JDK 17、Gradle 8+** (Kotlin DSL)
- **LSP4J**: Eclipse LSPフレームワーク（JSON-RPC通信）
- **SQLite**: インデックス/キャッシュ層（WALモード＋FTS5）
- **JUnit 5 + MockK**: テストフレームワーク

---

## よく使用するコマンド

```bash
# ビルド
./gradlew assemble

# テスト実行
./gradlew test

# 特定のテスト実行
./gradlew test --tests <TestClassName>

# ビルド+テスト
./gradlew build

# 開発モードで実行
./gradlew run

# Fat JAR作成
./gradlew shadowJar

# 検証（リント+テスト）
./gradlew verify

# JARから実行
java -Xmx2G -jar build/libs/kotlin-language-server-0.1.0-SNAPSHOT.jar
```

---

## 高レベルアーキテクチャ

### LSPサーバーのアーキテクチャ

- **LSP4JベースのJSON-RPC通信**: stdin/stdoutを使用
- **非同期処理**: スレッドプールによる並行処理設計
- **インクリメンタルなテキスト同期**: 効率的なドキュメント更新

### 5つの主要コンポーネント

1. **`server/`**: エントリーポイントとサーバー初期化
   - `Main.kt`: アプリケーションのエントリーポイント
   - `KotlinLanguageServer.kt`: サーバーのケイパビリティ定義

2. **`lsp/`**: LSPプロトコル実装
   - `KotlinTextDocumentService.kt`: テキストドキュメント関連の機能（補完、ホバー、定義ジャンプなど）
   - `KotlinWorkspaceService.kt`: ワークスペース関連の機能

3. **`analysis/`**: K2 Analysis API統合ポイント（Phase 2で実装予定）
   - `K2AnalysisProvider.kt`: Kotlinコンパイラの型解析機能へのインターフェース

4. **`persistence/`**: SQLiteベースのインデックス/キャッシュ層
   - `DatabaseSchema.kt`: シンボルインデックスのスキーマ定義
   - WALモード＋FTS5によるフルテキスト検索最適化

5. **`utils/`**: LSP型変換ユーティリティ
   - `TextEditUtils.kt`: インクリメンタル更新のためのテキスト編集ユーティリティ

### データフロー

```mermaid
graph LR
    Client[LSPクライアント] --> Main[Main.kt]
    Main --> KLS[KotlinLanguageServer]
    KLS --> TDS[TextDocumentService]
    KLS --> WS[WorkspaceService]
    TDS --> K2[K2AnalysisProvider]
    K2 --> API[K2 Analysis API]
    K2 --> DB[DatabaseSchema]
    DB --> SQLite[(SQLite)]
```

**フローの説明:**

1. LSPクライアント（VS Code、IntelliJなど）がJSON-RPCリクエストを送信
2. `Main.kt`がstdin/stdoutでリクエストを受信
3. `KotlinLanguageServer`がリクエストを適切なサービスにルーティング
4. `TextDocumentService`/`WorkspaceService`が機能を実行
5. `K2AnalysisProvider`がKotlinコンパイラAPIを呼び出して型解析を実行
6. 分析結果を`DatabaseSchema`経由でSQLiteにキャッシュ
7. 結果をクライアントに返却

### 重要な設計判断

- **スレッドセーフな並行設計**: `ConcurrentHashMap`でドキュメント状態を管理
- **インクリメンタル更新サポート**: `TextEditUtils`により差分更新を効率化
- **段階的実装が可能な拡張可能なアーキテクチャ**: フェーズ分けされた開発計画
- **SQLite WALモード＋FTS5**: フルテキスト検索の最適化とパフォーマンス向上

---

## 開発フロー

このプロジェクトは **GitHub Issueベースの開発フロー** と **Test-Driven Development (TDD)** を採用しています。

詳細は **[CONTRIBUTING.md](./CONTRIBUTING.md)** を参照してください。

### 重要な原則

- **Issue駆動開発**: 新機能や改善は**GitHub Issueから開始**
- **TDD**: **実装の前にテストを作成**（Red-Green-Refactor）
- **Conventional Commits**: コミットメッセージとPRタイトルは英語で記述
- **言語要件**: **Issue body and comments are written in English**

### クイックリファレンス

```bash
# テストを書く → 実装 → コミット
./gradlew test --tests <TestClassName>
git commit -m "feat: add new feature #123"
```

**コミットタイプ**: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `chore`, `ci`

詳細な開発フロー、コミット規約、実装例は [CONTRIBUTING.md](./CONTRIBUTING.md) を参照してください。

---

## 重要なファイル

新しい開発者やAIアシスタントが最初に読むべきファイル：

1. **`src/main/kotlin/com/kotlinls/server/KotlinLanguageServer.kt`**
   - サーバーのケイパビリティ定義
   - 初期化処理とサービスの登録

2. **`src/main/kotlin/com/kotlinls/lsp/KotlinTextDocumentService.kt`**
   - LSP機能の実装（補完、ホバー、定義ジャンプなど）
   - クライアントとのメインインターフェース

3. **`src/main/kotlin/com/kotlinls/analysis/K2AnalysisProvider.kt`**
   - K2 Analysis APIへの統合ポイント
   - コンパイラ機能の抽象化層

4. **`src/main/kotlin/com/kotlinls/persistence/DatabaseSchema.kt`**
   - シンボルインデックスのスキーマ定義
   - キャッシュ戦略の理解

---

## パフォーマンス目標

| 機能         | 目標レイテンシ | 優先度   |
| ------------ | -------------- | -------- |
| 補完         | < 100ms        | Critical |
| 定義ジャンプ | < 50ms         | High     |
| 参照検索     | < 200ms        | High     |
| ホバー       | < 50ms         | High     |

これらの目標は、実際のIDEでの使用体験を損なわないために設定されています。

---

## 追加リソース

- **README.md**: プロジェクトの概要と使用方法
- **BUILD_INSTRUCTIONS.md**: 詳細なビルド手順と環境設定
- **GitHub Issues**: 機能リクエスト、バグレポート、実装の進捗
- **その他調査レポート**: docs/additional-docs に事前調査レポートが含まれており、APIリファレンスなどの参考となる

---

このドキュメントは、プロジェクトの「大きな絵」を理解し、効率的に作業を開始するためのガイドです。詳細な実装やAPIリファレンスについては、コードベースとJavadoc/KDocコメントを参照してください。
