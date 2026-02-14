package io.github.kuju63.kotlin.lang.lsp

import io.github.kuju63.kotlin.lang.server.KotlinLanguageServer
import io.github.kuju63.kotlin.lang.utils.PositionUtils
import io.github.kuju63.kotlin.lang.utils.TextEditUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * テキストドキュメント関連のLSP機能実装
 * 
 * 開発者が求める主要機能のスケルトン:
 * - Code Completion (補完) ✅
 * - Go-to-Definition (定義ジャンプ) ✅
 * - Find References (参照検索) ✅
 * - Hover (ホバー情報) ✅
 * - Signature Help (シグネチャヘルプ) ✅
 * - Document Symbol ✅
 * - Diagnostics (診断情報) ✅
 * - Formatting ✅
 * - Code Action ✅
 * - Rename ✅
 */
class KotlinTextDocumentService(
    private val server: KotlinLanguageServer
) : TextDocumentService {
    
    // ファイルURIからドキュメント内容へのマッピング
    private val documents = ConcurrentHashMap<String, DocumentState>()
    
    /**
     * ドキュメント状態
     */
    data class DocumentState(
        var content: String,
        var version: Int,
        val uri: String
    )
    
    // ========== ドキュメントライフサイクル ==========
    
    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.textDocument.uri
        val content = params.textDocument.text
        val version = params.textDocument.version
        
        logger.info { "Document opened: $uri (version: $version)" }
        documents[uri] = DocumentState(content, version, uri)
        
        // 診断情報を生成して送信
        publishDiagnostics(uri, content)
    }
    
    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = params.textDocument.uri
        val version = params.textDocument.version
        val changes = params.contentChanges
        
        logger.debug { "Document changed: $uri (version: $version, ${changes.size} changes)" }
        
        val state = documents[uri]
        if (state == null) {
            logger.warn { "Document not found: $uri" }
            return
        }
        
        // インクリメンタル変更を適用
        var content = state.content
        for (change in changes) {
            content = TextEditUtils.applyContentChange(content, change.range, change.text)
        }
        
        state.content = content
        state.version = version
        
        // 診断情報を更新
        publishDiagnostics(uri, content)
    }
    
    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = params.textDocument.uri
        logger.info { "Document closed: $uri" }
        documents.remove(uri)
        
        // 診断情報をクリア
        server.logMessage(MessageType.Log, "Cleared diagnostics for $uri")
    }
    
    override fun didSave(params: DidSaveTextDocumentParams) {
        val uri = params.textDocument.uri
        logger.info { "Document saved: $uri" }
        
        // 保存時の処理（フォーマット、自動import整理など）
        // TODO: 実装
    }
    
    // ========== 1. Code Completion (最重要) ==========
    
    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CompletableFuture.supplyAsync {
            try {
                val uri = params.textDocument.uri
                val position = params.position
                
                logger.debug { "Completion requested: $uri at ${position.line}:${position.character}" }
                
                val state = documents[uri]
                if (state == null) {
                    logger.warn { "Document not found for completion: $uri" }
                    return@supplyAsync Either.forRight(CompletionList(false, emptyList()))
                }
                
                // TODO: K2 Analysis APIを使用して補完候補を取得
                val items = generateCompletionItems(state, position)
                
                logger.debug { "Generated ${items.size} completion items" }
                Either.forRight(CompletionList(false, items))
            } catch (e: Exception) {
                logger.error(e) { "Error during completion" }
                Either.forRight(CompletionList(false, emptyList()))
            }
        }
    }
    
    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        return CompletableFuture.supplyAsync {
            try {
                // TODO: 補完アイテムの詳細情報（ドキュメント、型情報など）を取得
                unresolved.apply {
                    if (detail == null) {
                        detail = "Type information"
                    }
                    if (documentation == null) {
                        documentation = Either.forLeft("Documentation for ${unresolved.label}")
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error resolving completion item" }
                unresolved
            }
        }
    }
    
    private fun generateCompletionItems(state: DocumentState, position: Position): List<CompletionItem> {
        // スケルトン実装: サンプル候補を返す
        return listOf(
            CompletionItem("println").apply {
                kind = CompletionItemKind.Function
                detail = "kotlin.io"
                insertText = "println(\$1)"
                insertTextFormat = InsertTextFormat.Snippet
            },
            CompletionItem("fun").apply {
                kind = CompletionItemKind.Keyword
                insertText = "fun \${1:name}(\$2): \${3:Unit} {\n\t\$0\n}"
                insertTextFormat = InsertTextFormat.Snippet
            },
            CompletionItem("class").apply {
                kind = CompletionItemKind.Keyword
                insertText = "class \${1:Name} {\n\t\$0\n}"
                insertTextFormat = InsertTextFormat.Snippet
            }
        )
    }
    
    // ========== 2. Go-to-Definition ==========
    
    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        return CompletableFuture.supplyAsync {
            try {
                val uri = params.textDocument.uri
                val position = params.position
                
                logger.debug { "Definition requested: $uri at ${position.line}:${position.character}" }
                
                // TODO: シンボル解決と定義位置の特定
                
                // スケルトン実装: 空リストを返す
                Either.forLeft(emptyList())
            } catch (e: Exception) {
                logger.error(e) { "Error during definition lookup" }
                Either.forLeft(emptyList())
            }
        }
    }
    
    override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        return CompletableFuture.supplyAsync {
            try {
                logger.debug { "Type definition requested: ${params.textDocument.uri}" }
                // TODO: 型定義の解決
                Either.forLeft(emptyList())
            } catch (e: Exception) {
                logger.error(e) { "Error during type definition lookup" }
                Either.forLeft(emptyList())
            }
        }
    }
    
    override fun implementation(params: ImplementationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        return CompletableFuture.supplyAsync {
            try {
                logger.debug { "Implementation requested: ${params.textDocument.uri}" }
                // TODO: 実装の検索
                Either.forLeft(emptyList())
            } catch (e: Exception) {
                logger.error(e) { "Error during implementation lookup" }
                Either.forLeft(emptyList())
            }
        }
    }
    
    // ========== 3. Find References ==========
    
    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
        return CompletableFuture.supplyAsync {
            try {
                val uri = params.textDocument.uri
                val position = params.position
                
                logger.debug { "References requested: $uri at ${position.line}:${position.character}" }
                
                // TODO: SQLiteから参照情報を検索
                
                // スケルトン実装
                emptyList()
            } catch (e: Exception) {
                logger.error(e) { "Error during references lookup" }
                emptyList()
            }
        }
    }
    
    // ========== 4. Hover ==========
    
    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        return CompletableFuture.supplyAsync {
            try {
                val uri = params.textDocument.uri
                val position = params.position
                
                logger.debug { "Hover requested: $uri at ${position.line}:${position.character}" }
                
                val state = documents[uri] ?: return@supplyAsync null
                
                // TODO: K2 Analysis APIを使用して型情報・ドキュメントを取得
                
                // スケルトン実装: サンプルホバー情報
                Hover().apply {
                    contents = Either.forLeft(
                        listOf(Either.forLeft("fun example(): String"))
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Error during hover" }
                null
            }
        }
    }
    
    // ========== 5. Signature Help ==========
    
    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> {
        return CompletableFuture.supplyAsync {
            try {
                val uri = params.textDocument.uri
                val position = params.position
                
                logger.debug { "Signature help requested: $uri at ${position.line}:${position.character}" }
                
                // TODO: 関数シグネチャ情報を取得
                
                // スケルトン実装
                SignatureHelp().apply {
                    signatures = listOf(
                        SignatureInformation().apply {
                            label = "fun example(param1: String, param2: Int): Boolean"
                            documentation = Either.forLeft("Example function signature")
                            parameters = listOf(
                                ParameterInformation().apply {
                                    label = Either.forLeft("param1: String")
                                },
                                ParameterInformation().apply {
                                    label = Either.forLeft("param2: Int")
                                }
                            )
                        }
                    )
                    activeSignature = 0
                    activeParameter = 0
                }
            } catch (e: Exception) {
                logger.error(e) { "Error during signature help" }
                null
            }
        }
    }
    
    // ========== 6. Document Symbol ==========
    
    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        return CompletableFuture.supplyAsync {
            try {
                val uri = params.textDocument.uri
                
                logger.debug { "Document symbols requested: $uri" }
                
                // TODO: ファイル内のシンボル一覧を取得
                
                // スケルトン実装
                emptyList()
            } catch (e: Exception) {
                logger.error(e) { "Error getting document symbols" }
                emptyList()
            }
        }
    }
    
    // ========== 7. Formatting ==========
    
    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        return CompletableFuture.supplyAsync {
            try {
                val uri = params.textDocument.uri
                
                logger.debug { "Formatting requested: $uri" }
                
                // TODO: kotlinter/ktlintを使用してフォーマット
                
                // スケルトン実装
                emptyList()
            } catch (e: Exception) {
                logger.error(e) { "Error during formatting" }
                emptyList()
            }
        }
    }
    
    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> {
        return CompletableFuture.supplyAsync {
            try {
                logger.debug { "Range formatting requested: ${params.textDocument.uri}" }
                // TODO: 範囲指定フォーマット
                emptyList()
            } catch (e: Exception) {
                logger.error(e) { "Error during range formatting" }
                emptyList()
            }
        }
    }
    
    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> {
        return CompletableFuture.supplyAsync {
            try {
                logger.debug { "On-type formatting requested: ${params.textDocument.uri}" }
                // TODO: タイプ時のフォーマット（}入力後の自動インデントなど）
                emptyList()
            } catch (e: Exception) {
                logger.error(e) { "Error during on-type formatting" }
                emptyList()
            }
        }
    }
    
    // ========== 8. Code Action ==========
    
    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
        return CompletableFuture.supplyAsync {
            try {
                val uri = params.textDocument.uri
                val range = params.range
                
                logger.debug { "Code action requested: $uri" }
                
                // TODO: リファクタリング候補を生成
                
                // スケルトン実装: サンプルコードアクション
                val actions = listOf(
                    CodeAction("Organize imports").apply {
                        kind = CodeActionKind.SourceOrganizeImports
                        isPreferred = true
                    },
                    CodeAction("Extract variable").apply {
                        kind = CodeActionKind.RefactorExtract
                    }
                )
                
                actions.map { Either.forRight<Command, CodeAction>(it) }
            } catch (e: Exception) {
                logger.error(e) { "Error during code action" }
                emptyList()
            }
        }
    }
    
    // ========== 9. Rename ==========
    
    override fun prepareRename(params: PrepareRenameParams): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> {
        return CompletableFuture.supplyAsync {
            try {
                logger.debug { "Prepare rename: ${params.textDocument.uri}" }
                
                // TODO: リネーム可能性をチェック
                
                // スケルトン実装: 常に許可
                Either3.forFirst(Range(params.position, params.position))
            } catch (e: Exception) {
                logger.error(e) { "Error during prepare rename" }
                throw e
            }
        }
    }
    
    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit?> {
        return CompletableFuture.supplyAsync {
            try {
                val uri = params.textDocument.uri
                val newName = params.newName
                
                logger.debug { "Rename to '$newName': $uri" }
                
                // TODO: すべての参照箇所を取得してWorkspaceEditを生成
                
                // スケルトン実装
                WorkspaceEdit().apply {
                    changes = emptyMap()
                }
            } catch (e: Exception) {
                logger.error(e) { "Error during rename" }
                null
            }
        }
    }
    
    // ========== 10. Document Highlight ==========
    
    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> {
        return CompletableFuture.supplyAsync {
            try {
                logger.debug { "Document highlight requested: ${params.textDocument.uri}" }
                // TODO: 同じシンボルの使用箇所をハイライト
                emptyList()
            } catch (e: Exception) {
                logger.error(e) { "Error during document highlight" }
                emptyList()
            }
        }
    }
    
    // ========== 診断情報 ==========
    
    private fun publishDiagnostics(uri: String, content: String) {
        try {
            // TODO: K2でコンパイルして診断情報を取得
            
            // スケルトン実装: サンプル診断情報
            val diagnostics = listOf<Diagnostic>(
                // 診断情報は実際のコンパイル結果から生成
            )
            
            logger.debug { "Publishing ${diagnostics.size} diagnostics for $uri" }
            server.logMessage(MessageType.Log, "Diagnostics: ${diagnostics.size} issues")
        } catch (e: Exception) {
            logger.error(e) { "Error publishing diagnostics" }
        }
    }
    
    // ========== ヘルパーメソッド ==========
    
    fun getDocumentContent(uri: String): String? {
        return documents[uri]?.content
    }
    
    fun getAllDocuments(): Map<String, DocumentState> {
        return documents.toMap()
    }
    
    fun shutdown() {
        logger.info { "Text document service shutting down" }
        documents.clear()
    }
}
