# Инструкции по сборке APK

## Быстрая сборка

Запустите скрипт сборки:
```bash
./build_apk.sh
```

## Ручная сборка

### 1. Установка зависимостей

#### Java JDK (требуется версия 17 или выше)

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# Или для Java 21
sudo apt install openjdk-21-jdk

# Проверка установки
java -version
```

#### Android SDK

**Вариант A: Через Android Studio (рекомендуется)**
1. Скачайте и установите [Android Studio](https://developer.android.com/studio)
2. Android Studio автоматически установит SDK
3. SDK обычно находится в `~/Android/Sdk`

**Вариант B: Командная строка (только SDK)**
```bash
# Скачайте command line tools
mkdir -p ~/Android/Sdk
cd ~/Android/Sdk
wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
unzip commandlinetools-linux-9477386_latest.zip
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true

# Настройте переменные окружения
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# Установите необходимые компоненты
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Добавьте в `~/.bashrc` или `~/.zshrc`:
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
```

### 2. Проверка модели

Убедитесь, что файл модели находится в правильном месте:
```bash
ls -lh app/src/main/assets/yolov8n.tflite
```

Если файла нет, конвертируйте модель:
```bash
python3 convert_model.py
cp yolov8n.tflite app/src/main/assets/
```

### 3. Сборка APK

```bash
# Очистка предыдущих сборок
./gradlew clean

# Сборка debug APK
./gradlew assembleDebug

# Или сборка release APK (требует настройки подписи)
./gradlew assembleRelease
```

### 4. Найденный APK

После успешной сборки APK будет находиться в:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 5. Установка на устройство

```bash
# Подключите устройство через USB и включите отладку по USB
adb devices

# Установите APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Сборка Release APK (для публикации)

1. Создайте файл `app/keystore.properties`:
```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=your_key_alias
storeFile=path/to/keystore.jks
```

2. Создайте keystore (если еще нет):
```bash
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
```

3. Соберите release APK:
```bash
./gradlew assembleRelease
```

## Устранение проблем

### Ошибка: "JAVA_HOME is not set"
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
# Или для Java 21
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

### Ошибка: "SDK location not found"
Убедитесь, что `ANDROID_HOME` установлен:
```bash
export ANDROID_HOME=$HOME/Android/Sdk
```

Или создайте файл `local.properties` в корне проекта:
```properties
sdk.dir=/home/your_username/Android/Sdk
```

### Ошибка: "Failed to find target with hash string 'android-34'"
Установите необходимую платформу:
```bash
sdkmanager "platforms;android-34"
```

### Ошибка при загрузке Gradle
Проверьте интернет-соединение. Gradle автоматически скачает необходимые зависимости при первой сборке.

## Размер APK

- Debug APK: ~15-25 MB (включает модель YOLO ~6MB)
- Release APK: ~10-15 MB (с оптимизацией и сжатием)

## Альтернативный способ: Docker

Если у вас проблемы с установкой зависимостей, можно использовать Docker:

```bash
docker run --rm -v $(pwd):/project -w /project android-build-tools ./gradlew assembleDebug
```




