# Kotlin Language Server

A high-performance Language Server implementation leveraging Kotlin 2.0 K2 Analysis API (Base Project)

## 🎯 Project Goals

Build a practical Kotlin Language Server with essential features that developers need:

### Core Features (Skeleton Implemented)

| Feature | Priority | Target Latency | Status |
|---------|----------|----------------|--------|
| **Code Completion** | Critical | < 100ms | ✅ Skeleton Implemented |
| **Go-to-Definition** | High | < 50ms | ✅ Skeleton Implemented |
| **Find References** | High | < 200ms | ✅ Skeleton Implemented |
| **Hover** | High | < 50ms | ✅ Skeleton Implemented |
| **Signature Help** | Medium | < 100ms | ✅ Skeleton Implemented |
| **Document Symbol** | Medium | < 300ms | ✅ Skeleton Implemented |
| **Workspace Symbol** | Low | < 1s | ✅ Skeleton Implemented |
| **Diagnostics** | High | < 500ms | ✅ Skeleton Implemented |
| **Formatting** | Medium | < 1s | ✅ Skeleton Implemented |
| **Code Action** | Medium | < 300ms | ✅ Skeleton Implemented |
| **Rename** | High | < 500ms | ✅ Skeleton Implemented |

### Advanced Features (Planned for Future)

- Semantic Tokens (semantic highlighting)
- Inlay Hints (type hints, parameter names)
- Call Hierarchy
- Type Hierarchy
- Document Links

## 🏗️ Project Structure

```
kotlin-language-server/
├── src/
│   ├── main/kotlin/com/kotlinls/
│   │   ├── server/              # Server core
│   │   │   ├── Main.kt          # Entry point
│   │   │   └── KotlinLanguageServer.kt
│   │   ├── lsp/                 # LSP feature implementation
│   │   │   ├── KotlinTextDocumentService.kt
│   │   │   └── KotlinWorkspaceService.kt
│   │   ├── analysis/            # K2 Analysis API integration
│   │   │   └── K2AnalysisProvider.kt
│   │   ├── persistence/         # Database layer
│   │   │   └── DatabaseSchema.kt
│   │   └── utils/               # Utilities
│   │       └── LspUtils.kt
│   └── test/kotlin/com/kotlinls/
│       ├── server/
│       ├── lsp/
│       ├── analysis/
│       └── utils/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 🚀 Quick Start

### Prerequisites

- JDK 17 or higher
- Gradle 8.5 or higher (Gradle Wrapper included)

### Build

```bash
# Build the project
./gradlew assemble

# Run tests
./gradlew test

# Build and test together
./gradlew build
```

### Run

```bash
# Run in development mode
./gradlew run

# Generate Fat JAR
./gradlew shadowJar

# Run from JAR
java -Xmx2G -jar build/libs/kotlin-language-server-0.1.0-SNAPSHOT.jar
```

## 🧪 Testing

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests KotlinLanguageServerTest

# View test report
open build/reports/tests/test/index.html
```

## 📦 Technology Stack

- **Language**: Kotlin 2.0.21
- **Build Tool**: Gradle 8.5 (Kotlin DSL)
- **JDK**: 17
- **LSP Framework**: Eclipse LSP4J 0.21.2
- **Compiler API**: Kotlin K2 Analysis API
- **Database**: SQLite 3.45 (WAL mode)
- **Logging**: Logback + kotlin-logging
- **Testing**: JUnit 5 + MockK

## 🔧 Development

### Development with IntelliJ IDEA

1. Open the project: `File > Open` → `kotlin-language-server`
2. It will be recognized as a Gradle project
3. Run/Debug `Main.kt`

### Adding Features

Build on the skeleton implementation to add real functionality:

1. **K2 Analysis API Integration** (`K2AnalysisProvider.kt`)
   - Build standalone session
   - Implement symbol resolution and type inference

2. **SQLite Persistence** (`DatabaseSchema.kt`)
   - Save symbol index
   - Manage reference information

3. **Completion Feature** (`KotlinTextDocumentService.kt`)
   - Get symbols in scope
   - Implement smart completion

## 📋 Implementation Roadmap

### Phase 1: Foundation ✅ Complete

- [x] LSP4J server skeleton
- [x] Skeleton implementation of core features
- [x] Test framework setup
- [x] Build and test environment

### Phase 2: Core Feature Implementation (Next Steps)

- [ ] K2 Analysis API integration
  - [ ] Standalone session construction
  - [ ] Basic symbol resolution
  - [ ] Type inference implementation
- [ ] Completion feature implementation
  - [ ] Get symbols in scope
  - [ ] Trigger character support
- [ ] Go-to-definition implementation
- [ ] Hover information implementation

### Phase 3: Optimization

- [ ] SQLite persistence
- [ ] Incremental updates
- [ ] Cache layer implementation
- [ ] Performance testing

## 📊 Performance Goals

| Metric | Target | Current |
|--------|--------|---------|
| Completion latency | < 100ms | - |
| Go-to-definition | < 300ms | - |
| Initial indexing (10k LOC) | < 10s | - |
| Memory usage (10k LOC) | < 500MB | - |
| Test execution time | < 10s | ✅ < 5s |

## 🧩 Dependencies

Key dependencies:

```kotlin
// Kotlin & Compiler
kotlin-stdlib: 2.0.21
kotlin-compiler-embeddable: 2.0.21
analysis-api-standalone: 2.0.21

// LSP
lsp4j: 0.21.2

// Database
sqlite-jdbc: 3.45.0.0

// Logging
logback-classic: 1.4.14
kotlin-logging-jvm: 3.0.5

// Testing
junit-jupiter: 5.10.1
mockk: 1.13.8
```

## 🤝 Contributing

Contributions are welcome! Please see **[CONTRIBUTING.md](./CONTRIBUTING.md)** for details.

This is a base project. We are looking for contributors to help with Phase 2 and beyond implementation.

### Contribution Guidelines

- **Issue-Driven Development**: Start with a GitHub Issue
- **Test-Driven Development (TDD)**: Test-first approach
- **Conventional Commits**: Standard commit message format
- **English for issues and PRs**: Issues, PRs, and commit messages should be written in English

## 📄 License

MIT License

## 📚 Resources

- [Kotlin Analysis API Documentation](https://kotlin.github.io/analysis-api/)
- [Eclipse LSP4J](https://github.com/eclipse/lsp4j)
- [Language Server Protocol Specification](https://microsoft.github.io/language-server-protocol/)
- [SQLite Documentation](https://www.sqlite.org/docs.html)

---

**Current Status**: ✅ Base project with successful builds and tests

Next Steps: Phase 2 - K2 Analysis API integration and core feature implementation
