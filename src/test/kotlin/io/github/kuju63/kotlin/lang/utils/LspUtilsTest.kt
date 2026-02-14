package io.github.kuju63.kotlin.lang.utils

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

/**
 * LSPユーティリティのテスト
 */
class LspUtilsTest {
    
    @Test
    @DisplayName("Position to Offset変換が正しく動作すること")
    fun `positionToOffset should work correctly`() {
        val content = "line1\nline2\nline3"
        
        // 最初の行の最初
        assertEquals(0, PositionUtils.positionToOffset(content, Position(0, 0)))
        
        // 最初の行の最後
        assertEquals(5, PositionUtils.positionToOffset(content, Position(0, 5)))
        
        // 2行目の最初
        assertEquals(6, PositionUtils.positionToOffset(content, Position(1, 0)))
        
        // 2行目の途中
        assertEquals(9, PositionUtils.positionToOffset(content, Position(1, 3)))
    }
    
    @Test
    @DisplayName("Offset to Position変換が正しく動作すること")
    fun `offsetToPosition should work correctly`() {
        val content = "line1\nline2\nline3"
        
        // offset 0 -> (0, 0)
        assertEquals(Position(0, 0), PositionUtils.offsetToPosition(content, 0))
        
        // offset 6 -> (1, 0)
        assertEquals(Position(1, 0), PositionUtils.offsetToPosition(content, 6))
        
        // offset 9 -> (1, 3)
        assertEquals(Position(1, 3), PositionUtils.offsetToPosition(content, 9))
    }
    
    @Test
    @DisplayName("URIからパスへの変換が正しく動作すること")
    fun `uriToPath should work correctly`() {
        assertEquals("/path/to/file.kt", UriUtils.uriToPath("file:///path/to/file.kt"))
        assertEquals("/path/to/file.kt", UriUtils.uriToPath("file:/path/to/file.kt"))
        assertEquals("/path/to/file.kt", UriUtils.uriToPath("/path/to/file.kt"))
    }
    
    @Test
    @DisplayName("パスからURIへの変換が正しく動作すること")
    fun `pathToUri should work correctly`() {
        assertEquals("file:///path/to/file.kt", UriUtils.pathToUri("/path/to/file.kt"))
        assertEquals("file:///path/to/file.kt", UriUtils.pathToUri("file:///path/to/file.kt"))
    }
    
    @Test
    @DisplayName("全体変更が正しく適用されること")
    fun `applyContentChange should handle full document changes`() {
        val original = "original content"
        val newText = "new content"
        
        val result = TextEditUtils.applyContentChange(original, null, newText)
        
        assertEquals(newText, result)
    }
    
    @Test
    @DisplayName("単一行の部分変更が正しく適用されること")
    fun `applyContentChange should handle single line changes`() {
        val original = "Hello World"
        val range = Range(Position(0, 6), Position(0, 11)) // "World"
        val newText = "Kotlin"
        
        val result = TextEditUtils.applyContentChange(original, range, newText)
        
        assertEquals("Hello Kotlin", result)
    }
    
    @Test
    @DisplayName("複数行にまたがる変更が正しく適用されること")
    fun `applyContentChange should handle multi-line changes`() {
        val original = "line1\nline2\nline3"
        val range = Range(Position(0, 2), Position(1, 3)) // "ne1\nlin"
        val newText = "XX"
        
        val result = TextEditUtils.applyContentChange(original, range, newText)
        
        assertEquals("liXXe2\nline3", result)
    }
}
