package io.github.kuju63.kotlin.lang.lsp

import io.github.kuju63.kotlin.lang.server.KotlinLanguageServer
import io.mockk.mockk
import org.eclipse.lsp4j.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName

/**
 * KotlinTextDocumentServiceのテスト
 */
class KotlinTextDocumentServiceTest {
    
    private lateinit var server: KotlinLanguageServer
    private lateinit var service: KotlinTextDocumentService
    
    @BeforeEach
    fun setUp() {
        server = mockk(relaxed = true)
        service = KotlinTextDocumentService(server)
    }
    
    @Test
    @DisplayName("ドキュメントを開くと内容が保存されること")
    fun `didOpen should store document content`() {
        val params = DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = "file:///test.kt"
                languageId = "kotlin"
                version = 1
                text = "fun main() {}"
            }
        }
        
        service.didOpen(params)
        
        val content = service.getDocumentContent("file:///test.kt")
        assertEquals("fun main() {}", content)
    }
    
    @Test
    @DisplayName("ドキュメントの変更が正しく適用されること")
    fun `didChange should apply document changes`() {
        // 最初にドキュメントを開く
        service.didOpen(DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = "file:///test.kt"
                languageId = "kotlin"
                version = 1
                text = "fun main() {}"
            }
        })
        
        // 変更を適用
        service.didChange(DidChangeTextDocumentParams().apply {
            textDocument = VersionedTextDocumentIdentifier().apply {
                uri = "file:///test.kt"
                version = 2
            }
            contentChanges = listOf(
                TextDocumentContentChangeEvent().apply {
                    range = Range(Position(0, 4), Position(0, 8)) // "main"
                    text = "example"
                }
            )
        })
        
        val content = service.getDocumentContent("file:///test.kt")
        assertEquals("fun example() {}", content)
    }
    
    @Test
    @DisplayName("ドキュメントを閉じると削除されること")
    fun `didClose should remove document`() {
        // ドキュメントを開く
        service.didOpen(DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = "file:///test.kt"
                languageId = "kotlin"
                version = 1
                text = "fun main() {}"
            }
        })
        
        // ドキュメントを閉じる
        service.didClose(DidCloseTextDocumentParams().apply {
            textDocument = TextDocumentIdentifier("file:///test.kt")
        })
        
        val content = service.getDocumentContent("file:///test.kt")
        assertNull(content)
    }
    
    @Test
    @DisplayName("補完リクエストが結果を返すこと")
    fun `completion should return results`() {
        // ドキュメントを開く
        service.didOpen(DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = "file:///test.kt"
                languageId = "kotlin"
                version = 1
                text = "fun main() {\n    pri\n}"
            }
        })
        
        val result = service.completion(CompletionParams().apply {
            textDocument = TextDocumentIdentifier("file:///test.kt")
            position = Position(1, 7)
        }).get()
        
        assertTrue(result.isRight)
        val completionList = result.right
        assertNotNull(completionList)
        assertTrue(completionList.items.isNotEmpty())
    }
    
    @Test
    @DisplayName("ホバーリクエストが処理されること")
    fun `hover should be processed`() {
        service.didOpen(DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = "file:///test.kt"
                languageId = "kotlin"
                version = 1
                text = "fun main() {}"
            }
        })
        
        val result = service.hover(HoverParams().apply {
            textDocument = TextDocumentIdentifier("file:///test.kt")
            position = Position(0, 4)
        }).get()
        
        assertNotNull(result)
    }
    
    @Test
    @DisplayName("定義ジャンプリクエストが処理されること")
    fun `definition should be processed`() {
        service.didOpen(DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = "file:///test.kt"
                languageId = "kotlin"
                version = 1
                text = "fun main() {}"
            }
        })
        
        val result = service.definition(DefinitionParams().apply {
            textDocument = TextDocumentIdentifier("file:///test.kt")
            position = Position(0, 4)
        }).get()
        
        assertNotNull(result)
    }
}
