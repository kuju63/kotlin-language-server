package com.kotlinls.persistence

/**
 * SQLiteデータベーススキーマ定義
 * 
 * WALモードでの運用を前提とした設計
 * Phase 3で実際のデータベース統合を実装予定
 */
object DatabaseSchema {
    
    /**
     * WALモード有効化とパフォーマンス最適化
     */
    const val INITIALIZE_PRAGMAS = """
        PRAGMA journal_mode=WAL;
        PRAGMA synchronous=NORMAL;
        PRAGMA cache_size=-200000;
        PRAGMA temp_store=MEMORY;
        PRAGMA mmap_size=268435456;
        PRAGMA foreign_keys=ON;
    """
    
    /**
     * ファイル管理テーブル
     */
    const val CREATE_FILES_TABLE = """
        CREATE TABLE IF NOT EXISTS files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            path TEXT NOT NULL UNIQUE,
            content_hash TEXT NOT NULL,
            last_indexed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            line_count INTEGER,
            size_bytes INTEGER
        );
        
        CREATE INDEX IF NOT EXISTS idx_files_path ON files(path);
        CREATE INDEX IF NOT EXISTS idx_files_hash ON files(content_hash);
    """
    
    /**
     * シンボル定義テーブル
     */
    const val CREATE_SYMBOLS_TABLE = """
        CREATE TABLE IF NOT EXISTS symbols (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            qualified_name TEXT,
            kind INTEGER NOT NULL,
            file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
            start_line INTEGER NOT NULL,
            start_column INTEGER NOT NULL,
            end_line INTEGER NOT NULL,
            end_column INTEGER NOT NULL,
            parent_id INTEGER REFERENCES symbols(id) ON DELETE CASCADE,
            type_signature TEXT,
            documentation TEXT,
            UNIQUE(file_id, qualified_name)
        );
        
        CREATE INDEX IF NOT EXISTS idx_symbols_name ON symbols(name);
        CREATE INDEX IF NOT EXISTS idx_symbols_qualified ON symbols(qualified_name);
        CREATE INDEX IF NOT EXISTS idx_symbols_file ON symbols(file_id);
    """
    
    /**
     * 参照テーブル
     */
    const val CREATE_REFERENCES_TABLE = """
        CREATE TABLE IF NOT EXISTS references (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            symbol_id INTEGER NOT NULL REFERENCES symbols(id) ON DELETE CASCADE,
            file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
            start_line INTEGER NOT NULL,
            start_column INTEGER NOT NULL,
            end_line INTEGER NOT NULL,
            end_column INTEGER NOT NULL,
            kind INTEGER DEFAULT 0
        );
        
        CREATE INDEX IF NOT EXISTS idx_refs_symbol ON references(symbol_id);
        CREATE INDEX IF NOT EXISTS idx_refs_file ON references(file_id);
    """
    
    /**
     * 全文検索テーブル（FTS5）
     */
    const val CREATE_FTS_TABLE = """
        CREATE VIRTUAL TABLE IF NOT EXISTS symbols_fts USING fts5(
            name, 
            qualified_name, 
            documentation,
            content=symbols, 
            content_rowid=id,
            tokenize='trigram'
        );
    """
}
