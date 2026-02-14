package io.github.kuju63.kotlin.lang.server

import io.github.oshai.kotlinlogging.KotlinLogging
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutionException
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Kotlin Language Server メインエントリーポイント
 * 
 * LSP4Jを使用してJSON-RPC over stdin/stdoutで通信を行う
 */
fun main() {
    logger.info { "Kotlin Language Server starting..." }
    logger.info { "Version: 0.1.0-SNAPSHOT" }
    logger.info { "Kotlin: ${KotlinVersion.CURRENT}" }
    
    try {
        val server = KotlinLanguageServer()
        startServer(server, System.`in`, System.out)
    } catch (e: Exception) {
        logger.error(e) { "Fatal error during server startup" }
        exitProcess(1)
    }
}

/**
 * LSP4J Launcherを使用してサーバーを起動
 */
fun startServer(
    server: KotlinLanguageServer,
    input: InputStream,
    output: OutputStream
) {
    logger.info { "Creating LSP launcher..." }
    
    val launcher = LSPLauncher.createServerLauncher(
        server,
        input,
        output
    )
    
    // クライアント接続
    val client = launcher.remoteProxy
    server.connect(client)
    
    logger.info { "Language Server connected, starting to listen..." }
    
    try {
        // メッセージリスニング開始（ブロッキング）
        val listening = launcher.startListening()
        listening.get()
    } catch (e: InterruptedException) {
        logger.warn { "Server interrupted" }
        Thread.currentThread().interrupt()
    } catch (e: ExecutionException) {
        logger.error(e) { "Server execution failed" }
        throw e
    }
    
    logger.info { "Language Server shutting down" }
}
