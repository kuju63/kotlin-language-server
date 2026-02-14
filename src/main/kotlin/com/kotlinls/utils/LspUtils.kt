package com.kotlinls.utils

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

/**
 * LSP Position/Range と PSI Offset の相互変換ユーティリティ
 */
object PositionUtils {
    
    /**
     * LSP Position を PSI offset に変換
     * 
     * @param content ファイル内容
     * @param position LSP Position（0-based line, 0-based character）
     * @return offset（0-based）
     */
    fun positionToOffset(content: String, position: Position): Int {
        val lines = content.lines()
        var offset = 0
        
        // 指定行までのオフセットを計算
        for (i in 0 until position.line.coerceAtMost(lines.size - 1)) {
            offset += lines[i].length + 1 // +1 for newline
        }
        
        // 行内のオフセットを追加
        if (position.line < lines.size) {
            offset += position.character.coerceAtMost(lines[position.line].length)
        }
        
        return offset.coerceAtMost(content.length)
    }
    
    /**
     * PSI offset を LSP Position に変換
     * 
     * @param content ファイル内容
     * @param offset PSI offset
     * @return LSP Position
     */
    fun offsetToPosition(content: String, offset: Int): Position {
        val lines = content.lines()
        var currentOffset = 0
        
        for ((lineNumber, line) in lines.withIndex()) {
            val lineEnd = currentOffset + line.length
            
            if (offset <= lineEnd) {
                val character = offset - currentOffset
                return Position(lineNumber, character)
            }
            
            currentOffset = lineEnd + 1 // +1 for newline
        }
        
        // Offset が範囲外の場合は最後の位置を返す
        return Position(lines.size - 1, lines.lastOrNull()?.length ?: 0)
    }
    
    /**
     * LSP Range を offset範囲に変換
     */
    fun rangeToOffsets(content: String, range: Range): Pair<Int, Int> {
        val start = positionToOffset(content, range.start)
        val end = positionToOffset(content, range.end)
        return Pair(start, end)
    }
    
    /**
     * Offset範囲を LSP Range に変換
     */
    fun offsetsToRange(content: String, startOffset: Int, endOffset: Int): Range {
        val start = offsetToPosition(content, startOffset)
        val end = offsetToPosition(content, endOffset)
        return Range(start, end)
    }
}

/**
 * URI ユーティリティ
 */
object UriUtils {
    
    /**
     * URIをファイルパスに変換
     */
    fun uriToPath(uri: String): String {
        return when {
            uri.startsWith("file://") -> {
                // file://path/to/file.kt -> /path/to/file.kt
                uri.substring(7)
            }
            uri.startsWith("file:") -> {
                // file:/path/to/file.kt -> /path/to/file.kt
                uri.substring(5)
            }
            else -> uri
        }
    }
    
    /**
     * ファイルパスをURIに変換
     */
    fun pathToUri(path: String): String {
        return if (!path.startsWith("file://")) {
            "file://$path"
        } else {
            path
        }
    }
}

/**
 * テキスト編集ユーティリティ
 */
object TextEditUtils {
    
    /**
     * インクリメンタルな変更を適用
     */
    fun applyContentChange(
        content: String,
        range: Range?,
        text: String
    ): String {
        // 全体変更の場合
        if (range == null) {
            return text
        }
        
        // 部分変更の場合
        val lines = content.lines().toMutableList()
        val startLine = range.start.line
        val startChar = range.start.character
        val endLine = range.end.line
        val endChar = range.end.character
        
        // 単一行の変更
        if (startLine == endLine) {
            val line = lines.getOrElse(startLine) { "" }
            val before = line.substring(0, startChar.coerceAtMost(line.length))
            val after = line.substring(endChar.coerceAtMost(line.length))
            lines[startLine] = before + text + after
        } else {
            // 複数行にまたがる変更
            val firstLine = lines.getOrElse(startLine) { "" }
            val lastLine = lines.getOrElse(endLine) { "" }
            
            val before = firstLine.substring(0, startChar.coerceAtMost(firstLine.length))
            val after = lastLine.substring(endChar.coerceAtMost(lastLine.length))
            val newContent = before + text + after
            val newLines = newContent.lines()
            
            // 古い行を削除
            if (endLine < lines.size) {
                lines.subList(startLine, endLine + 1).clear()
            }
            
            // 新しい行を挿入
            lines.addAll(startLine, newLines)
        }
        
        return lines.joinToString("\n")
    }
}
