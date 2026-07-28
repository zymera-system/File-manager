# Comparativo Arquitetural — 3 Projetos

## Projetos Analisados

| Projeto | Tipo | Package | Tamanho | Linguagem |
|---------|------|---------|---------|-----------|
| **FileManager (App)** | WebView App | `com.filemanager.app` | ~3MB | HTML/CSS/JS + Java Bridge |
| **File Manager+ (Base 1)** | Native App | `com.alphainventor.filemanager` | ~24MB | Java (obfuscated) |
| **ZArchiver Pro (Base 2)** | Native App | `ru.zdevs.zarchiver.pro` | ~4.5MB | Java + JNI/C |

---

## 1. ARQUITETURA GERAL

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        FileManager (APP)                                │
├─────────────────────────────────────────────────────────────────────────┤
│  Camada 1: WebView (HTML/CSS/JS)                                       │
│  Camada 2: @JavascriptInterface (FileBridge - 14 métodos)              │
│  Camada 3: Java API direta (File, FileObserver, etc)                   │
│  Persistência: localStorage (JS)                                       │
│  Serviços: Nenhum                                                      │
│  Progresso: Nenhum (operações síncronas)                               │
│  Múltiplas tarefas: Não                                               │
├─────────────────────────────────────────────────────────────────────────┤
│                     File Manager+ (BASE 1)                              │
├─────────────────────────────────────────────────────────────────────────┤
│  Camada 1: XML Layouts + Fragments + Adapters                          │
│  Camada 2: Activities (23) + Fragments (15+)                           │
│  Camada 3: CommandService (AIDL via Intent)                            │
│  Camada 4: file/o.smali (FileOperator - 17.742 linhas)                │
│  Camada 5: file/w.smali (AsyncTask - 18.443 linhas)                   │
│  Camada 6: 3 fallbacks (Local IO / SAF / Root+Shizuku)                │
│  Persistência: SQLite (BookmarkProvider) + SharedPreferences           │
│  Serviços: 5 (Command, FTP, HTTP, FileObserver, Scan)                 │
│  Progresso: Real via Handler + Notification                            │
│  Múltiplas tarefas: Sim (CopyOnWriteArrayList)                        │
├─────────────────────────────────────────────────────────────────────────┤
│                     ZArchiver Pro (BASE 2)                             │
├─────────────────────────────────────────────────────────────────────────┤
│  Camada 1: XML Layouts + ViewGroups customizados                      │
│  Camada 2: ZArchiver (Activity principal - 15.364 linhas)             │
│  Camada 3: ZArchiverService (AIDL Binder - 27 métodos)                │
│  Camada 4: C2JBridge (JNI → libp7zip.so)                              │
│  Camada 5: 9 bibliotecas nativas C                                    │
│  Persistência: SharedPreferences + SQLite (interno)                   │
│  Serviços: 2 (ZArchiverService, ClearTemp)                            │
│  Processos: 4 isolados (main, :service, :archive, :plugin)            │
│  Progresso: Real via Binder + Notification                             │
│  Múltiplas tarefas: Sim (5 slots simultâneos)                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. SISTEMA DE PERMISSÕES

| Permissão | App | FM+ | ZArchiver |
|-----------|-----|-----|-----------|
| READ_EXTERNAL_STORAGE | ✅ | ✅ (maxSdk 32) | ✅ |
| WRITE_EXTERNAL_STORAGE | ✅ | ✅ (maxSdk 32) | ✅ (maxSdk 29) |
| MANAGE_EXTERNAL_STORAGE | ❌ | ✅ (SDK 30+) | ✅ (SDK 30+) |
| POST_NOTIFICATIONS | ❌ | ✅ (SDK 33+) | ✅ (SDK 33+) |
| FOREGROUND_SERVICE | ❌ | ✅ | ✅ |
| REQUEST_INSTALL_PACKAGES | ❌ | ✅ | ✅ |
| ACCESS_SUPERUSER (ROOT) | ❌ | ✅ (Shizuku) | ✅ (Magisk) |
| WAKE_LOCK | ✅ | ✅ | ✅ |
| INTERNET | ✅ | ✅ | ❌ |
| PACKAGE_USAGE_STATS | ❌ | ✅ | ❌ |

### Estratégia de permissões:

**App:** Básica — solicita READ/WRITE na inicialização
**FM+:** Avançada — 3 camadas (Legacy ≤10, Moderno 11+, Granular 13+)
**ZArchiver:** Intermediária — Legacy + MANAGE_EXTERNAL_STORAGE

---

## 3. OPERAÇÕES DE ARQUIVO

| Operação | App | FM+ | ZArchiver |
|----------|-----|-----|-----------|
| Listar arquivos | ✅ | ✅ | ✅ |
| Criar pasta | ✅ | ✅ | ✅ |
| Criar arquivo | ⚠️ | ✅ | ✅ |
| Excluir | ✅ | ✅ | ✅ |
| Renomear | ✅ | ✅ | ✅ |
| Copiar | ⚠️ | ✅ | ✅ |
| Mover | ⚠️ | ✅ | ✅ |
| Pesquisa | ⚠️ | ✅ | ✅ |
| Propriedades | ⚠️ | ✅ | ✅ |
| Abrir arquivo | ✅ | ✅ | ✅ |
| Compartilhar | ✅ | ✅ | ✅ |
| Comprimir | ❌ | ✅ | ✅ |
| Extrair | ❌ | ✅ | ✅ |
| Batch rename | ❌ | ✅ | ❌ |
| Seleção múltipla | ⚠️ | ✅ | ✅ |
| Clipboard (copy/cut/paste) | ⚠️ | ✅ | ✅ |
| Cancelamento | ❌ | ✅ | ✅ |
| Progresso | ❌ | ✅ | ✅ |
| Favoritos | ⚠️ (localStorage) | ✅ (SQLite) | ✅ |
| Recentes | ❌ | ✅ (SQLite) | ❌ |
| Lixeira | ⚠️ (localStorage) | ❌ | ❌ |

---

## 4. FUNCIONALIDADES EXCLUSIVAS

### Apenas no App (nosso design):
- Interface WebView customizada
- SVG inline para ícones
- Animações CSS
- Layout flexível via HTML/CSS
- Tema dark/light via CSS

### Apenas no File Manager+:
- **Cloud Storage:** Box, OneDrive, Dropbox
- **Servidor FTP** com autenticação
- **Servidor HTTP** com streaming de mídia
- **FileObserver** (monitoramento em tempo real)
- **Players de mídia:** Vídeo (Media3/ExoPlayer), Imagem (PhotoView)
- **Bookmarks SQLite** com sincronização cloud
- **Lixeira** (parcial)
- **Shizuku** (acesso elevado sem root)
- **Multi-cloud** com autenticação OAuth2
- **FileProvider** para content:// URIs
- **App manager** real (QUERY_ALL_PACKAGES)
- **Split APK installer**
- **Suporte Android TV** (Leanback)

### Apenas no ZArchiver:
- **Compressão nativa** via JNI/C (7z, ZIP, RAR, etc.)
- **41+ formatos** de compactação
- **4 processos isolados** (main, service, archive, plugin)
- **AIDL Binder** robusto (27 métodos)
- **5 slots de tarefa** simultâneos
- **FileProvider triplo** (normal, archive, plugin)
- **Sistema de plugins** extensível
- **FloatingActionMenu** customizado
- **Multi-panel** (modo lista/grid)
- **ROOT direto** via Magisk
- **Divisão de volumes** (split archives)
- **Criptografia AES-256** em archives

---

## 5. COMUNICAÇÃO ENTRE CAMADAS

| Aspecto | App | FM+ | ZArchiver |
|---------|-----|-----|-----------|
| UI ↔ Lógica | @JavascriptInterface | Intent + Broadcast | **AIDL Binder** |
| Operações longas | Síncrono (bloqueia UI) | AsyncTask + Service | Service + 5 slots |
| Progresso | ❌ | ✅ Handler | ✅ Handler + Notification |
| Cancelamento | ❌ | ✅ por ID | ✅ por slot |
| Background | ❌ | ✅ Foreground Service | ✅ Foreground Service |
| Multi-tarefa | ❌ | ✅ | ✅ (5 simultâneas) |
| Persistência | localStorage | SQLite + SharedPrefs | SharedPrefs + SQLite |
| Notificações | ❌ | ✅ | ✅ |

---

## 6. COMPARATIVO DE COMPLEXIDADE

```
                    App          FM+           ZArchiver
                    ───          ───           ─────────
Classes Java:       4            ~500+         ~100+
Classes JS:         25           N/A           N/A
Linhas de código:   ~5K          ~100K+        ~50K+
Bibliotecas nativas:0           0             9 (.so)
Processos:          1            1             4
Serviços:           0            5             2
Providers:          0            4             3
Permissões:         5            22            8
Activities:         1            23            7
Layouts XML:        0 (HTML)     ~40           ~30
Menus:              0 (JS)       ~38           ~10
Formats suportados: N/A          ~10           41+
```

---

## 7. GAPS CRÍTICOS DO APP (vs bases)

### 🔴 Funcionalidades AUSENTES (impacto alto):
1. **Permissões modernas** — Sem MANAGE_EXTERNAL_STORAGE
2. **Serviço de operações** — Operações bloqueiam a UI
3. **Progresso de operações** — Usuário não vê progresso
4. **Cancelamento** — Impossível cancelar operação em andamento
5. **Multi-tarefa** — Não pode fazer 2 operações ao mesmo tempo
6. **SQLite nativo** — Favoritos e lixeira em localStorage (frágil)
7. **FileObserver** — Não monitora mudanças no filesystem
8. **Propriedades detalhadas** — Só mostra tamanho

### 🟡 Funcionalidades PARCIAIS (impacto médio):
1. **Seleção múltipla** — Existe mas sem ações completas
2. **Clipboard** — Parcialmente implementado
3. **Pesquisa** — Básica, sem filtros avançados
4. **Apps instalados** — Mock, não usa PackageManager

### 🟢 Funcionalidades que FUNCIONAM bem:
1. Listar arquivos
2. Criar pasta
3. Excluir (básico)
4. Renomear
5. Abrir arquivo
6. Compartilhar
7. Storage info
8. Navegação por breadcrumb
9. Home screen com categorias

---

## 8. PADRÕES ARQUITETURAIS PARA ADOTAR

### Do File Manager+:
- **3 camadas de permissões** (Legacy/Modern/Granular)
- **Bookmarks via SQLite** (ContentProvider)
- **FileObserver** para monitoramento
- **Handler para progresso** de operações
- **Foreground Service** para operações longas

### Do ZArchiver:
- **AIDL Binder** para comunicação robusta
- **Multi-processo** para isolamento
- **Sistema de slots** para multi-tarefa
- **Cancelamento por ID** de operação
- **FileProvider** para compartilhamento

### Para o nosso app:
- Manter WebView como UI
- Expandir FileBridge com mais métodos nativos
- Adicionar Service para operações longas
- Implementar SQLite para dados persistentes
- Adicionar progresso via callback JS↔Java
- Implementar cancelamento de operações
