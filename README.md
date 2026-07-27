# File Manager Android

Aplicativo Android wrapper para o File Manager Web.

## Estrutura

```
file-manager-android/
│
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/filemanager/app/
│   │   │   │   ├── MainActivity.java      ← Activity principal
│   │   │   │   ├── FileBridge.java        ← Bridge para operações de arquivo
│   │   │   │   └── StorageBridge.java     ← Bridge para detecção SD/USB
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   └── activity_main.xml  ← Layout principal
│   │   │   │   └── values/
│   │   │   │       ├── strings.xml
│   │   │   │       └── styles.xml
│   │   │   │
│   │   │   ├── assets/                    ← Arquivos web do File Manager
│   │   │   │   ├── index.html
│   │   │   │   ├── css/
│   │   │   │   ├── js/
│   │   │   │   └── icons/
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   └── build.gradle
│   │
│   └── build.gradle
│
├── gradle/
├── build.gradle
├── settings.gradle
└── README.md
```

## Funcionalidades

### 1. FileBridge (Operações de Arquivo)
- `listFiles(path)` - Lista arquivos e pastas
- `createFolder(parent, name)` - Cria pasta
- `deleteItem(path)` - Exclui arquivo/pasta
- `renameItem(path, newName)` - Renomeia
- `getStorageInfo()` - Informações de armazenamento
- `getRootPath()` - Caminho raiz
- `getStandardPaths()` - Caminhos padrão
- `fileExists(path)` - Verifica existência
- `getItemSize(path)` - Obtém tamanho

### 2. StorageBridge (Detecção de Dispositivos)
- `isSDCardConnected()` - Verifica se SD está conectado
- `getSDCardPath()` - Obtém caminho do SD
- `isUSBDriveConnected()` - Verifica se USB está conectado
- `getUSBDrivePath()` - Obtém caminho do USB
- `getStorageDevices()` - Lista dispositivos conectados
- `getStorageInfo(path)` - Info de dispositivo específico
- `ejectStorage(path)` - Ejeta dispositivo

## Como Usar no JavaScript

### FileBridge
```javascript
// Listar arquivos
const files = JSON.parse(window.FileBridge.listFiles('/storage/emulated/0'));

// Criar pasta
const result = JSON.parse(window.FileBridge.createFolder('/storage/emulated/0', 'NovaPasta'));

// Obter info de armazenamento
const info = JSON.parse(window.FileBridge.getStorageInfo());
```

### StorageBridge
```javascript
// Verificar SD card
const hasSD = window.StorageBridge.isSDCardConnected();

// Verificar USB
const hasUSB = window.StorageBridge.isUSBDriveConnected();

// Listar dispositivos
const devices = JSON.parse(window.StorageBridge.getStorageDevices());

// Obter info de armazenamento
const info = JSON.parse(window.StorageBridge.getStorageInfo('/storage/sdcard1'));
```

## Permissões

### Android 10 e abaixo
- `READ_EXTERNAL_STORAGE`
- `WRITE_EXTERNAL_STORAGE`

### Android 11+
- `MANAGE_EXTERNAL_STORAGE`

### USB
- `android.hardware.usb.host` (opcional)

## Como Buildar

### Usando Android Studio
1. Abra o projeto no Android Studio
2. Aguarde a sincronização do Gradle
3. Clique em "Run" ou pressione Shift+F10

### Usando Gradle (terminal)
```bash
cd file-manager-android
./gradlew assembleDebug
```

O APK será gerado em: `app/build/outputs/apk/debug/app-debug.apk`

## Notas

1. **Assets Web**: Os arquivos web do File Manager estão na pasta `app/src/main/assets/`
2. **JavaScript Interface**: As bridges são acessíveis via `window.FileBridge` e `window.StorageBridge`
3. **Permissões**: O app solicita permissões automaticamente na primeira execução
4. **USB**: A detecção de USB depende do dispositivo e versão do Android

## Compatibilidade

- **Mínimo**: Android 7.0 (API 24)
- **Alvo**: Android 14 (API 34)
- **Testado em**: Android 10, 11, 12, 13, 14
