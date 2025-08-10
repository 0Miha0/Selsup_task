**Класс CrptApi**
Основной класс для работы с API

**Конструкторы:**
public CrptApi(TimeUnit timeUnit, int requestLimit): Основной конструктор
timeUnit: Единица времени (TimeUnit.SECONDS, TimeUnit.MINUTES и т.д.)
requestLimit: Максимальное количество запросов за указанную единицу времени

**Методы:**
public String **createDocument(Document document, String signature, String productGroup)**: Создает документ. Метод является блокирующим и ожидает, если лимит запросов исчерпан
public void **shutdown()**: Корректно останавливает внутренний планировщик. Обязательно вызывайте этот метод при завершении работы с API для освобождения ресурсов
