#!/bin/bash

# Скрипт для установки APK на Android устройство

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
ADB_PATH="$HOME/Android/Sdk/platform-tools/adb"

# Проверка наличия APK
if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK файл не найден: $APK_PATH"
    echo "Сначала соберите APK: ./gradlew assembleDebug"
    exit 1
fi

echo "=== Установка APK на Android устройство ==="
echo ""

# Проверка ADB
if [ ! -f "$ADB_PATH" ]; then
    echo "❌ ADB не найден: $ADB_PATH"
    exit 1
fi

# Проверка подключенных устройств
echo "Проверка подключенных устройств..."
DEVICES=$($ADB_PATH devices | grep -v "List of devices" | grep "device$" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo ""
    echo "⚠️  Устройство не подключено!"
    echo ""
    echo "Инструкция по подключению:"
    echo "1. Подключите Android устройство к компьютеру через USB"
    echo "2. На устройстве: Настройки → О телефоне → 7 раз нажмите на 'Номер сборки'"
    echo "3. Вернитесь в Настройки → Для разработчиков → Включите 'Отладка по USB'"
    echo "4. При появлении запроса на устройстве разрешите отладку по USB"
    echo "5. Запустите этот скрипт снова"
    echo ""
    echo "Или используйте способ 2: скопируйте APK на устройство и установите вручную"
    echo ""
    read -p "Показать расположение APK файла? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        echo "APK файл находится здесь:"
        echo "$(pwd)/$APK_PATH"
        echo ""
        echo "Скопируйте этот файл на ваше устройство и откройте его для установки"
    fi
    exit 1
fi

echo "✓ Найдено устройств: $DEVICES"
echo ""

# Показываем список устройств
echo "Подключенные устройства:"
$ADB_PATH devices
echo ""

# Установка APK
echo "Установка APK..."
$ADB_PATH install -r "$APK_PATH"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ APK успешно установлен!"
    echo ""
    echo "Найдите приложение 'Object Detection' на вашем устройстве и запустите его."
    echo "При первом запуске разрешите доступ к камере."
else
    echo ""
    echo "❌ Ошибка при установке"
    echo ""
    echo "Возможные решения:"
    echo "1. Убедитесь, что на устройстве включена 'Отладка по USB'"
    echo "2. Разрешите установку из неизвестных источников (если требуется)"
    echo "3. Попробуйте: $ADB_PATH install -r -d $APK_PATH"
fi




