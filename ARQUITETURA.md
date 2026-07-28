# Arquitetura FileManager — Fundação Reutilizável

## Princípio Central

```
┌─────────────────────────────────────────────────┐
│  INTERFACE (WebView)                            │
│  HTML / CSS / SVG / Animações                   │
│  NUNCA alterada para acomodar funcionalidade     │
└──────────────────────┬──────────────────────────┘
                       │ @JavascriptInterface
                       │ (contrato único e imutável)
┌──────────────────────▼──────────────────────────┐
│  BRIDGE LAYER                                   │
│  FileManagerBridge.kt                           │
│  → Ponto único de entrada JS ↔ Java             │
│  → Validação de parâmetros                      │
│  → Roteamento para módulos                      │
│  → Serialização de respostas (JSON)             │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│  CORE LAYER (reutilizável por todos módulos)    │
│                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐    │
│  │Operation │ │Permission│ │  Database    │    │
│  │Manager   │ │Manager   │ │  Manager     │    │
│  │          │ │          │ │              │    │
│  │• Queue   │ │• Legacy  │ │• SQLite      │    │
│  │• Execute │ │• Modern  │ │• Migrations  │    │
│  │• Progress│ │• Granular│ │• Favorites   │    │
│  │• Cancel  │ │• SAF     │ │• Recent      │    │
│  │• Callback│ │• ROOT    │ │• Trash       │    │
│  └──────────┘ └──────────┘ └──────────────┘    │
│                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐    │
│  │Storage   │ │ File     │ │  Observer    │    │
│  │Manager   │ │ Provider │ │  Manager     │    │
│  │          │ │          │ │              │    │
│  │• Internal│ │• Content │ │• FileObserver│    │
│  │• SD Card │ │  URIs    │ │• MediaStore  │    │
│  │• USB     │ │• Sharing │ │• Callbacks   │    │
│  └──────────┘ └──────────┘ └──────────────┘    │
└─────────────────────────────────────────────────┘
```

---

## Módulos do Core Layer

### 1. OperationManager
**Responsabilidade:** Executar operações de arquivo em background com progresso e cancelamento.

```kotlin
// Cada operação é uma Task reutilizável
sealed class FileTask(
    val id: String,
    val type: TaskType,
    val sources: List<String>,
    val destination: String
) {
    class Copy(...) : FileTask(...)
    class Move(...) : FileTask(...)
    class Delete(...) : FileTask(...)
    class Rename(...) : FileTask(...)
    class Compress(...) : FileTask(...)
    class Extract(...) : FileTask(...)
}

// Executor genérico — reutilizado por TODAS as operações
class OperationManager {
    fun execute(task: FileTask, callback: TaskCallback)
    fun cancel(taskId: String)
    fun getProgress(taskId: String): TaskProgress
}

// Callback unificado — JS recebe sempre o mesmo formato
interface TaskCallback {
    fun onProgress(taskId: String, percent: Int, currentFile: String)
    fun onComplete(taskId: String, result: TaskResult)
    fun onError(taskId: String, error: TaskError)
    fun onCancelled(taskId: String)
}
```

**Por que é reutilizável:** Copiar, mover, excluir, comprimir e extrair usam EXATAMENTE a mesma infraestrutura. A única diferença é a implementação da operação.

---

### 2. PermissionManager
**Responsabilidade:** Gerenciar permissões de forma adaptativa por versão do Android.

```kotlin
class PermissionManager {
    // Verifica e solicita permissões de forma adaptativa
    fun requestStoragePermissions(activity: Activity): PermissionResult

    // Para Android 11+ — abre configurações
    fun requestManageStorage(activity: Activity)

    // Verifica se tem permissão
    fun hasStoragePermission(context: Context): Boolean

    // Para Android 13+ — mídia granular
    fun requestMediaPermissions(activity: Activity): PermissionResult
}
```

**Por que é reutilizável:** Toda funcionalidade que precisa de arquivos passa por aqui.

---

### 3. DatabaseManager
**Responsabilidade:** SQLite para dados persistentes (favoritos, recentes, lixeira).

```kotlin
class DatabaseManager(context: Context) {
    // Favoritos — type = 1
    fun addFavorite(path: String, name: String)
    fun removeFavorite(path: String)
    fun getFavorites(): List<Bookmark>
    fun isFavorite(path: String): Boolean

    // Recentes — type = 2, limite 200
    fun addRecent(path: String, name: String)
    fun getRecents(): List<Bookmark>

    // Lixeira — type = 3
    fun moveToTrash(path: String, name: String, originalParent: String)
    fun restoreFromTrash(trashId: Long)
    fun permanentDelete(trashId: Long)
    fun emptyTrash()
    fun getTrashItems(): List<TrashItem>
    fun cleanExpiredTrash() // Remove itens > 30 dias
}
```

**Por que é reutilizável:** Favoritos, recentes e lixeira são operações CRUD idênticas com tipos diferentes.

---

### 4. StorageManager
**Responsabilidade:** Detectar e gerenciar dispositivos de armazenamento.

```kotlin
class StorageManager {
    fun getInternalStorage(): StorageDevice
    fun getSDCard(): StorageDevice?
    fun getUSBDrives(): List<StorageDevice>
    fun getStorageInfo(path: String): StorageInfo
    fun isSDCardAvailable(): Boolean
    fun isUSBConnected(): Boolean
}
```

---

### 5. ObserverManager
**Responsabilidade:** Monitorar mudanças no filesystem.

```kotlin
class ObserverManager {
    fun startWatching(path: String, callback: FSChangeCallback)
    fun stopWatching(path: String)
}

interface FSChangeCallback {
    fun onFileCreated(path: String)
    fun onFileDeleted(path: String)
    fun onFileModified(path: String)
    fun onFileMoved(from: String, to: String)
}
```

---

## Bridge Layer — Contrato JS ↔ Java

### Princípio: UM único ponto de entrada, formatos padronizados

```kotlin
class FileManagerBridge(private val context: Context) {

    // Operaçoes de arquivo (delega para OperationManager)
    @JavascriptInterface
    fun listFiles(path: String): String  // JSON array

    @JavascriptInterface
    fun copyFile(source: String, dest: String): String  // JSON result

    @JavascriptInterface
    fun moveFile(source: String, dest: String): String

    @JavascriptInterface
    fun deleteFile(path: String): String

    @JavascriptInterface
    fun renameFile(oldPath: String, newPath: String): String

    @JavascriptInterface
    fun createFolder(path: String, name: String): String

    @JavascriptInterface
    fun createFile(path: String, name: String): String

    @JavascriptInterface
    fun searchFiles(path: String, query: String): String

    @JavascriptInterface
    fun getItemProperties(path: String): String  // JSON detalhado

    @JavascriptInterface
    fun openFile(path: String): String

    @JavascriptInterface
    fun shareFile(path: String): String

    // Operações em lote (reutiliza OperationManager)
    @JavascriptInterface
    fun copyMultiple(sources: String, dest: String): String  // JSON array de paths

    @JavascriptInterface
    fun moveMultiple(sources: String, dest: String): String

    @JavascriptInterface
    fun deleteMultiple(sources: String): String

    // Progresso e cancelamento
    @JavascriptInterface
    fun getTaskProgress(taskId: String): String

    @JavascriptInterface
    fun cancelTask(taskId: String): String

    // Storage
    @JavascriptInterface
    fun getStorageInfo(): String

    @JavascriptInterface
    fun getStorageDevices(): String

    // Database (favoritos, recentes, lixeira)
    @JavascriptInterface
    fun addFavorite(path: String): String

    @JavascriptInterface
    fun removeFavorite(path: String): String

    @JavascriptInterface
    fun getFavorites(): String

    @JavascriptInterface
    fun moveToTrash(path: String): String

    @JavascriptInterface
    fun restoreFromTrash(trashId: String): String

    @JavascriptInterface
    fun getTrashItems(): String

    @JavascriptInterface
    fun getRecents(): String

    // Apps
    @JavascriptInterface
    fun getInstalledApps(): String

    // Observers
    @JavascriptInterface
    fun startWatching(path: String): String

    @JavascriptInterface
    fun stopWatching(path: String): String
}
```

### Formato de resposta padrão:

```json
// Sucesso
{"success": true, "data": {...}}

// Erro
{"success": false, "error": "message", "code": "ERROR_CODE"}

// Progresso (callback)
{"type": "progress", "taskId": "abc", "percent": 45, "currentFile": "photo.jpg"}
```

---

## Fluxo de Implementação

```
FASE 1: FUNDAÇÃO (1-2 dias)
├── 1.1 OperationManager (execução async + cancelamento)
├── 1.2 PermissionManager (3 camadas)
├── 1.3 FileManagerBridge (contrato padronizado)
└── 1.4 Testar: copiar, mover, excluir 1 arquivo

FASE 2: PERSISTÊNCIA (1 dia)
├── 2.1 DatabaseManager (SQLite)
├── 2.2 Favoritos (add/remove/list)
├── 2.3 Recentes (add/list, limite 200)
└── 2.4 Testar: favoritos persistem após reinício

FASE 3: LIXEIRA (1 dia)
├── 3.1 moveToTrash (real, não localStorage)
├── 3.2 restoreFromTrash
├── 3.3 emptyTrash
└── 3.4 Testar: excluir → lixeira → restaurar

FASE 4: ARQUIVOS EXPANDIDOS (1-2 dias)
├── 4.1 Propriedades detalhadas (MIME, data, permissões)
├── 4.2 Seleção múltipla completa
├── 4.3 Operações em lote
└── 4.4 Testar: selecionar 5 arquivos → mover

FASE 5: MÓDULOS DA HOME (2-3 dias)
├── 5.1 Storage info real (StorageManager)
├── 5.2 Apps instalados (PackageManager)
├── 5.3 Downloads (MediaStore query)
├── 5.4 Imagens (MediaStore query)
├── 5.5 Áudios (MediaStore query)
├── 5.6 Vídeos (MediaStore query)
└── 5.7 Testar: cada módulo com dados reais

FASE 6: MONITORAMENTO (1 dia)
├── 6.1 FileObserver nativo
├── 6.2 Atualização automática da lista
└── 6.3 Testar: criar arquivo externo → aparece na lista

FASE 7: MÍDIA (1-2 dias)
├── 7.1 Player de vídeo nativo (ExoPlayer)
├── 7.2 Player de áudio nativo
└── 7.3 Testar: tocar vídeo/áudio do file manager
```

---

## Regras de Ouro

1. **UMA funcionalidade por vez.** Implementar → compilar → testar → validar → próximo.

2. **NÃO alterar HTML/CSS.** Toda lógica fica em Java/Kotlin + JS bridge.

3. **Reutilizar OperationManager** para TODA operação de arquivo. Nunca criar soluções paralelas.

4. **Formato JSON padronizado.** Toda resposta segue `{success, data/error}`.

5. **SQLite para dados persistentes.** Nunca usar localStorage para dados que precisam persistir.

6. **Progresso via callback.** JS registra callback, Java chama quando tem atualização.

7. **Cancelamento por taskId.** Toda operação assíncrona pode ser cancelada.

8. **Testar cada módulo isoladamente.** Não acumular funcionalidades sem validação.

9. **Documentar decisões.** Cada escolha arquitetural deve ser justificada.

10. **Planejar para crescer.** Nova funcionalidade deve usar infraestrutura existente.
