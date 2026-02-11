# Kotlin Language Server - ビルド・テスト手順書

## 環境要件

### 必須

- **JDK**: 17以上（推奨: JDK 17 LTS）
- **Gradle**: 8.5以上（Gradle Wrapper付属のため、インストール不要）
- **メモリ**: 最低2GB RAM（4GB推奨）

### 推奨

- **OS**: Linux, macOS, Windows 10/11
- **IDE**: IntelliJ IDEA 2023.3以上（Community/Ultimateどちらでも可）
- **ディスク容量**: 1GB以上の空き容量

## セットアップ

### 1. JDKのインストール確認

```bash
# Javaバージョン確認
java -version

# 期待される出力（バージョン17以上）:
# openjdk version "17.0.9" 2023-10-17
# OpenJDK Runtime Environment (build 17.0.9+9-Ubuntu-122.04)
```

JDKがインストールされていない場合:

**Ubuntu/Debian**:
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

**macOS (Homebrew)**:
```bash
brew install openjdk@17
```

**Windows**:
- [Adoptium](https://adoptium.net/)からダウンロード

### 2. プロジェクトのクローン/ダウンロード

```bash
# プロジェクトディレクトリに移動
cd kotlin-language-server

# ファイル構成確認
ls -la
```

期待されるファイル:
```
build.gradle.kts
settings.gradle.kts
gradle.properties
gradlew (実行可能)
gradle/
src/
```

## ビルド手順

### ステップ1: 依存関係のダウンロード

```bash
# Gradleが自動的に依存関係をダウンロード
./gradlew dependencies

# 初回は時間がかかります（5-10分程度）
```

### ステップ2: プロジェクトのビルド

```bash
# ソースコードをコンパイル
./gradlew assemble
```

**成功時の出力**:
```
> Task :compileKotlin
> Task :compileJava NO-SOURCE
> Task :processResources
> Task :classes
> Task :inspectClassesForKotlinIC
> Task :jar
> Task :assemble

BUILD SUCCESSFUL in 45s
7 actionable tasks: 7 executed
```

**ビルド成果物の確認**:
```bash
ls -lh build/libs/

# 出力例:
# kotlin-language-server-0.1.0-SNAPSHOT.jar
```

### ステップ3: Fat JARの作成（オプション）

```bash
# すべての依存関係を含むJARを生成
./gradlew shadowJar

# 生成されたJAR
ls -lh build/libs/kotlin-language-server-0.1.0-SNAPSHOT.jar
```

## テスト実行

### 全テストの実行

```bash
./gradlew test
```

**成功時の出力**:
```
> Task :compileKotlin UP-TO-DATE
> Task :compileTestKotlin
> Task :test

KotlinLanguageServerTest > server should be instantiated PASSED
KotlinLanguageServerTest > initialize should succeed PASSED
KotlinLanguageServerTest > text document service should be available PASSED
KotlinLanguageServerTest > workspace service should be available PASSED
KotlinLanguageServerTest > completion should be enabled PASSED
KotlinLanguageServerTest > shutdown should complete successfully PASSED

LspUtilsTest > positionToOffset should work correctly PASSED
LspUtilsTest > offsetToPosition should work correctly PASSED
LspUtilsTest > uriToPath should work correctly PASSED
LspUtilsTest > pathToUri should work correctly PASSED
LspUtilsTest > applyContentChange should handle full document changes PASSED
LspUtilsTest > applyContentChange should handle single line changes PASSED
LspUtilsTest > applyContentChange should handle multi-line changes PASSED

K2AnalysisProviderTest > provider should be instantiated PASSED
K2AnalysisProviderTest > initialize should complete successfully PASSED
K2AnalysisProviderTest > getSymbolAtPosition should be processed PASSED
K2AnalysisProviderTest > getTypeAtPosition should be processed PASSED
K2AnalysisProviderTest > getSymbolsInScope should be processed PASSED
K2AnalysisProviderTest > findReferences should be processed PASSED
K2AnalysisProviderTest > getDiagnostics should be processed PASSED
K2AnalysisProviderTest > shutdown should complete successfully PASSED

KotlinTextDocumentServiceTest > didOpen should store document content PASSED
KotlinTextDocumentServiceTest > didChange should apply document changes PASSED
KotlinTextDocumentServiceTest > didClose should remove document PASSED
KotlinTextDocumentServiceTest > completion should return results PASSED
KotlinTextDocumentServiceTest > hover should be processed PASSED
KotlinTextDocumentServiceTest > definition should be processed PASSED

BUILD SUCCESSFUL in 12s
27 tests, 27 successes
```

### 特定のテストクラスのみ実行

```bash
# 特定のテストクラス
./gradlew test --tests KotlinLanguageServerTest

# 特定のテストメソッド
./gradlew test --tests "KotlinLanguageServerTest.initialize should succeed"
```

### テストレポートの確認

```bash
# テストレポートをブラウザで開く
open build/reports/tests/test/index.html   # macOS
xdg-open build/reports/tests/test/index.html  # Linux
start build/reports/tests/test/index.html  # Windows
```

## 実行

### 開発モードで実行

```bash
# Gradleから直接実行
./gradlew run

# 出力例:
# 15:30:45.123 [main] INFO  com.kotlinls.server.MainKt - Kotlin Language Server starting...
# 15:30:45.234 [main] INFO  com.kotlinls.server.MainKt - Version: 0.1.0-SNAPSHOT
# 15:30:45.345 [main] INFO  com.kotlinls.server.MainKt - Kotlin: 2.0.21
```

### JARから実行

```bash
# ビルド済みJARを実行
java -Xmx2G -jar build/libs/kotlin-language-server-0.1.0-SNAPSHOT.jar
```

## トラブルシューティング

### エラー: "Could not determine Java version"

**原因**: JDKのバージョンが古い

**解決策**:
```bash
# JDKバージョン確認
java -version

# JDK 17以上にアップグレード
```

### エラー: "Execution failed for task ':compileKotlin'"

**原因**: Kotlinコンパイラエラー

**解決策**:
```bash
# クリーンビルド
./gradlew clean build

# Gradleキャッシュをクリア
./gradlew clean --refresh-dependencies
```

### エラー: "Test failed"

**原因**: テストケースの失敗

**解決策**:
```bash
# 詳細なテストログを表示
./gradlew test --info

# 失敗したテストのみ再実行
./gradlew test --rerun-tasks
```

### ビルドが遅い

**原因**: 依存関係のダウンロードやコンパイル

**解決策**:
```bash
# Gradleデーモンを有効化（gradle.propertiesで設定済み）
# 並列ビルドを有効化（設定済み）

# メモリを増やす
export GRADLE_OPTS="-Xmx4096m"
./gradlew build
```

### Windows: "Permission denied"

**原因**: gradlewスクリプトの実行権限

**解決策**:
```bash
# Git Bashを使用
bash gradlew build

# または PowerShellで
.\gradlew.bat build
```

## 継続的インテグレーション

### GitHub Actions設定例

```yaml
name: Build and Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Build with Gradle
      run: ./gradlew assemble
    
    - name: Run tests
      run: ./gradlew test
    
    - name: Upload test results
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: test-results
        path: build/reports/tests/
```

## パフォーマンス測定

### ビルド時間

```bash
# ビルド時間を測定
time ./gradlew clean build

# 期待される時間:
# - 初回ビルド: 1-3分
# - インクリメンタルビルド: 5-15秒
```

### テスト実行時間

```bash
# テスト時間を測定
time ./gradlew test

# 期待される時間:
# - 27テスト: 3-10秒
```

## 次のステップ

ビルドとテストが成功したら:

1. **IntelliJ IDEAでプロジェクトを開く**
   - `File > Open` → `kotlin-language-server`

2. **Phase 2の機能実装を開始**
   - K2 Analysis API統合
   - 補完機能の実装

3. **VS Code拡張の作成**
   - Language Serverとの統合

---

**重要**: すべてのコマンドは、プロジェクトのルートディレクトリ（`kotlin-language-server/`）で実行してください。
