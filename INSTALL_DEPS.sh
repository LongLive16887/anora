#!/bin/bash

# Скрипт для установки зависимостей для сборки Android APK
# Требует sudo права

set -e

echo "=== Установка зависимостей для сборки Android APK ==="
echo ""

# Проверка Java
if ! command -v java &> /dev/null; then
    echo "Установка Java JDK 17..."
    sudo apt update
    sudo apt install -y openjdk-17-jdk
    
    # Настройка JAVA_HOME
    JAVA_HOME_PATH=$(readlink -f /usr/bin/java | sed "s:bin/java::")
    echo ""
    echo "Добавьте в ~/.bashrc или ~/.zshrc:"
    echo "export JAVA_HOME=$JAVA_HOME_PATH"
    echo "export PATH=\$PATH:\$JAVA_HOME/bin"
    echo ""
    read -p "Добавить автоматически? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "" >> ~/.bashrc
        echo "export JAVA_HOME=$JAVA_HOME_PATH" >> ~/.bashrc
        echo "export PATH=\$PATH:\$JAVA_HOME/bin" >> ~/.bashrc
        echo "✓ Добавлено в ~/.bashrc"
        echo "Выполните: source ~/.bashrc"
    fi
else
    echo "✓ Java уже установлена: $(java -version 2>&1 | head -n 1)"
fi

echo ""
echo "=== Android SDK ==="
echo "Для установки Android SDK рекомендуется использовать Android Studio:"
echo "1. Скачайте: https://developer.android.com/studio"
echo "2. Установите Android Studio"
echo "3. SDK будет автоматически установлен в ~/Android/Sdk"
echo ""
echo "Или установите только SDK через командную строку (см. BUILD_INSTRUCTIONS.md)"

echo ""
echo "После установки Android SDK, настройте переменные:"
echo "export ANDROID_HOME=\$HOME/Android/Sdk"
echo "export PATH=\$PATH:\$ANDROID_HOME/platform-tools"

echo ""
echo "=== Готово! ==="
echo "Запустите сборку: ./build_apk.sh"




