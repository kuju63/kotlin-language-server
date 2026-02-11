package com.kotlinls.server

import com.kotlinls.lsp.KotlinTextDocumentService
import com.kotlinls.lsp.KotlinWorkspaceService
import mu.KotlinLogging
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

/**
 * Kotlin Language Serverのメイン実装
 * 
 * 開発者が求める主要機能:
 * 1. Code Completion (補完) - Critical
 * 2. Go-to-Definition (定義ジャンプ) - High
 * 3. Find References (参照検索) - High
 * 4. Hover (ホバー情報) - High
 * 5. Diagnostics (診断情報) - High
 * 6. Document Symbol - Medium
 * 7. Workspace Symbol - Low
 * 8. Signature Help - Medium
 * 9. Formatting - Medium
 * 10. Code Action/Refactoring - Medium
 */
class KotlinLanguageServer : LanguageServer, LanguageClientAware {
    
    private lateinit var client: LanguageClient
    private val textDocumentService: KotlinTextDocumentService by lazy {
        KotlinTextDocumentService(this)
    }
    private val workspaceService: KotlinWorkspaceService by lazy {
        KotlinWorkspaceService(this)
    }
    
    // リクエスト処理用スレッドプール
    private val executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    )
    
    @Volatile
    private var isInitialized = false
    
    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        logger.info { "Initializing Language Server" }
        logger.info { "Client: ${params.clientInfo?.name} ${params.clientInfo?.version}" }
        logger.info { "Root URI: ${params.rootUri}" }
        logger.info { "Workspace folders: ${params.workspaceFolders?.size ?: 0}" }
        
        // サーバーのケイパビリティを定義
        val capabilities = ServerCapabilities().apply {
            // テキスト同期モード（インクリメンタル）
            textDocumentSync = Either.forLeft(TextDocumentSyncKind.Incremental)
            
            // 1. Code Completion - 最重要機能
            completionProvider = CompletionOptions().apply {
                resolveProvider = true
                triggerCharacters = listOf(".", ":", "@", "(", "<", "\"", "/")
                allCommitCharacters = listOf(".", ",", "(", "[")
            }
            
            // 2. Go-to-Definition
            definitionProvider = Either.forLeft(true)
            
            // 3. Type Definition (型定義ジャンプ)
            typeDefinitionProvider = Either.forLeft(true)
            
            // 4. Implementation (実装ジャンプ)
            implementationProvider = Either.forLeft(true)
            
            // 5. References
            referencesProvider = Either.forLeft(true)
            
            // 6. Hover
            hoverProvider = Either.forLeft(true)
            
            // 7. Signature Help
            signatureHelpProvider = SignatureHelpOptions().apply {
                triggerCharacters = listOf("(", ",", "<")
                retriggerCharacters = listOf(",")
            }
            
            // 8. Document Symbols
            documentSymbolProvider = Either.forLeft(true)
            
            // 9. Workspace Symbol
            workspaceSymbolProvider = Either.forLeft(true)
            
            // 10. Code Action (リファクタリング)
            codeActionProvider = Either.forRight(CodeActionOptions().apply {
                codeActionKinds = listOf(
                    CodeActionKind.QuickFix,
                    CodeActionKind.Refactor,
                    CodeActionKind.RefactorExtract,
                    CodeActionKind.RefactorInline,
                    CodeActionKind.RefactorRewrite,
                    CodeActionKind.Source,
                    CodeActionKind.SourceOrganizeImports
                )
                resolveProvider = true
            })
            
            // 11. Document Formatting
            documentFormattingProvider = Either.forLeft(true)
            documentRangeFormattingProvider = Either.forLeft(true)
            documentOnTypeFormattingProvider = DocumentOnTypeFormattingOptions().apply {
                firstTriggerCharacter = "}"
                moreTriggerCharacter = listOf(";", "\n")
            }
            
            // 12. Rename
            renameProvider = Either.forRight(RenameOptions().apply {
                prepareProvider = true
            })
            
            // 13. Document Highlight
            documentHighlightProvider = Either.forLeft(true)
            
            // 14. Document Link
            documentLinkProvider = DocumentLinkOptions().apply {
                resolveProvider = true
            }
            
            // 15. Folding Range
            foldingRangeProvider = Either.forLeft(true)
            
            // 16. Selection Range
            selectionRangeProvider = Either.forLeft(true)
            
            // 17. Call Hierarchy
            callHierarchyProvider = Either.forLeft(true)
            
            // 18. Semantic Tokens
            semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
                legend = SemanticTokensLegend(
                    // Token types
                    listOf(
                        "namespace", "class", "interface", "enum", "typeParameter",
                        "function", "method", "property", "variable", "parameter",
                        "enumMember", "keyword", "comment", "string", "number",
                        "operator", "decorator"
                    ),
                    // Token modifiers
                    listOf(
                        "declaration", "definition", "readonly", "static",
                        "deprecated", "abstract", "async", "modification"
                    )
                )
                full = Either.forLeft(true)
                range = Either.forLeft(true)
            }
            
            // 19. Inlay Hint (型ヒント、パラメータ名など)
            inlayHintProvider = Either.forRight(InlayHintRegistrationOptions().apply {
                resolveProvider = true
            })
            
            // 20. Diagnostics (診断情報)
            diagnosticProvider = DiagnosicRegistrationOptions().apply {
                interFileDependencies = true
                workspaceDiagnostics = false
            }
            
            // Workspace capabilities
            workspace = WorkspaceServerCapabilities().apply {
                workspaceFolders = WorkspaceFoldersOptions().apply {
                    supported = true
                    changeNotifications = Either.forLeft(true)
                }
                
                fileOperations = FileOperationsServerCapabilities().apply {
                    didCreate = FileOperationRegistrationOptions().apply {
                        filters = listOf(
                            FileOperationFilter().apply {
                                pattern = FileOperationPattern("**/*.kt")
                            }
                        )
                    }
                    didRename = FileOperationRegistrationOptions().apply {
                        filters = listOf(
                            FileOperationFilter().apply {
                                pattern = FileOperationPattern("**/*.kt")
                            }
                        )
                    }
                    didDelete = FileOperationRegistrationOptions().apply {
                        filters = listOf(
                            FileOperationFilter().apply {
                                pattern = FileOperationPattern("**/*.kt")
                            }
                        )
                    }
                }
            }
        }
        
        val serverInfo = ServerInfo("Kotlin Language Server", "0.1.0-SNAPSHOT")
        
        return CompletableFuture.completedFuture(InitializeResult(capabilities, serverInfo))
    }
    
    override fun initialized(params: InitializedParams) {
        logger.info { "Server initialized successfully" }
        isInitialized = true
        
        // 初期化完了通知
        client.showMessage(MessageParams(MessageType.Info, "Kotlin Language Server initialized"))
        
        // 初期化後の処理（バックグラウンド）
        executor.submit {
            try {
                logger.info { "Starting background initialization..." }
                
                // TODO: 初期インデックス構築
                // TODO: Gradle依存関係の解決
                // TODO: SQLiteデータベースの初期化
                
                logger.info { "Background initialization completed" }
                client.showMessage(MessageParams(MessageType.Info, "Indexing completed"))
            } catch (e: Exception) {
                logger.error(e) { "Error during background initialization" }
                client.showMessage(MessageParams(MessageType.Error, "Initialization error: ${e.message}"))
            }
        }
    }
    
    override fun shutdown(): CompletableFuture<Any> {
        logger.info { "Shutting down Language Server" }
        
        return CompletableFuture.supplyAsync {
            try {
                isInitialized = false
                
                // リソースのクリーンアップ
                textDocumentService.shutdown()
                workspaceService.shutdown()
                executor.shutdown()
                
                logger.info { "Shutdown completed successfully" }
                null
            } catch (e: Exception) {
                logger.error(e) { "Error during shutdown" }
                throw e
            }
        }
    }
    
    override fun exit() {
        logger.info { "Exiting Language Server" }
        System.exit(if (isInitialized) 1 else 0)
    }
    
    override fun getTextDocumentService(): TextDocumentService {
        return textDocumentService
    }
    
    override fun getWorkspaceService(): WorkspaceService {
        return workspaceService
    }
    
    override fun connect(client: LanguageClient) {
        logger.info { "Connected to language client" }
        this.client = client
    }
    
    /**
     * クライアントへメッセージを送信
     */
    fun sendMessage(type: MessageType, message: String) {
        if (::client.isInitialized) {
            client.showMessage(MessageParams(type, message))
        }
    }
    
    /**
     * クライアントへログメッセージを送信
     */
    fun logMessage(type: MessageType, message: String) {
        if (::client.isInitialized) {
            client.logMessage(MessageParams(type, message))
        }
    }
    
    /**
     * クライアントから入力を要求
     */
    fun showInputBox(prompt: String): CompletableFuture<String?> {
        return CompletableFuture.completedFuture(null)
    }
}
