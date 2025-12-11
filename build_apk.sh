#!/bin/bash

# Скрипт для сборки APK файла
# Требования: Java JDK 17+, Android SDK

set -e

echo "=== Проверка зависимостей ==="

# Проверка Java
if ! command -v java &> /dev/null; then
    echo "❌ Java не установлена"
    echo "Установите Java JDK 17 или выше:"
    echo "  sudo apt install openjdk-17-jdk"
    echo "  или"
    echo "  sudo apt install openjdk-21-jdk"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "⚠️  Требуется Java 17 или выше. Найдена версия: $JAVA_VERSION"
    exit 1
fi

echo "✓ Java установлена: $(java -version 2>&1 | head -n 1)"

# Проверка ANDROID_HOME
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "⚠️  ANDROID_HOME не установлен"
    echo "Установите Android SDK и настройте переменную окружения:"
    echo "  export ANDROID_HOME=\$HOME/Android/Sdk"
    echo "  export PATH=\$PATH:\$ANDROID_HOME/tools:\$ANDROID_HOME/platform-tools"
    echo ""
    echo "Или установите Android Studio, который автоматически настроит SDK"
    echo ""
    read -p "Продолжить сборку без проверки Android SDK? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    ANDROID_SDK=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
    echo "✓ Android SDK найден: $ANDROID_SDK"
fi

# Проверка наличия модели
if [ ! -f "app/src/main/assets/yolov8n.tflite" ]; then
    echo "❌ Модель yolov8n.tflite не найдена в app/src/main/assets/"
    echo "Поместите файл модели в указанную директорию"
    exit 1
fi

echo "✓ Модель найдена: app/src/main/assets/yolov8n.tflite"

echo ""
echo "=== Начало сборки APK ==="
echo ""

# Очистка предыдущих сборок
./gradlew clean

# Сборка debug APK
./gradlew assembleDebug

# Проверка результата
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo ""
    echo "✅ APK успешно собран!"
    echo "📦 Файл: $APK_PATH"
    echo "📊 Размер: $APK_SIZE"
    echo ""
    echo "Для установки на устройство:"
    echo "  adb install $APK_PATH"
else
    echo "❌ Ошибка: APK файл не найден"
    exit 1
fi




