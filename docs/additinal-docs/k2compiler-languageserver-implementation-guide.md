Kotlin 2.0の**K2 Analysis API**は、従来のコンパイラ内部APIに代わる公式なセマンティック解析インターフェースを提供し、Language Server実装に最適化された設計となっている。本調査では、**KaSession**ベースの解析アーキテクチャ、**SQLite WAL**による永続化、**Salsa風インクリメンタル計算**による高性能設計を組み合わせた、10k LOC規模プロジェクトで実用的なLanguage Server構築の全体像を示す。

---

## K2 Analysis APIの基本アーキテクチャ

K2 Analysis APIは、Kotlin PSI（構文木）の上に構築されたセマンティック情報アクセス層である。 [github](https://kotlin.github.io/analysis-api/index_md.html)  [Kotlin](https://kotlin.github.io/analysis-api/index_md.html) すべての解析は`analyze {}`ブロック内の`KaSession`スコープで実行され、**セッション外へのオブジェクト持ち出しは禁止**という重要な制約がある。

### コア概念とライフタイム管理

```kotlin
// 基本的な解析パターン - すべての解析はanalyzeブロック内で完結
@RequiresReadLock
fun analyzeElement(element: KtElement) {
    analyze(element) {
        // KaSessionがレシーバーとして利用可能
        val type = element.expressionType  // 式の型情報取得
        val symbol = element.mainReference?.resolveToSymbol()  // シンボル解決
    }
}
```

**重要な制約**: `KaSymbol`、`KaType`などの`KaLifetimeOwner`オブジェクトはセッション終了後に無効化される。セッション間でシンボルを受け渡すには`KaSymbolPointer`を使用する。 [github](https://googlesamples.github.io/android-custom-lint-rules/api-guide/ast-analysis.md.html) [Kotlin](https://kotlin.github.io/analysis-api/fundamentals.html)

### シンボル階層構造

```
KaSymbol (基底インターフェース)
├── KaFileSymbol
├── KaClassSymbol
│   ├── KaNamedClassSymbol
│   └── KaAnonymousObjectSymbol
├── KaCallableSymbol
│   ├── KaFunctionSymbol / KaNamedFunctionSymbol
│   ├── KaConstructorSymbol
│   ├── KaPropertySymbol
│   └── KaParameterSymbol
└── KaDeclarationSymbol
```

### Language Server向けスタンドアロンセッション構築

IDE外でのLanguage Server実装には`buildStandaloneAnalysisAPISession`を使用する： [github](https://kotlin.github.io/analysis-api/index_md.html)

```kotlin
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule

val analysisSession = buildStandaloneAnalysisAPISession(
    projectDisposable = Disposer.newDisposable("LanguageServerSession")
) {
    buildKtSourceModule {
        moduleName = "main"
        platform = JvmPlatforms.defaultJvmPlatform
        addSourceRoot(sourcePath)
        addRegularDependency(stdlibModule)
    }
}
```

---

## 高性能アーキテクチャ設計

既存のkotlin-language-serverが抱える「シンボル数増加に伴う指数関数的な処理量増加」問題の根本原因は、**全シンボルのメモリ内保持**、**起動時の全ファイル再インデックス**、**永続化層の欠如**にある。rust-analyzerの**Salsa**アーキテクチャを参考に、以下の設計で解決する。

### 全体アーキテクチャ図

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Kotlin Language Server Process                        │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │                    Transport Layer (JSON-RPC via LSP4J)             ││
│  │  stdin/stdout ←→ MessageParser ←→ RequestRouter                     ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                              ↓                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │                    Request Handler Layer                             ││
│  │  ┌───────────────┐  ┌─────────────┐  ┌─────────────────────────┐   ││
│  │  │ Cancellation  │  │ Priority    │  │ Rate Limiter            │   ││
│  │  │ Manager       │  │ Queue       │  │ (Coding Agent対応)       │   ││
│  │  └───────────────┘  └─────────────┘  └─────────────────────────┘   ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                              ↓                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │              Incremental Computation Engine (Salsa-like)             ││
│  │  ┌──────────────────────┐  ┌────────────────────────────────────┐  ││
│  │  │  Query Database      │  │  Dependency Graph                  │  ││
│  │  │  - Input: FileContent│  │  - 自動依存追跡                     │  ││
│  │  │  - Derived: Parse,   │  │  - Early Cutoff最適化              │  ││
│  │  │    Symbols, Types    │  │  - Durability Level管理            │  ││
│  │  └──────────────────────┘  └────────────────────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                              ↓                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │                    K2 Analysis Layers                                ││
│  │  ┌───────────┐  ┌────────────┐  ┌───────────┐  ┌────────────────┐ ││
│  │  │ Syntax    │→ │ ItemTree   │→ │ DefMap    │→ │ Type System    │ ││
│  │  │ (parse)   │  │ (summary)  │  │ (scopes)  │  │ (inference)    │ ││
│  │  └───────────┘  └────────────┘  └───────────┘  └────────────────┘ ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                              ↓                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │                    Persistence Layer (SQLite WAL)                    ││
│  │  symbols.db │ symbols.db-wal │ symbols.db-shm                       ││
│  └─────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────┘
```

### リクエスト優先度とキャンセレーション

| 優先度 | リクエスト種別 | レイテンシ目標 |
|--------|---------------|---------------|
| Critical | textDocument/completion | < 100ms |
| High | textDocument/hover, signatureHelp | < 200ms |
| Medium | textDocument/definition | < 300ms |
| Low | workspace/symbol, references | < 1s |
| Background | diagnostics, indexing | ベストエフォート |

**キャンセレーション戦略**（rust-analyzer方式）: ユーザーがタイプするたびに、旧状態に関するすべての進行中作業をキャンセルし、その後変更を適用する。

---

## データフローと処理フロー

### 補完リクエスト処理フロー

```
┌────────────────┐     ┌────────────────┐     ┌────────────────┐
│  Client        │────→│  LSP Handler   │────→│  Query Engine  │
│  (VS Code)     │     │  (LSP4J)       │     │  (Salsa-like)  │
└────────────────┘     └────────────────┘     └────────────────┘
                              │                       │
                              ↓                       ↓
                       ┌────────────────┐     ┌────────────────┐
                       │ Position解析   │     │ キャッシュ確認  │
                       │ Context特定    │     │ 依存性チェック  │
                       └────────────────┘     └────────────────┘
                              │                       │
                              ↓                       ↓
                       ┌────────────────┐     ┌────────────────┐
                       │ K2 analyze{}   │←────│ KaSession取得  │
                       │ スコープ取得    │     │ (Read Lock)    │
                       └────────────────┘     └────────────────┘
                              │
                              ↓
                       ┌────────────────┐
                       │ CompletionItem │
                       │ リスト生成      │
                       └────────────────┘
```

### インクリメンタル更新の仕組み

rust-analyzerの**ItemTree**パターンを採用する。これは構文木を「要約」に凝縮したデータ構造で、**関数本体の変更がモジュール構造クエリに影響しない**という重要な不変条件を実現する。

```
FileA.kt ──imports──→ FileB.kt
    │                    │
    ↓                    ↓
 ItemTree_A          ItemTree_B (シグネチャのみ、本体なし)
    │                    │
    └────────┬───────────┘
             ↓
        DefMap (モジュールスコープ)
             ↓
        Type Resolution
```

**Early Cutoff最適化**: 入力が変更されても、出力が同じならば依存クエリを無効化しない。例：空白変更 → AST不変 → 型チェックスキップ。

---

## 永続化層設計（SQLite WAL推奨）

### データベース選定根拠

| 項目 | SQLite (WAL) | LMDB | RocksDB | PostgreSQL |
|------|--------------|------|---------|------------|
| 並行読み取り | ✅ 無制限 | ✅ 無制限 | ✅ 可 | ✅ 可 |
| 並行書き込み | ❌ 単一 | ❌ 単一 | ✅ 複数 | ✅ 複数 |
| SQL対応 | ✅ フル | ❌ なし | ❌ なし | ✅ フル |
| 全文検索 | ✅ FTS5 | ❌ なし | ❌ なし | ✅ 可 |
| 組み込み | ✅ 可 | ✅ 可 | ✅ 可 | ❌ 不可 |
| セットアップ | 低 | 中 | 中 | 高 |

**SQLite WALモードの利点**: 読み取りが書き込みをブロックしない、単一ファイルで管理容易、FTS5による高速シンボル検索。 [sqlite](https://sqlite.org/wal.html)

### DBスキーマ設計

```sql
-- =========================================
-- ファイル管理テーブル
-- =========================================
CREATE TABLE files (
    id INTEGER PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    content_hash TEXT NOT NULL,          -- SHA-256 ハッシュ
    last_indexed_at TEXT NOT NULL,
    line_count INTEGER
);

CREATE INDEX idx_files_path ON files(path);
CREATE INDEX idx_files_hash ON files(content_hash);

-- =========================================
-- シンボル定義テーブル
-- =========================================
CREATE TABLE symbols (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    qualified_name TEXT,                  -- 完全修飾名: module.Class.method
    kind INTEGER NOT NULL,                -- SymbolKind enum (LSP準拠)
    file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    
    -- 位置情報
    start_line INTEGER NOT NULL,
    start_column INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    end_column INTEGER NOT NULL,
    
    -- スコープ/包含関係
    parent_id INTEGER REFERENCES symbols(id) ON DELETE CASCADE,
    scope_level INTEGER DEFAULT 0,
    
    -- 型情報
    type_signature TEXT,                  -- 例: "(String, Int) -> Boolean"
    return_type TEXT,
    
    -- メタデータ
    visibility INTEGER DEFAULT 0,         -- 0=public, 1=private, 2=protected, 3=internal
    is_exported BOOLEAN DEFAULT 0,
    is_suspend BOOLEAN DEFAULT 0,
    is_inline BOOLEAN DEFAULT 0,
    is_deprecated BOOLEAN DEFAULT 0,
    
    -- ドキュメント
    documentation TEXT,
    
    UNIQUE(file_id, qualified_name)
);

-- コアインデックス
CREATE INDEX idx_symbols_name ON symbols(name);
CREATE INDEX idx_symbols_qualified ON symbols(qualified_name);
CREATE INDEX idx_symbols_file ON symbols(file_id);
CREATE INDEX idx_symbols_parent ON symbols(parent_id);
CREATE INDEX idx_symbols_file_line ON symbols(file_id, start_line);

-- =========================================
-- 参照テーブル（使用箇所）
-- =========================================
CREATE TABLE references (
    id INTEGER PRIMARY KEY,
    symbol_id INTEGER NOT NULL REFERENCES symbols(id) ON DELETE CASCADE,
    file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    start_line INTEGER NOT NULL,
    start_column INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    end_column INTEGER NOT NULL,
    kind INTEGER DEFAULT 0               -- 0=read, 1=write, 2=declaration
);

CREATE INDEX idx_refs_symbol ON references(symbol_id);
CREATE INDEX idx_refs_file ON references(file_id);
CREATE INDEX idx_refs_location ON references(file_id, start_line, start_column);

-- =========================================
-- シンボル関係テーブル（継承、実装など）
-- =========================================
CREATE TABLE symbol_relations (
    id INTEGER PRIMARY KEY,
    source_id INTEGER NOT NULL REFERENCES symbols(id) ON DELETE CASCADE,
    target_id INTEGER NOT NULL REFERENCES symbols(id) ON DELETE CASCADE,
    relation_kind INTEGER NOT NULL,       -- 0=extends, 1=implements, 2=overrides
    UNIQUE(source_id, target_id, relation_kind)
);

CREATE INDEX idx_relations_source ON symbol_relations(source_id);
CREATE INDEX idx_relations_target ON symbol_relations(target_id);

-- =========================================
-- 全文検索（FTS5 + trigram）
-- =========================================
CREATE VIRTUAL TABLE symbols_fts USING fts5(
    name, qualified_name, documentation,
    content=symbols, content_rowid=id,
    tokenize='trigram'                    -- 部分文字列マッチング対応
);

-- FTS同期トリガー
CREATE TRIGGER symbols_fts_insert AFTER INSERT ON symbols BEGIN
    INSERT INTO symbols_fts(rowid, name, qualified_name, documentation)
    VALUES (NEW.id, NEW.name, NEW.qualified_name, NEW.documentation);
END;
```

### 主要クエリ例

```sql
-- 定義ジャンプ（qualified_nameで検索）
SELECT * FROM symbols WHERE qualified_name = ? LIMIT 1;

-- 参照検索
SELECT r.*, f.path FROM references r
JOIN files f ON r.file_id = f.id
WHERE r.symbol_id = ?
ORDER BY f.path, r.start_line;

-- ファジーシンボル検索（ワークスペースシンボル）
SELECT s.*, bm25(symbols_fts) as rank
FROM symbols_fts
JOIN symbols s ON symbols_fts.rowid = s.id
WHERE symbols_fts MATCH ?
ORDER BY rank LIMIT 50;

-- 位置からシンボル取得
SELECT * FROM symbols
WHERE file_id = ?
  AND start_line <= ? AND end_line >= ?
  AND (start_line < ? OR start_column <= ?)
  AND (end_line > ? OR end_column >= ?)
ORDER BY (end_line - start_line) LIMIT 1;
```

---

## LSP実装パターン（LSP4J）

### サーバー基本構造

```kotlin
// メインエントリポイント
fun main() {
    val server = KotlinLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(server, System.`in`, System.out)
    server.connect(launcher.remoteProxy)
    launcher.startListening().get()
}

class KotlinLanguageServer : LanguageServer, LanguageClientAware {
    private lateinit var client: LanguageClient
    private val textDocumentService = KotlinTextDocumentService(this)
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    
    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val capabilities = ServerCapabilities().apply {
            textDocumentSync = TextDocumentSyncKind.Incremental
            completionProvider = CompletionOptions().apply {
                resolveProvider = true
                triggerCharacters = listOf(".", ":", "@", "(")
            }
            definitionProvider = Either.forLeft(true)
            referencesProvider = Either.forLeft(true)
            documentSymbolProvider = Either.forLeft(true)
            hoverProvider = Either.forLeft(true)
        }
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }
    
    override fun getTextDocumentService() = textDocumentService
    override fun getWorkspaceService() = KotlinWorkspaceService(this)
    // ...
}
```

### 補完機能実装

```kotlin
class KotlinTextDocumentService(private val server: KotlinLanguageServer) : TextDocumentService {
    private val analysisProvider = K2AnalysisProvider()
    
    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CompletableFutures.computeAsync { cancelChecker ->
            cancelChecker.checkCanceled()  // キャンセレーション対応
            
            val uri = params.textDocument.uri
            val position = params.position
            val file = getKtFile(uri)
            val offset = positionToOffset(file, position)
            
            val items = analysisProvider.withAnalysis(file) {
                // K2 Analysis API使用
                val element = file.findElementAt(offset)
                val scopeContext = element?.containingKtFile?.scopeContext(element)
                
                scopeContext?.scopes?.flatMap { scope ->
                    scope.getAllSymbols().map { symbol ->
                        createCompletionItem(symbol)
                    }
                } ?: emptyList()
            }
            
            Either.forRight(CompletionList(false, items))
        }
    }
    
    private fun createCompletionItem(symbol: KaSymbol): CompletionItem {
        return CompletionItem().apply {
            label = when (symbol) {
                is KaNamedSymbol -> symbol.name.asString()
                else -> "unknown"
            }
            kind = when (symbol) {
                is KaFunctionSymbol -> CompletionItemKind.Function
                is KaPropertySymbol -> CompletionItemKind.Property
                is KaClassSymbol -> CompletionItemKind.Class
                else -> CompletionItemKind.Text
            }
            // resolveで詳細情報を遅延取得
            data = symbol.createPointer()
        }
    }
}
```

### K2 Analysis Provider実装

```kotlin
class K2AnalysisProvider {
    private val project: Project
    private val readLock = ReentrantReadWriteLock()
    
    fun <T> withAnalysis(file: KtFile, action: KaSession.() -> T): T {
        return readLock.readLock().withLock {
            analyze(file, action)
        }
    }
    
    fun resolveSymbolAtPosition(file: KtFile, offset: Int): KaSymbolPointer<*>? {
        val element = file.findElementAt(offset) ?: return null
        val reference = element.parent as? KtReferenceExpression ?: return null
        
        return withAnalysis(file) {
            reference.mainReference.resolveToSymbol()?.createPointer()
        }
    }
    
    fun getTypeAtPosition(file: KtFile, offset: Int): String? {
        val element = file.findElementAt(offset)?.parent as? KtExpression ?: return null
        
        return withAnalysis(file) {
            element.expressionType?.render(position = Variance.INVARIANT)
        }
    }
}
```

---

## Gradle Tooling API連携

### 依存関係取得

```kotlin
class GradleProjectAnalyzer(private val projectDir: File) {
    
    fun getProjectDependencies(): ProjectDependencies {
        val connection = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .useBuildDistribution()
            .connect()
        
        return try {
            val ideaProject = connection.getModel(IdeaProject::class.java)
            
            val dependencies = ideaProject.modules.flatMap { module ->
                module.dependencies.mapNotNull { dep ->
                    when (dep) {
                        is IdeaSingleEntryLibraryDependency -> LibraryDependency(
                            file = dep.file,
                            source = dep.source,
                            javadoc = dep.javadoc,
                            scope = dep.scope.scope
                        )
                        is IdeaModuleDependency -> ProjectDependency(
                            moduleName = dep.targetModuleName
                        )
                        else -> null
                    }
                }
            }
            
            val sourceDirs = ideaProject.modules.flatMap { module ->
                module.contentRoots.flatMap { root ->
                    root.sourceDirectories.map { it.directory } +
                    root.testDirectories.map { it.directory }
                }
            }
            
            ProjectDependencies(dependencies, sourceDirs)
        } finally {
            connection.close()
        }
    }
}

// インクリメンタル同期マネージャ
class IncrementalSyncManager(private val projectDir: File) {
    private var lastSyncHash: String = ""
    private var cachedDependencies: ProjectDependencies? = null
    
    fun syncIfNeeded(): ProjectDependencies {
        val currentHash = computeBuildFilesHash()
        
        if (currentHash == lastSyncHash && cachedDependencies != null) {
            return cachedDependencies!!  // キャッシュ使用
        }
        
        val analyzer = GradleProjectAnalyzer(projectDir)
        cachedDependencies = analyzer.getProjectDependencies()
        lastSyncHash = currentHash
        return cachedDependencies!!
    }
    
    private fun computeBuildFilesHash(): String {
        return projectDir.walkTopDown()
            .filter { it.name.endsWith(".gradle.kts") || it.name.endsWith(".gradle") }
            .map { it.lastModified() }
            .hashCode().toString()
    }
}
```

---

## 性能最適化ポイント

### 指数関数的処理増加の回避策

| 問題パターン | 原因 | 解決策 |
|-------------|------|--------|
| 全ファイル再解析 | 変更のたびに全体再計算 | ItemTree + Early Cutoff |
| N×Nシンボル解決 | 線形検索でシンボル探索 | インデックス + ハッシュマップ |
| 無制限な推移的閉包 | 全import再帰的に追跡 | スコープ制限 + 遅延読み込み |
| GCスラッシング | 大量の一時オブジェクト | オブジェクトプーリング + インターニング |

### Durability System（rust-analyzer方式）

```kotlin
enum class Durability {
    LOW,      // ユーザーコード（頻繁に変更）
    MEDIUM,   // 依存ライブラリ（稀に変更）
    HIGH      // 標準ライブラリ（ほぼ不変）
}

// バージョンベクタで管理
data class RevisionVector(
    val userCodeVersion: Long,
    val dependencyVersion: Long,
    val stdlibVersion: Long
)

// stdlibクエリはuserCodeVersion変更で無効化されない
```

### キャッシュ戦略

| レベル | 内容 | 無効化条件 |
|--------|------|-----------|
| L1 (Hot) | パース済みAST、現在ファイルのシンボル | テキスト変更時 |
| L2 (Warm) | 型情報、解決済み参照 | 依存ファイル変更時 |
| L3 (Cold) | クロスファイルシンボル、インポートグラフ | ファイル追加/削除時 |
| Disk | SQLiteインデックス | content_hashによる検証 |

---

## VS Code拡張設定

### package.json

```json
{
    "name": "kotlin-k2-language-server",
    "displayName": "Kotlin K2 Language Server",
    "version": "0.1.0",
    "engines": { "vscode": "^1.74.0" },
    "activationEvents": ["onLanguage:kotlin"],
    "main": "./out/extension.js",
    "contributes": {
        "languages": [{
            "id": "kotlin",
            "aliases": ["Kotlin"],
            "extensions": [".kt", ".kts"]
        }],
        "configuration": {
            "properties": {
                "kotlin.languageServer.path": {
                    "type": "string",
                    "description": "Path to Language Server JAR"
                }
            }
        }
    },
    "dependencies": {
        "vscode-languageclient": "^9.0.0"
    }
}
```

### extension.ts

```typescript
import { LanguageClient, ServerOptions, TransportKind } from 'vscode-languageclient/node';

export function activate(context: vscode.ExtensionContext) {
    const serverJar = context.asAbsolutePath('server/kotlin-ls.jar');
    
    const serverOptions: ServerOptions = {
        command: 'java',
        args: ['-Xmx2G', '-jar', serverJar],
        transport: TransportKind.stdio
    };
    
    const clientOptions = {
        documentSelector: [
            { scheme: 'file', language: 'kotlin' }
        ]
    };
    
    const client = new LanguageClient(
        'kotlinLanguageServer',
        'Kotlin Language Server',
        serverOptions,
        clientOptions
    );
    
    client.start();
}
```

---

## 性能目標と実現方法

| メトリクス | 目標 | 実現方法 |
|-----------|------|---------|
| 補完レイテンシ | < 100ms | プリコンピュート + スコープ制限 |
| 定義ジャンプ | < 300ms | SQLiteインデックス直接参照 |
| 初期インデックス (10k LOC) | < 10s | 並列パース + 遅延解析 |
| インクリメンタル更新 | < 100ms | ItemTree安定性 + Early Cutoff |
| メモリ使用量 (10k LOC) | < 500MB | SQLite永続化 + LRUキャッシュ |

---

## 実装ロードマップ（PoC向け）

**Phase 1: 基盤構築（2週間）**
- LSP4Jによるサーバー骨格
- K2 Analysis APIスタンドアロンセッション構築
- SQLiteスキーマ実装とマイグレーション

**Phase 2: コア機能（2週間）**
- 補完機能（基本的なスコープ内シンボル）
- 定義ジャンプ
- ホバー情報（型表示）

**Phase 3: 最適化（1週間）**
- インクリメンタル更新機構
- キャッシュ層実装
- Gradle Tooling API連携

---

## 結論と推奨事項

Kotlin 2.0のK2 Analysis APIは、Language Server実装に適した設計となっており、**KaSession**ベースの明確なライフタイム管理、**KaSymbolPointer**によるセッション間シンボル受け渡し、スタンドアロンモードでのIDE外利用をサポートしている。

既存のkotlin-language-serverが抱える性能問題は、主に**メモリ内全シンボル保持**と**永続化層の欠如**に起因する。これを解決するには、**SQLite WALによる永続化**、**Salsa風インクリメンタル計算**、**ItemTreeパターンによる安定した中間表現**の採用が有効である。

K2コンパイラ自体が従来比**最大376%の解析速度向上**を実現していることから、このAPIを適切に活用することで、10k LOC規模のプロジェクトでも実用的なレスポンス時間を達成できる見込みがある。

