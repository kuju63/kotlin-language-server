# Kotlin 2.0 K2 Analysis API 調査レポート
## Language Server実装に必要な機能

---

## 1. K2 Analysis APIの概要

Kotlin 2.0のK2 Analysis APIは、Kotlinコードのセマンティック解析を行うための公式APIです。従来のコンパイラ内部APIに代わる、安定したインターフェースを提供します。

### 1.1 主要な特徴

- **Kotlin PSI上に構築**: 構文木の上にセマンティック情報へのアクセス層を提供
- **コンパイラ内部の隠蔽**: APIレイヤーはコンパイラ内部を公開せず、安定したインターフェースを提供
- **後方互換性**: 安定部分についてはソースおよびバイナリレベルの後方互換性を保証
- **遅延解決とキャッシュ無効化**: 効率的なコード解析に必要な複雑な部分をカプセル化
- **K1/K2両対応**: 同じコードがK1/K2両モードで動作（K2がメイン）

---

## 2. コアコンセプト

### 2.1 KaSession（エントリポイント）

**KaSession**はAnalysis APIとのやり取りの中心となるオブジェクトです。

```kotlin
@RequiresReadLock
fun perform(element: KtElement) {
    analyze(element) {
        // KaSessionがレシーバーとして利用可能
        val type = element.expressionType
        val symbol = element.mainReference?.resolveToSymbol()
    }
}
```

**重要な制約**:
- `analyze {}`ブロック内でのみ有効
- ブロック外への持ち出し禁止
- 読み取りアクション内でのみ呼び出し可能（`@RequiresReadLock`）

### 2.2 KaLifetimeOwner（ライフタイム管理）

`KaSession`から取得したオブジェクト（`KaSymbol`、`KaType`など）は**KaLifetimeOwner**の性質を持ち、セッション終了後に無効化されます。

**禁止事項**:
- 長期保持クラスのプロパティへの保存
- staticコンテキストへの保存
- `analyze {}`ブロック外でのアクセス

**理由**: メモリリークを引き起こす（解決セッション全体を保持）

### 2.3 KaSymbolPointer（セッション間のシンボル受け渡し）

セッション間でシンボルを受け渡す際は`KaSymbolPointer`を使用します。

```kotlin
fun resolveCall(ktCall: KtCallExpression): KaSymbolPointer<KaCallableSymbol>? {
    analyze(ktCall) {
        val symbol = ktCall.mainReference.resolveToSymbol() as? KaCallableSymbol
        return symbol?.createPointer() // ポインタ作成
    }
}

fun processCallTarget(ktContext: KtElement, pointer: KaSymbolPointer<KaCallableSymbol>) {
    analyze(ktContext) {
        val symbol = pointer.restoreSymbol() ?: return
        symbol.callableId // 復元したシンボルを使用
    }
}
```

**注**: IntelliJ IDEA 2024.3以降、`KaType`にもポインタが利用可能になります。

---

## 3. シンボル階層（KaSymbol）

Analysis APIで扱うシンボルの階層構造：

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

### 3.1 シンボルの取得方法

```kotlin
analyze(element) {
    // PSI要素からシンボルを解決
    val symbol = element.mainReference?.resolveToSymbol()
    
    // 宣言からシンボルを取得
    val functionSymbol = (declaration as KtNamedFunction).symbol
    
    // 型情報の取得
    val type = expression.expressionType
}
```

---

## 4. 型システム（KaType）

### 4.1 基本的な型情報の取得

```kotlin
analyze(this) {
    val type = expression.expressionType
    
    // 組み込み型へのアクセス
    val stringType = builtinTypes.string
    val anyType = builtinTypes.any
    val nothingType = builtinTypes.nothing
}
```

### 4.2 型の比較と検査

```kotlin
analyze(element) {
    val type = expression.expressionType
    
    // Nothing型のチェック
    if (type?.isNothingType == true) {
        // ...
    }
    
    // 型のレンダリング
    val typeString = type?.render(position = Variance.INVARIANT)
}
```

---

## 5. コール解決（Call Resolution）

### 5.1 呼び出しの解決

```kotlin
fun analyzeCall(call: KtCallExpression) {
    analyze(call) {
        val callInfo = call.resolveToCall()
        
        when (val resolvedCall = callInfo?.singleFunctionCallOrNull()) {
            is KaFunctionCall -> {
                val functionSymbol = resolvedCall.symbol
                val returnType = functionSymbol.returnType
            }
        }
    }
}
```

### 5.2 参照の解決

```kotlin
analyze(element) {
    // メイン参照の解決
    val symbol = element.mainReference?.resolveToSymbol()
    
    // すべての参照の解決
    val references = element.references
    references.forEach { ref ->
        val resolved = ref.resolveToSymbol()
    }
}
```

---

## 6. スコープとシンボル検索

### 6.1 スコープ内のシンボル取得

```kotlin
analyze(element) {
    val scopeContext = element.containingKtFile?.scopeContext(element)
    
    scopeContext?.scopes?.flatMap { scope ->
        scope.getAllSymbols() // スコープ内のすべてのシンボル
    }
}
```

### 6.2 特定のシンボルの検索

```kotlin
analyze(file) {
    // ファイル内の特定の名前を持つシンボルを検索
    val symbols = file.fileSymbol
        .fileScope
        .getCallableSymbols { it.asString() == "functionName" }
}
```

---

## 7. 診断情報（Diagnostics）

### 7.1 診断情報の収集

```kotlin
analyze(file) {
    // すべての診断情報を収集
    val diagnostics = file.collectDiagnostics()
    
    // フィルタリングして収集
    val commonDiagnostics = file.collectDiagnostics(
        KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS
    )
    
    diagnostics.forEach { diagnostic ->
        val message = diagnostic.defaultMessage
        val severity = diagnostic.severity
        val range = diagnostic.textRanges
    }
}
```

---

## 8. スタンドアロンモード（Language Server向け）

IDE外でのLanguage Server実装には**スタンドアロンセッション**を使用します。

### 8.1 基本的なセッション構築

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
        
        // 標準ライブラリの追加
        addRegularDependency(stdlibModule)
    }
}
```

### 8.2 プロジェクト構造の定義

```kotlin
buildStandaloneAnalysisAPISession(
    projectDisposable = projectDisposable
) {
    // ソースモジュール
    val mainModule = buildKtSourceModule {
        moduleName = "main"
        platform = JvmPlatforms.defaultJvmPlatform
        
        // ソースルートの追加
        addSourceRoot(Path.of("/path/to/src"))
        
        // コンパイルクラスパスの追加
        addRegularDependency(stdlibPath)
        addRegularDependency(kotlinReflectPath)
    }
    
    // テストモジュール（オプション）
    buildKtSourceModule {
        moduleName = "test"
        platform = JvmPlatforms.defaultJvmPlatform
        addSourceRoot(Path.of("/path/to/test"))
        addRegularDependency(mainModule) // メインモジュールへの依存
    }
}
```

### 8.3 モジュールの取得と使用

```kotlin
// ファイルからモジュールを取得
val module = KaModuleProvider.getModule(project, ktFile, useSiteModule = null)

// モジュールコンテキストで解析
analyze(ktFile) {
    // このセッションは ktFile のモジュールの視点から解析
    val symbols = ktFile.fileSymbol.fileScope.getAllSymbols()
}
```

---

## 9. インメモリファイル解析

### 9.1 ファイルの作成と解析

```kotlin
val factory = KtPsiFactory(project)
val file = factory.createFile("""
    package test
    fun foo() {
        println("Hello")
    }
""".trimIndent())

// コンテキストモジュールの設定
val contextModule = KaModuleProvider.getModule(project, contextFile, null)
file.contextModule = contextModule

analyze(file) {
    val diagnostics = file.collectDiagnostics()
    // モジュールコンテキストで解析可能
}
```

### 9.2 コードフラグメント

小さなコード片を解析する場合、コードフラグメントを使用できます。

```kotlin
// 式の解析
val expressionFragment = KtExpressionCodeFragment(
    project, 
    "expression.kt", 
    "listOf(1, 2, 3)",
    imports = "",
    context = contextElement
)

// ブロックの解析  
val blockFragment = KtBlockCodeFragment(
    project,
    "block.kt",
    "val x = 10\nprintln(x)",
    imports = "",
    context = contextElement
)

// 型の解析
val typeFragment = KtTypeCodeFragment(
    project,
    "type.kt",
    "List<String>",
    imports = "",
    context = contextElement
)
```

---

## 10. Language Server実装に必要なAPI一覧

### 10.1 補完機能（Code Completion）

| API | 用途 |
|-----|------|
| `KtElement.scopeContext()` | 現在位置のスコープコンテキスト取得 |
| `KaScope.getAllSymbols()` | スコープ内のすべてのシンボル取得 |
| `KaScope.getCallableSymbols()` | 呼び出し可能なシンボル取得 |
| `KaSymbol.name` | シンボル名の取得 |
| `KaCallableSymbol.returnType` | 戻り値型の取得 |
| `KaType.render()` | 型情報の文字列化 |

**実装例**:
```kotlin
fun getCompletionItems(file: KtFile, offset: Int): List<CompletionItem> {
    val element = file.findElementAt(offset) ?: return emptyList()
    
    return analyze(file) {
        val scopeContext = file.scopeContext(element)
        
        scopeContext?.scopes?.flatMap { scope ->
            scope.getAllSymbols().mapNotNull { symbol ->
                createCompletionItem(symbol)
            }
        } ?: emptyList()
    }
}

fun createCompletionItem(symbol: KaSymbol): CompletionItem? {
    return when (symbol) {
        is KaNamedSymbol -> CompletionItem().apply {
            label = symbol.name.asString()
            kind = when (symbol) {
                is KaFunctionSymbol -> CompletionItemKind.Function
                is KaPropertySymbol -> CompletionItemKind.Property
                is KaClassSymbol -> CompletionItemKind.Class
                else -> CompletionItemKind.Text
            }
            detail = (symbol as? KaCallableSymbol)?.returnType?.render()
        }
        else -> null
    }
}
```

### 10.2 定義ジャンプ（Go to Definition）

| API | 用途 |
|-----|------|
| `KtElement.mainReference` | 要素の主要参照取得 |
| `KtReference.resolveToSymbol()` | 参照からシンボルへの解決 |
| `KaSymbol.psi` | シンボルに対応するPSI要素 |
| `KaSymbolPointer.restoreSymbol()` | ポインタからシンボルの復元 |

**実装例**:
```kotlin
fun getDefinition(file: KtFile, offset: Int): Location? {
    val element = file.findElementAt(offset)?.parent as? KtReferenceExpression
        ?: return null
    
    return analyze(file) {
        val symbol = element.mainReference?.resolveToSymbol() ?: return null
        val psi = symbol.psi ?: return null
        
        Location(
            uri = psi.containingFile.virtualFile.url,
            range = psi.textRange.toRange()
        )
    }
}
```

### 10.3 参照検索（Find References）

| API | 用途 |
|-----|------|
| `KtElement.symbol` | 宣言からシンボル取得 |
| PSI検索API | PSI要素の検索 |
| `KtReference.isReferenceTo()` | 特定要素への参照チェック |

**実装例**:
```kotlin
fun findReferences(file: KtFile, offset: Int): List<Location> {
    val element = file.findElementAt(offset)?.parent as? KtNamedDeclaration
        ?: return emptyList()
    
    val pointer = analyze(element) {
        element.symbol?.createPointer()
    } ?: return emptyList()
    
    // プロジェクト全体を検索（PSI Search APIを使用）
    return searchReferencesInProject(element, pointer)
}
```

### 10.4 ホバー情報（Hover）

| API | 用途 |
|-----|------|
| `KtExpression.expressionType` | 式の型情報取得 |
| `KaSymbol.render()` | シンボル情報のレンダリング |
| `KaCallableSymbol.returnType` | 戻り値型の取得 |
| PSI要素のドキュメント取得 | KDoc情報の取得 |

**実装例**:
```kotlin
fun getHover(file: KtFile, offset: Int): Hover? {
    val element = file.findElementAt(offset)?.parent
    
    return analyze(file) {
        when (element) {
            is KtExpression -> {
                val type = element.expressionType
                Hover(contents = type?.render() ?: "")
            }
            is KtNamedDeclaration -> {
                val symbol = element.symbol
                val doc = element.docComment?.text
                Hover(contents = buildString {
                    append(symbol?.render())
                    doc?.let { append("\n\n").append(it) }
                })
            }
            else -> null
        }
    }
}
```

### 10.5 シグネチャヘルプ（Signature Help）

| API | 用途 |
|-----|------|
| `KtCallExpression.resolveToCall()` | 呼び出しの解決 |
| `KaFunctionSymbol.valueParameters` | 関数パラメータ情報 |
| `KaValueParameterSymbol.name` | パラメータ名 |
| `KaValueParameterSymbol.returnType` | パラメータ型 |

**実装例**:
```kotlin
fun getSignatureHelp(file: KtFile, offset: Int): SignatureHelp? {
    val call = file.findElementAt(offset)
        ?.getParentOfType<KtCallExpression>(false)
        ?: return null
    
    return analyze(file) {
        val callInfo = call.resolveToCall()
        val functionSymbol = callInfo?.singleFunctionCallOrNull()?.symbol
            ?: return null
        
        SignatureHelp().apply {
            signatures = listOf(SignatureInformation().apply {
                label = functionSymbol.render()
                parameters = functionSymbol.valueParameters.map { param ->
                    ParameterInformation(
                        label = param.name.asString(),
                        documentation = param.returnType.render()
                    )
                }
            })
        }
    }
}
```

### 10.6 診断情報（Diagnostics）

| API | 用途 |
|-----|------|
| `KtFile.collectDiagnostics()` | ファイルの診断情報収集 |
| `KaDiagnostic.defaultMessage` | 診断メッセージ |
| `KaDiagnostic.severity` | 診断の重要度 |
| `KaDiagnostic.textRanges` | 診断対象範囲 |

**実装例**:
```kotlin
fun getDiagnostics(file: KtFile): List<Diagnostic> {
    return analyze(file) {
        file.collectDiagnostics(
            KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS
        ).map { diagnostic ->
            Diagnostic(
                range = diagnostic.textRanges.first().toRange(),
                severity = diagnostic.severity.toDiagnosticSeverity(),
                message = diagnostic.defaultMessage,
                source = "kotlin"
            )
        }
    }
}
```

### 10.7 ドキュメントシンボル（Document Symbols）

| API | 用途 |
|-----|------|
| `KtFile.declarations` | ファイル内の宣言一覧 |
| `KtDeclaration.symbol` | 宣言のシンボル取得 |
| `KaSymbol.classKind` | クラスの種類 |
| `KaClassSymbol.memberScope` | クラスメンバーのスコープ |

**実装例**:
```kotlin
fun getDocumentSymbols(file: KtFile): List<DocumentSymbol> {
    return analyze(file) {
        file.declarations.mapNotNull { declaration ->
            createDocumentSymbol(declaration)
        }
    }
}

fun KaSession.createDocumentSymbol(declaration: KtDeclaration): DocumentSymbol? {
    val symbol = declaration.symbol ?: return null
    
    return DocumentSymbol(
        name = (symbol as? KaNamedSymbol)?.name?.asString() ?: "",
        kind = when (symbol) {
            is KaClassSymbol -> SymbolKind.Class
            is KaFunctionSymbol -> SymbolKind.Function
            is KaPropertySymbol -> SymbolKind.Property
            else -> SymbolKind.Variable
        },
        range = declaration.textRange.toRange(),
        selectionRange = declaration.nameIdentifier?.textRange?.toRange() 
            ?: declaration.textRange.toRange(),
        children = getChildSymbols(symbol)
    )
}
```

### 10.8 ワークスペースシンボル（Workspace Symbols）

| API | 用途 |
|-----|------|
| PSI検索API | プロジェクト全体のシンボル検索 |
| `KtDeclaration.symbol` | 宣言のシンボル取得 |
| `KaSymbol.classId` / `callableId` | 完全修飾名 |

**実装例**:
```kotlin
fun getWorkspaceSymbols(query: String): List<SymbolInformation> {
    // PSI検索APIでプロジェクト全体から検索
    val declarations = searchDeclarationsByName(query)
    
    return declarations.mapNotNull { declaration ->
        analyze(declaration) {
            val symbol = declaration.symbol ?: return@mapNotNull null
            
            SymbolInformation(
                name = (symbol as? KaNamedSymbol)?.name?.asString() ?: "",
                kind = symbol.toSymbolKind(),
                location = declaration.toLocation()
            )
        }
    }
}
```

### 10.9 リネーム（Rename）

| API | 用途 |
|-----|------|
| `KtElement.symbol` | 要素のシンボル取得 |
| PSI Refactoring API | リネーム処理 |
| `KaSymbolPointer` | シンボルの一貫した追跡 |

**実装例**:
```kotlin
fun rename(file: KtFile, offset: Int, newName: String): WorkspaceEdit? {
    val element = file.findElementAt(offset)?.parent as? KtNamedDeclaration
        ?: return null
    
    val pointer = analyze(element) {
        element.symbol?.createPointer()
    } ?: return null
    
    // PSI Refactoring APIを使用してリネーム
    val references = findAllReferences(element, pointer)
    
    return WorkspaceEdit().apply {
        changes = references.groupBy { it.containingFile }
            .mapValues { (_, refs) ->
                refs.map { ref ->
                    TextEdit(ref.textRange.toRange(), newName)
                }
            }
    }
}
```

---

## 11. パフォーマンス最適化のポイント

### 11.1 遅延解決の活用

Analysis APIは遅延解析を行うため、必要な情報のみを取得するようにします。

```kotlin
analyze(file) {
    // ✓ 良い例: 必要な情報だけ取得
    val symbol = element.mainReference?.resolveToSymbol()
    val name = (symbol as? KaNamedSymbol)?.name
    
    // ✗ 悪い例: 不要な情報まで取得
    val allSymbols = file.fileSymbol.fileScope.getAllSymbols().toList()
}
```

### 11.2 KaSymbolPointerの活用

セッション間でシンボルを保持する場合は必ず`KaSymbolPointer`を使用します。

```kotlin
// ✓ 良い例
class SymbolCache {
    private val pointers = mutableMapOf<String, KaSymbolPointer<*>>()
    
    fun cache(key: String, symbol: KaSymbol) {
        pointers[key] = symbol.createPointer()
    }
    
    fun restore(key: String, context: KtElement): KaSymbol? {
        return analyze(context) {
            pointers[key]?.restoreSymbol()
        }
    }
}

// ✗ 悪い例: メモリリーク
class BadSymbolCache {
    private val symbols = mutableMapOf<String, KaSymbol>() // メモリリーク！
}
```

### 11.3 読み取りアクションの最小化

`analyze {}`ブロックの呼び出しは読み取りロックを取得するため、最小限にします。

```kotlin
// ✓ 良い例: 1回のanalyzeブロック内で処理
fun processElements(elements: List<KtElement>) {
    analyze(elements.first()) {
        elements.forEach { element ->
            val type = element.expressionType
            // 処理
        }
    }
}

// ✗ 悪い例: 要素ごとにanalyzeを呼び出し
fun processElements(elements: List<KtElement>) {
    elements.forEach { element ->
        analyze(element) { // ロック取得が多すぎる
            val type = element.expressionType
        }
    }
}
```

---

## 12. 注意事項とベストプラクティス

### 12.1 API安定性

- **安定版**: 主要なシンボル/型関連API
- **実験的**: スタンドアロンモードの一部機能（`@KaExperimentalApi`）
- **変更の可能性**: APIは進化中で、特にIntelliJ IDEA 2024.3でシンボル名が変更された（`KtSymbol` → `KaSymbol`）

### 12.2 命名規則の変化

```kotlin
// 旧API (Kotlin 1.x, IntelliJ IDEA 2024.2以前)
val symbol: KtSymbol
val type: KtType

// 新API (Kotlin 2.0+, IntelliJ IDEA 2024.3+)
val symbol: KaSymbol
val type: KaType
```

**Ka = Kotlin Analysis API**

### 12.3 読み取りアクションの必須化

```kotlin
// 必ず読み取りアクション内で実行
@RequiresReadLock
fun analyze(element: KtElement) {
    analyze(element) {
        // Analysis API使用
    }
}

// IntelliJ Platformでの読み取りアクション取得
ReadAction.run<Unit> {
    analyze(element) {
        // ...
    }
}
```

---

## 13. 依存関係の設定

### 13.1 Gradle設定

```kotlin
dependencies {
    // Analysis API本体
    implementation("org.jetbrains.kotlin:analysis-api-standalone:2.0.21")
    
    // スタンドアロンモード用
    implementation("org.jetbrains.kotlin:analysis-api-standalone-base:2.0.21")
    
    // FIR実装（K2）
    implementation("org.jetbrains.kotlin:analysis-api-fir:2.0.21")
    
    // PSI
    implementation("org.jetbrains.kotlin:kotlin-compiler:2.0.21")
    
    // IntelliJ Platform（必要に応じて）
    implementation("com.jetbrains.intellij.platform:core:2024.3")
}
```

### 13.2 Maven設定

```xml
<dependencies>
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>analysis-api-standalone</artifactId>
        <version>2.0.21</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>analysis-api-standalone-base</artifactId>
        <version>2.0.21</version>
    </dependency>
</dependencies>
```

---

## 14. Language Server実装のロードマップ

### Phase 1: 基本セットアップ（1週間）
- [ ] スタンドアロンセッションの構築
- [ ] プロジェクト構造の定義（ソースルート、依存関係）
- [ ] PSI要素の基本操作確認

### Phase 2: コア機能実装（2週間）
- [ ] 補完機能（スコープベース）
- [ ] 定義ジャンプ
- [ ] ホバー情報
- [ ] 基本的な診断情報

### Phase 3: 高度な機能（2週間）
- [ ] 参照検索
- [ ] シグネチャヘルプ
- [ ] ドキュメントシンボル
- [ ] ワークスペースシンボル検索

### Phase 4: 最適化（1週間）
- [ ] KaSymbolPointerによるキャッシング
- [ ] インクリメンタル更新対応
- [ ] パフォーマンスチューニング

---

## 15. 参考リソース

### 公式ドキュメント
- [Kotlin Analysis API Documentation](https://kotlin.github.io/analysis-api/)
- [Kotlin Analysis API Repository](https://github.com/Kotlin/analysis-api)
- [Kotlin Compiler Repository](https://github.com/JetBrains/kotlin)

### 実装例
- [Android Lint - AST Analysis](https://googlesamples.github.io/android-custom-lint-rules/api-guide/ast-analysis.md.html)
- Dokka（ドキュメント生成ツール）のK2移行
- IntelliJ IDEA Kotlin Plugin

### コミュニティ
- [Kotlin Slack - #compiler チャンネル](https://kotlinlang.slack.com/)
- [JetBrains YouTrack - Analysis API Issues](https://youtrack.jetbrains.com/issues/KT)

---

## まとめ

Kotlin 2.0のK2 Analysis APIは、Language Server実装に必要な以下の機能を提供します：

1. **セマンティック解析**: シンボル解決、型推論、呼び出し解決
2. **スタンドアロンモード**: IDE外での使用をサポート
3. **効率的な設計**: 遅延解析、キャッシュ無効化の自動処理
4. **ライフタイム管理**: 明確なセッションベースの設計
5. **診断情報**: コンパイラレベルのエラー・警告の取得

**重要な制約**:
- `analyze {}`ブロック内でのみ使用可能
- `KaLifetimeOwner`の長期保持禁止
- セッション間のデータ受け渡しは`KaSymbolPointer`を使用

これらのAPIを適切に活用することで、高性能なKotlin Language Serverを実装できます。

