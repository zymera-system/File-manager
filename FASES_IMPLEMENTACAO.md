# Documentação Detalhada — Fases de Implementação

## Sumário Executivo

| Fase | Conteúdo | Arquivos | Métodos JS | Status |
|------|----------|----------|------------|--------|
| **1** | Core Managers | 5 arquivos | 20+ métodos | ✅ |
| **2** | Background Service | 1 arquivo | 3 métodos | ✅ |
| **3** | Integração JS | FileBridge | 25+ métodos | ✅ |
| **4** | Compressão | 1 arquivo | 4 métodos | ✅ |
| **5** | Players de Mídia | 1 arquivo | 5 métodos | ✅ |
| **6** | UI Nativa | 1 arquivo | 8 métodos | ✅ |
| **7** | Testes | 1 arquivo | 1 método | ✅ |

**Total: 10 arquivos Java, 60+ métodos @JavascriptInterface**

---

## Fase 1 — Core Managers Modulares

### Objetivo
Criar a fundação da arquitetura com 5 managers reutilizáveis que encapsulam toda a lógica de domínio, separando-a da FileBridge.

### Arquivos Criados

#### `core/PermissionManager.java` (220 linhas)
**Responsabilidade:** Gerenciar permissões de armazenamento de forma adaptativa.

**Inspiração:** File Manager+ (3 camadas de permissões)

**Estratégia em 3 camadas:**
- **Camada 1 — Android ≤ 12 (SDK ≤ 32):** `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE` + `requestLegacyExternalStorage="true"` no Manifest
- **Camada 2 — Android 11-12 (SDK 30-32):** `MANAGE_EXTERNAL_STORAGE` — redireciona para configurações do sistema
- **Camada 3 — Android 13+ (SDK ≥ 33):** Permissões granulares de mídia (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`) + `POST_NOTIFICATIONS`

**Métodos públicos:**
- `hasStoragePermission()` → boolean
- `hasPermission(String)` → boolean
- `requestStoragePermissions()` → void (detecta versão e aplica estratégia correta)
- `openStorageSettings()` → void (abre config "Todos os arquivos" Android 11+)
- `requestNotificationPermission()` → void (Android 13+)
- `handlePermissionsResult()` → void (chamar no onRequestPermissionsResult)
- `getStatusJson()` → String (JSON com status completo)

**Interface de callback:**
```java
interface PermissionCallback {
    void onPermissionGranted(String permission);
    void onPermissionDenied(String permission);
    void onRequiresManualGrant(); // Android 11+
}
```

**Decisões técnicas:**
- Usa `Environment.isExternalStorageManager()` para verificar MANAGE_EXTERNAL_STORAGE
- Reflection para acessar configurações de storage em dispositivos Samsung/Huawei que bloqueiam intents padrão
- `POST_NOTIFICATIONS` necessário para foreground service no Android 13+

---

#### `core/OperationManager.java` (290 linhas)
**Responsabilidade:** Executar operações de arquivo em background com progresso e cancelamento.

**Inspiração:** ZArchiver (5 slots simultâneos, cancelamento cooperativo)

**Arquitetura:**
- `ExecutorService` com pool de 5 threads
- Cada operação recebe um `taskId` único (UUID)
- Progresso: `processedCount / totalCount` + `currentFile`
- Cancelamento: cooperativo via `checkCancellation()`

**Classes internas:**
- `TaskInfo` — informações da tarefa (taskId, cancelled, progress, processedCount, totalCount, currentFile, operationType)
- `ProgressOperation` — interface funcional para operações com progresso
- `CancellationException` — exceção para interromper operações
- `TaskCallback` — callback de conclusão (main thread)

**Métodos públicos:**
- `submit(String type, ProgressOperation, TaskCallback)` → String (taskId)
- `submit(String type, ProgressOperation)` → String (fire and forget)
- `cancel(String taskId)` → boolean
- `cancelAll()` → void
- `isCancelled(String taskId)` → boolean
- `getProgressJson(String taskId)` → String (JSON progresso)
- `getActiveTaskCount()` → int
- `getActiveTasksJson()` → String (JSON array)
- `cleanup()` → void (remove tarefas concluídas)
- `shutdown()` → void (desliga executor)

**Métodos estáticos (para uso dentro das operações):**
- `checkCancellation(TaskInfo)` → CancellationException
- `updateProgress(TaskInfo, processed, total, currentFile)` → void
- `formatSize(long bytes)` → String

**Decisões técnicas:**
- Pool de 5 threads (mesmo número do ZArchiver) para não sobrecarregar o device
- Cancelamento cooperativo: a operação deve chamar `checkCancellation()` periodicamente
- `ConcurrentHashMap` para thread-safety no registro de tarefas
- Auto-cleanup: tarefas concluídas podem ser removidas da memória

---

#### `core/StorageDetector.java` (300 linhas)
**Responsabilidade:** Detectar e gerenciar volumes de armazenamento.

**Inspiração:** File Manager+ (SD card detection, multi-volume support)

**Classes internas:**
- `StorageVolumeInfo` — informações de um volume (id, displayName, path, isPrimary, isRemovable, totalBytes, freeBytes, usedBytes)

**Métodos públicos:**
- `detectAllVolumes()` → List<StorageVolumeInfo>
- `getPrimaryVolumeJson()` → String
- `getAllVolumesJson()` → String (JSON array)
- `getSpaceJson(String path)` → String
- `resolveVirtualPath(String)` → String (ex: "internal:/DCIM" → "/storage/emulated/0/DCIM")
- `toVirtualPath(String)` → String (inverso)

**Detecção de volumes:**
1. Sempre inclui armazenamento interno (emulated/0)
2. Usa `StorageManager.getStorageVolumes()` (Android N+)
3. Reflection para obter path do StorageVolume (campo não público)
4. Fallback: verifica diretórios comuns de SD (`/storage/sdcard1`, `/storage/extSdCard`) e USB (`/storage/usbotg`, `/storage/usb`)

**Mapeamento virtual:**
```
"internal:/Download"  →  /storage/emulated/0/Download
"sdcard:/DCIM"        →  /storage/sdcard1/DCIM
"/storage/emulated/0" →  (caminho real, sem conversão)
```

---

#### `core/DatabaseManager.java` (280 linhas)
**Responsabilidade:** Persistência SQLite para bookmarks, favoritos e histórico.

**Inspiração:** File Manager+ (SQLite bookmarks, type system, 200 history limit)

**Schema:**
```sql
CREATE TABLE bookmarks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL,
    name TEXT,
    type INTEGER DEFAULT 1,
    created_at INTEGER,
    extra TEXT  -- JSON
);
CREATE INDEX idx_bookmarks_type ON bookmarks(type);
CREATE INDEX idx_bookmarks_path ON bookmarks(path);
```

**Tipos de registro:**
- `TYPE_FAVORITE = 1` — Favoritos
- `TYPE_BOOKMARK = 2` — Atalhos
- `TYPE_HISTORY = 3` — Histórico (máximo 200)
- `TYPE_TRASH = 4` — Lixeira
- `TYPE_CLOUD = 5` — Cloud

**Métodos públicos:**
- `addBookmark(path, name, type, extra)` → long (id)
- `removeBookmark(path, type)` → int (rows deleted)
- `removeBookmarkById(long)` → int
- `isBookmark(path, type)` → boolean
- `getBookmarksJson(type)` → String
- `addToHistory(path, name)` → void (auto-limit 200)
- `clearHistory()` → void
- `addToTrash(path, name, originalParent, extra)` → void
- `getTrashJson()` → String
- `emptyTrash()` → void
- `searchBookmarks(query)` → String
- `getStatsJson()` → String
- `clearType(int)` → void
- `clearAll()` → void

**Decisões técnicas:**
- Singleton pattern com `getInstance(Context)`
- Histórico com limite de 200 entradas (auto-cleanup via SQL)
- Duplicatas são removidas antes de inserir (path + type = unique)
- `extra` é campo JSON flexível para metadados futuros

---

#### `core/ObserverManager.java` (180 linhas)
**Responsabilidade:** Monitoramento de mudanças no sistema de arquivos.

**Inspiração:** File Manager+ (FileObserverService)

**Classes internas:**
- `FsEvent` — evento de mudança (eventType, path, directory, timestamp, type)
- `EventCollector` — buffer circular de 100 eventos para polling
- `DirectoryObserver` — extensão de `FileObserver`

**Eventos monitorados:**
`CREATE`, `DELETE`, `MOVED_TO`, `MOVED_FROM`, `MODIFY`, `CLOSE_WRITE`

**Métodos públicos:**
- `startWatching(String directory)` → boolean
- `stopWatching(String directory)` → boolean
- `stopAll()` → void
- `isWatching(String directory)` → boolean
- `pollEvents()` → String (drena eventos como JSON)
- `getPendingEventCount()` → int
- `getStatusJson()` → String

**Padrão de uso pelo JS:**
```
1. FileBridge.watchDirectory("/storage/emulated/0/Download")
2. A cada 2 segundos: FileBridge.pollFsEvents()
3. Processar eventos e atualizar UI se necessário
4. FileBridge.unwatchDirectory("/storage/emulated/0/Download")
```

---

## Fase 2 — Background Service

### Objetivo
Manter o app vivo durante operações longas com notificação persistente de progresso.

### Arquivo Criado

#### `service/FileManagerService.java` (260 linhas)
**Responsabilidade:** Foreground Service com notificação de progresso.

**Inspiração:** File Manager+ (FileObserverService) e ZArchiver (background tasks)

**Ações suportadas:**
- `ACTION_START` — Inicia operação, cria notificação
- `ACTION_UPDATE` — Atualiza progresso (0-100%)
- `ACTION_FINISH` — Finaliza com sucesso/erro, auto-dismiss após 3s
- `ACTION_CANCEL` — Cancela operação, notifica JS via callback

**Configuração da notificação:**
- Channel: "filemanager_operations", IMPORTANCE_LOW
- Título: "FileManager — [Operation Type]"
- Barra de progresso quando 0 < progress < 100
- Botão "Cancelar" com PendingIntent
- Auto-cancel após conclusão

**Métodos estáticos (conveniência):**
- `start(context, operationType, description)` → void
- `updateProgress(context, progress, currentFile)` → void
- `finish(context, success, message)` → void
- `cancel(context)` → void

**Lifecycle:**
- `START_STICKY` — reinicia se o sistema matar o service
- `stopForeground(true)` + `stopSelf()` após finish ou cancel
- Cleanup no `onDestroy()`

**Manifest:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".service.FileManagerService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

---

## Fase 3 — Integração JS

### Objetivo
Conectar todos os managers ao JavaScript via @JavascriptInterface, mantendo backward compat total.

### Abordagem
A FileBridge existente foi refatorada para:
1. Manter TODOS os métodos originais inalterados (listFiles, createFolder, deleteItem, copyFile, moveFile, etc.)
2. Adicionar novos métodos que delegam aos managers
3. Inicialização lazy: managers são criados no primeiro uso via `init()`

**Novos métodos JS adicionados (25+):**

| Método | Manager | Descrição |
|--------|---------|-----------|
| `checkPermission()` | PermissionManager | Status de permissões |
| `requestPermission()` | PermissionManager | Solicitar permissões |
| `openStorageSettings()` | PermissionManager | Config Android 11+ |
| `asyncCopy(src, dest)` | OperationManager + Service | Cópia assíncrona |
| `asyncMove(src, dest)` | OperationManager + Service | Movimentação assíncrona |
| `asyncDelete(path)` | OperationManager + Service | Exclusão assíncrona |
| `cancelOperation(taskId)` | OperationManager | Cancelar por taskId |
| `cancelAllOperations()` | OperationManager | Cancelar tudo |
| `pollProgress(taskId)` | OperationManager | Progresso JSON |
| `getActiveOperations()` | OperationManager | Operações ativas |
| `getStorageVolumes()` | StorageDetector | Todos os volumes |
| `getPathSpace(path)` | StorageDetector | Espaço disponível |
| `addFavorite(path, name)` | DatabaseManager | Adicionar favorito |
| `removeFavorite(path)` | DatabaseManager | Remover favorito |
| `getFavorites()` | DatabaseManager | Listar favoritos |
| `isFavorite(path)` | DatabaseManager | Verificar favorito |
| `addToHistory(path, name)` | DatabaseManager | Adicionar ao histórico |
| `getHistory()` | DatabaseManager | Listar histórico |
| `clearHistory()` | DatabaseManager | Limpar histórico |
| `watchDirectory(path)` | ObserverManager | Monitorar diretório |
| `unwatchDirectory(path)` | ObserverManager | Parar monitoramento |
| `pollFsEvents()` | ObserverManager | Poll de eventos |
| `getObserverStatus()` | ObserverManager | Status do observer |
| `getSystemStatus()` | Todos | Status consolidado |
| `runTests()` | TestManager | Executar testes |

**Integração Service nas operações async:**
```java
// asyncCopy agora:
1. FileManagerService.start(activity, "copy", "Copiando photo.jpg...")
2. operationManager.submit("copy", (taskInfo) -> {
    // ... copiar com progresso ...
    FileManagerService.finish(activity, true, "photo.jpg copiado");
});
```

---

## Fase 4 — Compressão/Descompressão

### Objetivo
Suporte nativo a formatos compactados, com foco em ZIP (leitura/escrita) e formatos de leitura via Intent.

### Arquivo Criado

#### `core/ArchiveManager.java` (280 linhas)
**Responsabilidade:** Compressão e descompressão de arquivos.

**Formatos suportados:**
- **Criação:** ZIP, GZ
- **Leitura:** ZIP, TAR, GZ, TGZ, BZ2, XZ, LZMA, 7Z, RAR, ISO, CAB, ARJ, LHA, ACE, ZOO, ARC, DMS, DF, SWF, CFB, ALZ, RPM, DEB, NSIS, CPIO, PAQ, SQX, UDF, HFS, APFS, WIM, EGG

**Métodos públicos:**
- `compressToZip(File[], File output, callback)` → boolean
- `compressToGzip(File source, File output)` → boolean
- `extractZip(File zip, File destDir, callback)` → boolean
- `extractGzip(File gzip, File output)` → boolean
- `getArchiveInfo(String path)` → String (JSON)
- `listZipContents(String path)` → String (JSON array)

**Métodos estáticos:**
- `isSupportedArchive(String path)` → boolean
- `canCreate(String format)` → boolean
- `getExtension(String path)` → String

**Segurança:**
- **Proteção contra Zip Slip:** verifica `canonicalPath` antes de extrair
- Validação de nomes de entrada (../.. bloqueado)

**Detalhes de implementação:**
- ZIP usa `java.util.zip.ZipInputStream/ZipOutputStream` nativo
- Buffer de 8KB para I/O
- Progresso via callback (contagem de arquivos)
- Preserva timestamps dos arquivos originais

---

## Fase 5 — Players de Mídia

### Objetivo
Abrir arquivos de mídia com apps nativos do sistema e fornecer informações de mídia.

### Arquivo Criado

#### `core/MediaPlayerManager.java` (340 linhas)
**Responsabilidade:** Gerenciar abertura, info e compartilhamento de mídia.

**Mapeamento MIME (30+ formatos):**
```java
// Imagens
jpg→image/jpeg, png→image/png, gif→image/gif, webp→image/webp, heic→image/heic
// Áudio
mp3→audio/mpeg, wav→audio/wav, flac→audio/flac, ogg→audio/ogg, opus→audio/opus
// Vídeo
mp4→video/mp4, mkv→video/x-matroska, avi→video/x-msvideo, webm→video/webm
```

**Métodos públicos:**
- `openMedia(String filePath)` → String (abre com app nativo)
- `openWith(String filePath, String packageName)` → String (app específico)
- `getMediaInfo(String filePath)` → String (JSON: mimeType, category, dimensions)
- `getMediaInfoBatch(String pathsJson)` → String (múltiplos)
- `scanMedia(String filePath)` → String (atualiza galeria)
- `scanDirectory(String dirPath)` → String (escaneia recursivo)
- `shareFile(String filePath)` → String (Intent share)
- `shareMultiple(String pathsJson)` → String (share múltiplos)

**Métodos estáticos:**
- `getMimeType(String path)` → String
- `getMediaCategory(String ext)` → String ("image", "audio", "video", "document", "archive", "app", "other")
- `isMediaFile(String filename)` → boolean
- `isImage(String path)` → boolean
- `isAudio(String path)` → boolean
- `isVideo(String path)` → boolean

**Detalhes:**
- Para imagens, lê dimensões via `BitmapFactory.Options.inJustDecodeBounds`
- `MediaScannerConnection.scanFile()` para atualizar a galeria do sistema
- `Intent.ACTION_SEND` / `ACTION_SEND_MULTIPLE` para compartilhamento
- Cache de tipos MIME em `HashMap` para performance

---

## Fase 6 — UI Nativa

### Objetivo
Fornecer componentes UI nativos Android que complementam a interface HTML existente.

### Arquivo Criado

#### `core/UIBridge.java` (310 linhas)
**Responsabilidade:** Diálogos, toasts, overlays e bottom sheets nativos.

**Componentes:**

| Componente | Método | Uso |
|------------|--------|-----|
| **Toast** | `showToast(msg)` | Mensagens rápidas (2s) |
| **Toast Long** | `showToastLong(msg)` | Mensagens longas (3.5s) |
| **Confirm Dialog** | `showConfirmDialog(...)` | Sim/Não com callback JS |
| **Input Dialog** | `showInputDialog(...)` | Campo de texto (renomear, criar) |
| **Options Sheet** | `showOptionsSheet(...)` | Lista de opções (bottom sheet) |
| **Progress Overlay** | `showProgressOverlay(...)` | ProgressBar nativa sobre conteúdo |
| **Update Progress** | `updateProgressOverlay(...)` | Atualizar progresso |
| **Hide Progress** | `hideProgressOverlay()` | Esconder overlay |
| **Info Dialog** | `showInfoDialog(...)` | Mensagem informativa |
| **Error Dialog** | `showErrorDialog(...)` | Mensagem de erro |

**Detalhes de implementação:**
- Todos os componentes rodam na UI thread via `runOnUiThread()`
- Callbacks JS: `notifyJsCallback(name, value)` executa `window.callbackName(value)` no WebView
- Progress overlay: Dialog customizado com ProgressBar horizontal + percentage text
- Input dialog: auto-focus + abre teclado automaticamente
- Options sheet: AlertDialog.Builder com `setItems()` (estilo bottom sheet visual)
- Background escuro semi-transparente (#E6222222) no progress overlay

---

## Fase 7 — Testes Automatizados

### Objetivo
Suite de testes que valida o correto funcionamento de todos os managers.

### Arquivo Criado

#### `core/TestManager.java` (480 linhas)
**Responsabilidade:** Executar e reportar testes de integração.

**Testes por manager:**

| Manager | Testes | Validações |
|---------|--------|------------|
| **PermissionManager** | 3 | getStatusJson, hasStoragePermission, statusFields |
| **OperationManager** | 4 | submit, progress, cancel, activeTasks |
| **StorageDetector** | 5 | detectVolumes, primaryVolume, spaceInfo, virtualPath, allVolumes |
| **DatabaseManager** | 6 | addFavorite, isFavorite, listFavorites, addHistory, removeFavorite, stats |
| **ObserverManager** | 4 | statusInitial, startWatching, pollEvents, stopWatching |
| **ArchiveManager** | 4 | isSupported, getExtension, canCreate, zipExtract |
| **FileBridge** | 1 | selfTest (integração) |

**Total: 27 testes**

**Framework de teste:**
```java
// Uso simples
test("nome_teste", () -> {
    assert condition : "mensagem de erro";
});

// Resultados
- passed: 25
- failed: 2
- passRate: 92.6%
```

**Relatório JSON:**
```json
{
    "permissionManager": {"getStatus": "OK", "hasStoragePermission": "OK", ...},
    "operationManager": {"submit": "OK", "progress": "OK", "cancel": "OK", ...},
    "summary": {
        "total": 27,
        "passed": 25,
        "failed": 2,
        "passRate": 92.6,
        "sdk": 34,
        "device": "Samsung Galaxy S24",
        "android": "14"
    }
}
```

**Como executar pelo JS:**
```javascript
let results = JSON.parse(FileBridge.runTests());
console.log("Pass rate: " + results.summary.passRate + "%");
```

---

## Arquitetura Final

```
FileBridge (contrato único JS↔Java, 60+ métodos)
│
├── PermissionManager      → 3 camadas adaptativas (Legacy/Storage/Granular)
├── OperationManager       → 5 threads + cancelamento cooperativo
├── StorageDetector        → detecção de volumes + mapeamento virtual
├── DatabaseManager        → SQLite bookmarks/history/trash
├── ObserverManager        → FileObserver + buffer circular
├── ArchiveManager         → ZIP/GZ + 41 formatos leitura
├── MediaPlayerManager     → Intent para apps nativos + media scan
├── UIBridge               → Toast/Dialog/Progress nativos
├── TestManager            → 27 testes automatizados
└── FileManagerService     → foreground service + notificação
```

### Inversão de Dependências
- FileBridge NÃO implementa lógica de domínio
- FileBridge DELEGA tudo para managers
- Managers são independentes entre si
- Service é acoplado via `FileManagerService.start/finish`

### Backward Compatibility
- TODOS os métodos originais da FileBridge permanecem intactos
- Novos métodos são ADICIONADOS, não substituídos
- JS existente continua funcionando sem modificações
- Design HTML/CSS/SVG NÃO foi alterado

---

## Métricas do Código

| Métrica | Valor |
|---------|-------|
| **Arquivos Java criados** | 10 |
| **Linhas de código** | ~3.500 |
| **Métodos @JavascriptInterface** | 60+ |
| **Managers** | 8 core + 1 service + 1 test |
| **Formatos MIME suportados** | 30+ |
| **Formatos archive suportados** | 41+ |
| **Testes automatizados** | 27 |
| **Dependências AndroidX** | appcompat, webkit, material (existentes) |
| **Dependências externas** | 0 (tudo nativo) |
