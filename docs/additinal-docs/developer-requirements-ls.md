# 静的型付け言語のLanguage Server機能要件：開発者が求めるもの

gopls、rust-analyzer、jdtlsの3つのLanguage Server実装を包括的に調査した結果、**コード補完の100ms以下のレイテンシ**、**大規模プロジェクトでのメモリ効率**、**インクリメンタル処理**がKotlin K2 Language Server実装の成功を左右する最重要要素であることが判明した。rust-analyzerは130以上のコードアクションで最も豊富なリファクタリングを提供し、gopls v0.12は「分離コンパイル」アーキテクチャで**75%のメモリ削減**を達成している。

---

## 必須機能：開発者が絶対に必要とする機能

3つのLanguage Serverすべてで、**Go-to-Definition**、**Find References**、**Code Completion**が最も頻繁に使用される機能として確認された。これらは「テーブルステークス」（最低限必要な機能）であり、品質の低さは即座にユーザー離脱につながる。

### コード補完の品質が最重要視される

開発者が最も敏感なのは補完の品質である。rust-analyzerユーザーは「auto-importを伴う補完」と「postfix completions」（例：`expr.if`、`expr.match`）を高く評価している。goplsでは「unimported packageからの補完候補が5件に制限されている」という不満がGitHub Issue #60988で報告されており、補完候補の数と精度のバランスが重要である。

| 機能 | 使用頻度 | 品質重要度 |
|------|---------|-----------|
| Code Completion | 最高 | 最重要 |
| Go-to-Definition | 最高 | 高 |
| Find References | 高 | 高 |
| Hover | 高 | 中 |
| Diagnostics | 高 | 高 |
| Rename | 中 | 最重要（破壊的変更のため） |

jdtlsでは「大規模プロジェクト（数百万行）での補完に20秒かかる一方、IntelliJでは2秒」という報告（GitHub Issue #3202）があり、**補完レイテンシはIDEの評価を決定づける**要素である。

---

## リファクタリング機能の詳細比較

### rust-analyzer：130以上のコードアクション

rust-analyzerは最も豊富なリファクタリング機能を提供している。

**Extract系**（抽出）:
- `extract_variable` / `extract_constant` / `extract_static`
- `extract_function`（ジェネリクスを適切に処理）
- `extract_module` / `extract_type_alias`
- `extract_struct_from_enum_variant`

**Inline系**（インライン化）:
- `inline_call`（関数呼び出しをインライン化）
- `inline_into_callers`（すべての呼び出し元にインライン化）
- `inline_local_variable` / `inline_macro` / `inline_type_alias`

**Convert系**（変換）:
- `convert_if_let_with_match` / `convert_match_with_if_let`
- `convert_for_loop_to_while_let`
- `convert_named_struct_to_tuple_struct`
- `convert_closure_to_fn`

**Generate系**（生成）:
- `generate_function`（呼び出し元からスタブ関数を生成）
- `generate_impl` / `generate_trait_impl`
- `generate_getter` / `generate_setter`
- `generate_default_from_new`

### goplsのリファクタリング機能

goplsは実用的なリファクタリングセットを提供するが、rust-analyzerより限定的である。

- **Extract**: `extract.function`、`extract.method`、`extract.variable`、`extract.constant`、`extract.toNewFile`
- **Inline**: `inline.call`、`inline.variable`
- **Rewrite**: `fillStruct`、`fillSwitch`、`invertIf`、`removeUnusedParam`、`addTags`（構造体タグ）

### 最も要望されるリファクタリング機能

3つのLanguage Serverに共通して、**Change Signature**（シグネチャ変更）が最も要望されている機能である。goplsのGitHub Issue #38028では「IntelliJにはメソッドの定義/シグネチャを変更する機能があり、パラメータの型を変更したり、すべての呼び出し元にデフォルト値を持つパラメータを追加したりできる」と報告されている。

LSPプロトコルの制限により、任意のリファクタリングダイアログを表示することが困難であり（microsoft/language-server-protocol Issue #1164）、Kotlin K2 LSでは独自拡張を検討する必要がある。

---

## 性能要件：具体的な数値目標

### レスポンス時間の期待値

| 機能 | 目標レイテンシ | 許容上限 | 根拠 |
|------|--------------|---------|------|
| **Code Completion** | <100ms | 500ms | 業界コンセンサス、Google研究 |
| **Go-to-Definition** | <50ms | 100ms | 「即座」と感じる閾値 |
| **Find References** | <200ms (パッケージ内) | 2-5秒 (ワークスペース全体) | LSP 3.17部分結果サポート |
| **Diagnostics** | <500ms（入力停止後） | 1秒 | リアルタイム感覚 |
| **Hover** | <50ms | 100ms | 遅延を感じさせない |

rust-analyzerの目標は「マルチコアマシンで大規模プロジェクトでも100ms以下のautocomplete」であり、Salsa 3.0への移行でこれを実現する計画がある。

### メモリ使用量の許容範囲

| プロジェクト規模 | gopls (v0.12) | rust-analyzer | jdtls |
|----------------|---------------|---------------|-------|
| 小規模 (<10K LOC) | 500MB-1GB | 1GB+ | 1GB |
| 中規模 (10K-50K LOC) | 1-2GB | 2-3GB | 1-2GB |
| 大規模 (50K-100K LOC) | 2-4GB | 3-5GB | 2-4GB (要設定) |
| 超大規模 (100K+ LOC) | 3-5GB | 4-6GB+ | 4GB+ (OOMリスク) |

**重要な知見**: gopls v0.12は「メモリ使用量が開いているパッケージ＋直接依存に比例するO(open packages + direct imports)」アーキテクチャを採用し、**75%のメモリ削減**を達成した。Kubernetesリポジトリでは約90%の削減を実現している。

### 大規模プロジェクトでの性能問題

rust-analyzerのGitHub Issue #17491（82以上の👍）では、専任チームがフルタイムで性能改善に取り組んでいる。主な問題点：

- **Rownan構文木ライブラリが遅い**: ツリー構築が代替実装の2倍遅い
- **マクロ展開が二次時間**: tt-muncherで顕著
- **プロジェクト読み込みが遅い**: 「rust-analyzerは100クレートごとに5秒rustcを待つ」ため、1000以上の依存関係では1分近く待機
- **VFSがファイルを直列読み込み**: 起動時にディスクI/Oを飽和させていない

jdtlsでは「3000ファイル以上のプロジェクトでデフォルト設定だとOOMでクラッシュ」（GitHub Issue #1469）という報告があり、`-Xmx4G`以上の設定が推奨される。

### インクリメンタル更新の重要性

**インクリメンタル処理なしでは、すべてのキーストロークで完全な再解析がトリガーされ、非自明なプロジェクトでIDEが使用不能になる。**

| Language Server | 戦略 | 効果 |
|-----------------|------|------|
| **gopls** | 分離コンパイル + ファイルキャッシュ | 再起動後も高速、メモリ効率的 |
| **rust-analyzer** | Salsaクエリシステム | 関数本体内の変更がグローバルデータを無効化しない |
| **jdtls** | Eclipse JDTの自動ビルド | 初期化中に機能がブロックされる問題あり |

goplsの「Pruned Invalidation」は特に重要で、シンボル参照グラフを追跡し、影響を受けないパッケージを再コンパイルから除外する。ほとんどの変更はインポートサマリーに影響しないため、カスケードを回避できる。

---

## 高度な機能と要望

### インレイヒント（Inlay Hints）

rust-analyzerがLSPでこの機能を先駆けて実装し、現在は業界標準となっている。

**rust-analyzerのインレイヒント**:
- ローカル変数の型
- 関数引数名
- const genericパラメータ名
- チェーン式の型
- クロージャの戻り値型（オプション）
- 省略されたライフタイム（オプション）

**スマートな省略ヒューリスティクス**:
- パラメータ名が関数名の接尾辞の場合は省略
- 引数がパラメータ名と完全一致する場合は省略
- 単項関数の単一文字パラメータは省略

goplsでは`parameterNames`、`assignVariableTypes`、`constantValues`、`rangeVariableTypes`などが利用可能で、デフォルトでは無効だが広く使用されている。

### セマンティックハイライト

rust-analyzerは**30以上のトークンタイプ**と**20以上のモディファイア**を提供している。

**トークンタイプ例**:
- Items: `attribute`, `enum`, `function`, `derive`, `macro`, `method`, `struct`, `trait`
- Literals: `boolean`, `character`, `number`, `string`, `escapeSequence`
- Operators: `arithmetic`, `bitwise`, `comparison`, `logical`

**モディファイア例**:
- `async`, `callable`, `constant`, `consuming`, `controlFlow`
- `declaration`, `defaultLibrary`, `mutable`, `public`, `static`, `unsafe`

### コールヒエラルキーとタイプヒエラルキー

3つのLanguage Serverすべてがこれらの機能をサポートしているが、制限がある。

goplsの制限:
- 動的呼び出し（インターフェースメソッド、関数値）は含まれない
- ネストされた関数は囲む関数と区別されない
- ラムダで停止する（Issue #64451）

### テスト・デバッグ統合

jdtlsはvscode-java-testとjava-debug拡張機能を通じて最も充実したテスト/デバッグ統合を提供している。これらは`initializationOptions.bundles`経由でロードされ、Debug Adapter Protocol (DAP)を使用する。

goplsは`source.addTest`コードアクションでテーブル駆動テストを生成でき、パラメータ、レシーバー、コンテキスト、エラーを処理する。

---

## 開発者フィードバック：不満点と要望

### 共通の不満点

| 問題 | gopls | rust-analyzer | jdtls |
|------|-------|---------------|-------|
| 大規模プロジェクトで遅い | ✓ | ✓✓ | ✓✓ |
| 起動/インデックス時間が長い | ✓ | ✓✓ | ✓✓ |
| リファクタリング機能が限定的 | ✓✓ | ✓ | ✓ |
| 補完精度の問題 | ✓ | ✓ | ✓✓ |
| メモリ使用量 | - | ✓✓ | ✓✓ |
| リファクタリング時のコメント消失 | ✓✓ | ✓ | ✓ |

### 最も要望される未実装機能

1. **Change Signature** - すべてのLanguage Serverで要望
2. **Safe Delete** - 使用箇所がないことを確認してから削除
3. **Move Method/Class** - ファイル/モジュール間でのコード移動
4. **Cross-file Inline** - 複数ファイルにまたがるインライン化

### 具体的な開発者の声

**rust-analyzer** (GitHub Issue #17491):
> 「rust-analyzerの現在の性能は、私たちの雇用主のRustプログラマーが直面している最大の問題です」

**jdtls** (GitHub Issue #3202):
> 「大規模プロジェクトでの補完が遅すぎる。クラスインスタンスの最初の補完に20秒かかる。IntelliJでは2秒しかかからない」

**gopls** (JetBrains調査):
> 「goplsは良くなってきたが、GoLandと比較すると、特にリファクタリングの力とスマートな型マッチング補完において、まだ道のりがある」

---

## 比較分析：アーキテクチャと設計哲学

### rust-analyzerのSalsaフレームワーク

rust-analyzerは**Salsaフレームワーク**を使用したオンデマンド計算モデルを採用している。

**コアコンセプト**:
- キー値ストアが派生値を計算可能
- 関数呼び出しの依存関係を自動記録
- 細粒度の無効化
- **Early cutoff**: 入力が変わっても出力が同じなら伝播しない

**Durabilityシステム**（最適化）:
- クエリをdurabilityレベルに分割（volatile, normal, durable）
- 標準ライブラリクエリは「durable」（めったに変わらない）
- ユーザーコードクエリは「volatile」（頻繁に変わる）
- 編集ごとに300ms以上のstdlibクエリをチェックすることを回避

### goplsの分離コンパイルアーキテクチャ

gopls v0.12は大幅なアーキテクチャ刷新を行い、以下を実現した：

- **メモリO(開いているパッケージ + 直接インポート)**: ワークスペース全体ではなく
- **ファイルベースキャッシュ**: パッケージごとのインデックスをディスクに永続化
- **Pruned Rebuilds**: シンボル参照グラフを追跡し、影響を受けないパッケージを除外
- **2回目の起動は大幅に高速**: キャッシュデータを活用

### jdtlsのEclipse JDTコンパイラ

jdtlsはEclipse JDT（Java Development Tools）を活用し、**ECJ（Eclipse Compiler for Java）**という独自のインクリメンタルコンパイラを使用している。

- **エラー耐性**: 未解決エラーがあってもコンパイル・実行可能（javacとは異なる）
- **依存関係追跡**: 依存関係グラフと問題リストを含む内部状態を維持
- **より詳細な診断**: javacよりも多くの警告/エラーを生成可能

### 業界ベストプラクティス

| プラクティス | 実装例 | 効果 |
|-------------|-------|------|
| ファイルベースキャッシュ | gopls | 再起動後の高速起動 |
| オンデマンド計算 | rust-analyzer (Salsa) | 必要なものだけ計算 |
| キャンセル可能な処理 | rust-analyzer | 入力中に進行中の解析をキャンセル |
| 分離プロセス | rust-analyzer (proc-macro) | クラッシュの隔離 |
| ハイブリッド起動 | jdtls | 構文サーバー先行起動でUX改善 |

---

## Kotlin K2 Language Server実装への推奨事項

### 必須実装項目

1. **100ms以下のコード補完レイテンシ** - 業界標準、ユーザー期待値
2. **ファイルベースキャッシュ**（gopls v0.12方式）- インデックスコストの償却
3. **細粒度のインクリメンタル分析** - 大規模プロジェクトに必須
4. **メモリ予算**: 基準2GB、大規模プロジェクトで4-6GBにスケール
5. **初期化中に機能をブロックしない** - 部分的結果のストリーミング

### リファクタリング優先順位

1. **Change Signature** - 最も要望されている
2. **Extract Method/Function** - 頻繁に使用
3. **Extract Variable/Constant** - 基本的だが重要
4. **Inline Method/Variable** - 補完的操作
5. **Rename** - 破壊的変更のため高品質必須
6. **Move to New File** - Kotlin特有の需要あり
7. **Safe Delete** - 使用確認付き削除

### 品質保証項目

- **リファクタリング時にコメントを失わない** - goplsの主要な痛点
- **適用前にコンパイル破壊を検証** - 安全性確保
- **エラー耐性パーサー** - 部分的に入力されたコードでも動作

### Kotlin特有の考慮事項

- **postfix completions**: `.let`、`.also`、`.apply`、`.run`、`.if`などKotlinイディオム
- **スマート補完**: 期待される型を理解
- **null安全性ヒント**: `?.`、`!!`、`?:`の適切な提案
- **coroutine対応**: `suspend`関数の特別な処理
- **Javaとの相互運用**: Java補完とKotlin補完の統合

### ベンチマーク目標

| 指標 | 目標値 | 競合比較 |
|------|-------|---------|
| 補完レイテンシ | <100ms | IntelliJ同等 |
| メモリ（中規模） | <2GB | GoLand同等 |
| 初期インデックス（50K LOC） | <2分 | rust-analyzer同等 |
| インクリメンタル更新 | <100ms | 業界標準 |

継続的なテレメトリ監視により、50msの回帰を数時間以内に検出できる体制を構築すべきである。

