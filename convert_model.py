#!/usr/bin/env python3
"""
Скрипт для конвертации модели YOLO 8n в формат TensorFlow Lite
Требуется установка: pip install ultralytics
"""

from ultralytics import YOLO
import os

def convert_yolo_to_tflite():
    """Конвертирует YOLOv8n модель в TFLite формат"""
    
    print("Загрузка модели YOLOv8n...")
    # Загружаем предобученную модель YOLOv8n
    model = YOLO('yolov8n.pt')  # Автоматически скачает если нет
    
    print("Экспорт в TensorFlow Lite...")
    # Экспортируем в TFLite
    model.export(
        format='tflite',
        imgsz=640,  # Размер входного изображения
        int8=False,  # Используем float32 для лучшей точности
        dynamic=False  # Статический размер входных данных
    )
    
    # Проверяем созданные файлы (могут быть в разных местах)
    possible_files = [
        'yolov8n.tflite',  # Стандартное расположение
        'yolov8n_saved_model/yolov8n_float32.tflite',  # SavedModel формат
        'yolov8n_saved_model/yolov8n_float16.tflite',  # SavedModel формат (float16)
    ]
    
    found_files = []
    for tflite_file in possible_files:
        if os.path.exists(tflite_file):
            found_files.append(tflite_file)
            size_mb = os.path.getsize(tflite_file) / (1024*1024)
            print(f"✓ Найден файл: {tflite_file} ({size_mb:.2f} MB)")
    
    if found_files:
        # Предпочитаем float32 для лучшей точности
        preferred_file = None
        for f in found_files:
            if 'float32' in f:
                preferred_file = f
                break
        if not preferred_file:
            preferred_file = found_files[0]
        
        print(f"\nРекомендуется использовать: {preferred_file}")
        print(f"\nПереместите файл в:")
        print("  app/src/main/assets/yolov8n.tflite")
        print(f"\nКоманда для копирования:")
        print(f"  cp {preferred_file} app/src/main/assets/yolov8n.tflite")
    else:
        print("✗ Ошибка: файл не найден")
        print("Проверьте, что экспорт завершился успешно")

if __name__ == "__main__":
    try:
        convert_yolo_to_tflite()
    except ImportError:
        print("Ошибка: требуется установка ultralytics")
        print("Установите: pip install ultralytics")
    except Exception as e:
        print(f"Ошибка: {e}")


