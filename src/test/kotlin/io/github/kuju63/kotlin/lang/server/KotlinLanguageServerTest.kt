package io.github.kuju63.kotlin.lang.server

import org.eclipse.lsp4j.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName

/**
 * KotlinLanguageServerの基本テスト
 */
class KotlinLanguageServerTest {
    
    private lateinit var server: KotlinLanguageServer
    
    @BeforeEach
    fun setUp() {
        server = KotlinLanguageServer()
    }
    
    @Test
    @DisplayName("サーバーが正常にインスタンス化できること")
    fun `server should be instantiated`() {
        assertNotNull(server)
    }
    
    @Test
    @DisplayName("初期化リクエストが成功すること")
    fun `initialize should succeed`() {
        val params = InitializeParams().apply {
            rootUri = "file:///test/project"
            capabilities = ClientCapabilities()
        }
        
        val result = server.initialize(params).get()
        
        assertNotNull(result)
        assertNotNull(result.capabilities)
        
        // 主要なケイパビリティが有効になっていることを確認
        assertTrue(result.capabilities.completionProvider != null)
        assertTrue(result.capabilities.definitionProvider?.isLeft == true)
        assertTrue(result.capabilities.referencesProvider?.isLeft == true)
        assertTrue(result.capabilities.hoverProvider?.isLeft == true)
    }
    
    @Test
    @DisplayName("TextDocumentServiceが取得できること")
    fun `text document service should be available`() {
        val textDocService = server.textDocumentService
        assertNotNull(textDocService)
    }
    
    @Test
    @DisplayName("WorkspaceServiceが取得できること")
    fun `workspace service should be available`() {
        val workspaceService = server.workspaceService
        assertNotNull(workspaceService)
    }
    
    @Test
    @DisplayName("補完機能が有効であること")
    fun `completion should be enabled`() {
        val params = InitializeParams().apply {
            rootUri = "file:///test/project"
            capabilities = ClientCapabilities()
        }
        
        val result = server.initialize(params).get()
        val completionProvider = result.capabilities.completionProvider
        
        assertNotNull(completionProvider)
        assertTrue(completionProvider.resolveProvider)
        assertTrue(completionProvider.triggerCharacters.contains("."))
    }
    
    @Test
    @DisplayName("シャットダウンが正常に完了すること")
    fun `shutdown should complete successfully`() {
        val shutdownResult = server.shutdown().get()
        assertNull(shutdownResult)
    }
}
