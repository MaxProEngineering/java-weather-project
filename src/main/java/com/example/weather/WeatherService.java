package com.example.weather; // Объявление пакета, соответствующего пути src/main/java/com/example/weather

import com.google.gson.Gson; // Импорт класса Gson — основного парсера JSON из библиотеки Google Gson
import com.google.gson.GsonBuilder; // Импорт GsonBuilder — построителя Gson с настройками (нужен для pretty printing)
import com.google.gson.JsonParser; // Импорт JsonParser — разбирает JSON в общую структуру без привязки к классам
import com.google.gson.annotations.SerializedName; // Импорт аннотации SerializedName — связывает поля JSON с полями record

import java.net.URI; // Импорт класса URI — нужен для построения адреса запроса
import java.net.http.HttpClient; // Импорт HttpClient — HTTP-клиент из стандартной библиотеки Java 11+
import java.net.http.HttpRequest; // Импорт HttpRequest — класс, описывающий HTTP-запрос
import java.net.http.HttpResponse; // Импорт HttpResponse — класс, описывающий HTTP-ответ сервера
import java.time.Duration; // Импорт Duration — задаёт таймаут ожидания ответа
import java.time.LocalDate; // Импорт LocalDate — для парсинга дат из прогноза
import java.time.format.DateTimeParseException; // Импорт DateTimeParseException — необходим для обработки ошибок в блоке try-catch при парсинге даты из API
import java.time.format.DateTimeFormatter; // Импорт DateTimeFormatter — задаёт формат вывода дат
import java.util.List; // Импорт List — тип списка дней прогноза
import java.util.Scanner; // Импорт Scanner — для чтения ввода пользователя из консоли

// Объявление публичного класса приложения
public class WeatherService {
    // Объявление константы с API-ключом. Необходимо сюда вставить свой ключ X-Yandex-Weather-Key
    private static final String API_KEY = "Вставьте Ваш API_KEY";

    // Точка запроса API, возвращающая прогноз по координатам
    private static final String URL = "https://api.weather.yandex.ru/v2/forecast";

    // Объявление константы с максимальным допустимым количеством суток прогноза погоды
    // Максимум зависит от тарифа (см. документацию API): для тестового (API Яндекс Погода) — это 7-дневный прогноз
    private static final int MAX_LIMIT = 7;

    // Объявление record — корневой модели ответа: текущая погода и список дней
    private record ForecastResponse(Fact fact, List<ForecastDay> forecasts) {}

    // Объявление record — модели текущей погоды с полем temp
    private record Fact(@SerializedName("temp") Double temp) {}

    // Объявление record — модели одного дня прогноза: дата и части суток
    private record ForecastDay(@SerializedName("date") String date, Parts parts) {}

    // Объявление record — модели частей суток внутри дня прогноза
    private record Parts(DayPart day) {}

    // Объявление record — модели дневной части суток с полем temp_avg
    private record DayPart(@SerializedName("temp_avg") Double tempAvg) {}

    // Объявление точки входа программы
    public static void main(String[] args) {
        // Начало блока try — перехват любых исключений, чтобы программа не падала
        try {
            // Вызов основного метода со всей логикой приложения
            run();
        // Начало блока catch — обработка возникшей ошибки
        } catch (Exception e) {
            // Вывод сообщения об ошибке пользователю
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    // Объявление основного метода со всей логикой приложения
    private static void run() throws Exception {
        // Создание объекта Scanner для чтения ввода пользователя
        Scanner scanner = new Scanner(System.in);

        // Вывод приветственного заголовка программы
        System.out.println("=================== Сервис по получению метеорологических данных ===================");

        // Чтение и валидация координаты lat с диапазоном от -90 до 90
        double lat = readCoordinate(scanner, "Введите значение координаты lat (широта, в градусах) от -90 до 90 (например, 55.83): ",
                -90.0, 90.0);

        // Чтение и валидация координаты lon с диапазоном от -180 до 180
        double lon = readCoordinate(scanner, "Введите значение координаты lon (долгота, в градусах) от -180 до 180 (например, 37.61): ",
                -180.0, 180.0);

        // Чтение и валидация параметра limit с ограничением по максимуму
        int limit = readLimit(scanner);

        // Формирование полного адреса запроса из URL и параметров lat, lon, limit, lang
        String uri = String.format("%s?lat=%s&lon=%s&limit=%d&lang=ru_RU",
                URL, lat, lon, limit);

        // Вывод заголовка раздела "1)"
        System.out.println("1) HTTP-запрос к API: выполняется GET-запрос к " + uri);

        // Создание построителя HTTP-запроса
        HttpRequest request = HttpRequest.newBuilder()
                // Указание адреса запроса из строки uri
                .uri(URI.create(uri))
                // Установка таймаута ожидания ответа в 10 секунд
                .timeout(Duration.ofSeconds(10))
                // Добавление заголовка с API-ключом
                .header("X-Yandex-Weather-Key", API_KEY)
                // Указание HTTP-метода GET
                .GET()
                // Завершение построения объекта запроса
                .build();

        // Создание нового HTTP-клиента для отправки запроса
        HttpClient client = HttpClient.newHttpClient();

        // Отправка запроса и получение ответа в виде строки
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        // Проверка того, что код ответа отличается от 200 (успех)
        if (response.statusCode() != 200) {
            // Вывод сообщения о коде ошибки от сервера
            System.out.println("   Сервер вернул ошибку " + statusText(response.statusCode()));
            // Вывод тела ответа с описанием ошибки
            System.out.println("   Тело ответа: " + response.body());
            // Завершение работы метода при ошибке запроса
            return;
        }

        // Вывод сообщения об успешном получении ответа
        System.out.println("   Ответ получен: " + statusText(response.statusCode()));

        // Сохранение тела ответа сервера в строковую переменную
        String jsonBody = response.body();

        System.out.println("2) Полный JSON-ответ «как есть» (Справочно: если необходим JSON-ответ в читаемом отформатированном виде, то прошу обратиться к пункту «5)» ниже):");

        // Вывод тела ответа сервера без изменений
        System.out.print(jsonBody);

        // Создание объекта Gson для разбора JSON в модели данных
        Gson gson = new Gson();

        // Преобразование JSON-строки в объект модели ForecastResponse
        ForecastResponse data = gson.fromJson(jsonBody, ForecastResponse.class);

        // Проверка наличия данных о текущей температуре в ответе
        if (data.fact() != null && data.fact().temp() != null) {
            // Вывод значения текущей температуры fact.temp
            System.out.printf("3) Текущая температура в момент запроса к API (fact.temp) корректно извлечена и составляет: %.1f °C%n",
                    data.fact().temp());
        } else {
            // Вывод сообщения о том, что fact.temp получить не удалось
            System.out.println("   Не удалось получить значение текущей температуры (fact.temp) из ответа.");
        }

        // Получение фактического числа дней в ответе сервера
        int availableDays = data.forecasts() != null ? data.forecasts().size() : 0;

        // Выбор числа дней для расчёта: минимум из запрошенного limit и доступного
        int daysUsed = Math.min(limit, availableDays);

        // Проверка наличия прогнозных дней в принципе
        if (availableDays == 0) {
            // Вывод сообщения о невозможности расчёта из-за отсутствия прогнозых дней
            System.out.println("В ответе отсутствуют прогнозные данные. Расчёт средней температуры невозможен.");
            // Завершение работы метода (нет возможности расчета среднего из-за отсутствия прогнозных данных)
            return;
        }

        // Проверка, для выявления случая, когда фактически полученных данных меньше, чем запрошено
        if (daysUsed < limit) {
            // Вывод предупреждения об отсутствии части запрошенных прогнозных данных
            System.out.println("Внимание: запрошенные данные отсутствуют.");
            // Вывод информации о том, сколько прогнозных суток реально доступно
            System.out.printf("Данных за %d суток нет, максимально доступно %d суток%n",
                    limit, daysUsed);
            // Вывод информации о числе прогнозных суток, по которым будет рассчитана средняя температура
            System.out.printf("Средняя температура будет рассчитана только за %d суток%n", daysUsed);
        }

        // Вызов метода расчёта и вывода средней температуры по дням
        printAverage(daysUsed, data);

        // Объявление переменной для хранения нормализованного ответа пользователя
        String answer;

        // Начало бесконечного цикла — повторяем вопрос пользователю до корректного ответа
        while (true) {
            // Вывод вопроса пользователю о том, необходимо ли вывести JSON-ответ в красивом формате
            System.out.print("5) Вывести JSON-ответ из пункта \"2)\" выше в читаемом отформатированном виде? (Да/Нет): ");
            // Чтение ответа пользователя с очисткой пробелов по краям
            String input = scanner.nextLine().trim();

            // Проверка того, что пользователь ответил согласием
            if (input.equalsIgnoreCase("да") || input.equalsIgnoreCase("yes")) {
                // Присвоение флага согласия для выхода из цикла
                answer = "да";
                // Завершение цикла — ответ корректен
                break;
            // Проверка, что пользователь ответил отказом
            } else if (input.equalsIgnoreCase("нет") || input.equalsIgnoreCase("no")) {
                // Присвоение флага отказа для выхода из цикла
                answer = "нет";
                // Завершение цикла — ответ корректен
                break;
            } else {
                // Вывод сообщения о непредусмотренном ответе с цитатой пользователя
                System.out.println("Ответ \"" + input + "\" не предусмотрен. Пожалуйста, введите Да или Нет.");
            }
        }

        // Проверка того, что пользователь дал согласие на вывод JSON-ответа в читаемом отформатированном виде
        if (answer.equalsIgnoreCase("да")) {
            // Вывод заголовка отформатированного ответа
            System.out.println("   ---------- JSON-ответ в читаемом отформатированном виде ----------");
            // Создание Gson с включённым режимом красивого форматирования
            Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
            // Разбор исходного JSON в общую структуру и вывод его в отформатированном виде
            System.out.println(prettyGson.toJson(JsonParser.parseString(jsonBody)));
        // Начало ветки else — если пользователь ответил «Нет»
        } else {
            // Вывод сообщения о пропуске вывода отформатированного JSON
            System.out.println("   Вывод отформатированного JSON-ответа не осуществляется по решению пользователя (Справочно: JSON-ответ «как есть» содержится в пункте «2)» выше)");
        }
    }

    // Объявление метода перевода кода ответа сервера в читаемый вид
    private static String statusText(int code) {
        // Инструкции для различных кодов ответа
        return switch (code) {
            case 200 -> "HTTP 200 OK (Аутентификация прошла успешно: заголовок X-Yandex-Weather-Key был передан, ключ валиден; запрос синтаксически корректен; тело ответа содержит JSON)";
            case 400 -> "HTTP 400 Bad Request";
            case 403 -> "HTTP 403 Forbidden";
            case 404 -> "HTTP 404 Not Found";
            case 429 -> "HTTP 429 Too Many Requests";
            case 500 -> "HTTP 500 Internal Server Error";
            case 503 -> "HTTP 503 Service Unavailable";
            default -> "HTTP " + code;
        };
    }

    // Объявление метода расчёта и вывода средней температуры по дням
    private static void printAverage(int daysUsed, ForecastResponse data) {
        // Создание форматтера дат в виде дд.ММ.гггг
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // Объявление накопителя суммы температур
        double sum = 0;
        // Объявление счётчика дней с корректными данными
        int validDays = 0;
        // Создание построителя строк для таблицы прогноза
        StringBuilder table = new StringBuilder();
        // Добавление строки заголовка таблицы в построитель
        table.append(String.format("   %-12s | %s%n", "Дата", "Среднедневная температура (°C)"));
        // Добавление строки-разделителя таблицы в построитель
        table.append("   -------------+---------------").append(System.lineSeparator());

        // Начало цикла по дням прогноза
        for (int i = 0; i < daysUsed; i++) {
            // Получение очередного дня прогноза из списка по индексу
            ForecastDay day = data.forecasts().get(i);

            // Проверка наличия данных о дневной части суток у этого дня
            if (day.parts() == null || day.parts().day() == null || day.parts().day().tempAvg() == null) {
                // Пропуск дня без данных — он не участвует в расчёте
                continue;
            }
            
            // Извлечение средней дневной температуры текущего дня
            double tempAvg = day.parts().day().tempAvg();
            // Добавление температуры дня к общей сумме
            sum += tempAvg;
            // Увеличение счётчика валидных дней
            validDays++;
            // Сохранение даты дня в исходном строковом виде
            String dateStr = day.date();

            // Начало блока try — попытка преобразовать дату к красивому виду
            try {
                // Разбор даты в формате ISO и переформатирование в дд.ММ.гггг
                dateStr = LocalDate.parse(day.date()).format(formatter);
            // Начало блока catch — перехват ошибки разбора даты
            } catch (DateTimeParseException e) {
                // Сообщение в поток ошибок о том, что строку не удалось разобрать
                System.err.println("Не удалось разобрать дату: " + dateStr);
            }

            // Добавление строки таблицы с датой и температурой дня
            table.append(String.format("   %-12s | %10.1f%n", dateStr, tempAvg));
        }

        // Проверка того, что хотя бы один день участвовал в расчёте
        if (validDays > 0) {
            // Вычисление среднего арифметического температур валидных дней
            double averageTemp = sum / validDays;
            // Вывод итогового значения средней температуры
            System.out.printf("4) Средняя температура по прогнозу, а именно среднее арифметическое от среднедневных прогнозных температур (parts.day.temp_avg) за %d суток: %.1f °C%n", daysUsed, averageTemp);
            // Вывод заголовка справочной таблицы с прогнозами
            System.out.printf("   Справочно:%n   Таблица с прогнозом среднедневной температуры (parts.day.temp_avg) для каждого дня и их среднее арифметическое:%n");
            // Добавление разделителя перед итоговой строкой
            table.append("   -------------+---------------").append(System.lineSeparator());
            // Добавление в таблицу итоговой строки со средней температурой
            table.append(String.format("   %-12s | %10.1f%n", "Средняя", averageTemp));
            // Вывод собранной таблицы прогноза по дням
            System.out.print(table);
        // Начало ветки else — если валидных дней нет
        } else {
            // Вывод сообщения об отсутствии данных для расчёта
            System.out.println("   Отсутствуют данные с прогнозом среднедневной температуры (parts.day.temp_avg) для расчёта среднего арифметического.");
        }
    }

    // Объявление метода чтения и валидации координаты
    private static double readCoordinate(Scanner scanner, String prompt, double min, double max) {
        // Начало бесконечного цикла (ввод повторяеися до корректного значения)
        while (true) {
            // Вывод приглашения ко вводу значения
            System.out.print(prompt);
            // Чтение строки пользователя с очисткой пробелов и заменой запятой на точку
            String input = scanner.nextLine().trim().replace(',', '.');

            // Начало блока try — попытка преобразовать строку в число
            try {
                // Преобразование строки в вещественное число
                double value = Double.parseDouble(input);
                
                // Проверка выхода значения за допустимый диапазон
                if (value < min || value > max) {
                    // Вывод сообщения об ошибке диапазона и просьбе повторить ввод
                    System.out.printf("Ошибка: значение должно быть в диапазоне от %s до %s. Попробуйте ещё раз.%n", min, max);
                    // Переход к следующей итерации цикла ввода
                    continue;
                }
                
                // Возврат корректного значения координаты
                return value;
           
            // Начало блока catch — перехват ошибки преобразования нечисловой строки
            } catch (NumberFormatException e) {
                // Вывод сообщения о том, что введено не число
                System.out.println("Ошибка: введено не число. Попробуйте ещё раз.");
            }
        }
    }

    // Объявление метода чтения и валидации параметра limit
    private static int readLimit(Scanner scanner) {
        // Начало бесконечного цикла — повторяем ввод до корректного значения
        while (true) {
            // Вывод приглашения ввести limit (количество прогнозируемых суток, включая текущие сутки) с указанием допустимого диапазона
            System.out.printf("Введите желаемый параметр запроса limit (количество прогнозируемых суток, включая текущие сутки), целое число от 1 до %d (ограничение тестового тарифа): ", MAX_LIMIT);
            // Чтение строки пользователя с очисткой пробелов по краям
            String input = scanner.nextLine().trim();

            // Начало блока try — попытка преобразовать строку в целое число
            try {
                // Преобразование строки в целое число
                int value = Integer.parseInt(input);
                
                // Проверка того, что значение не положительное
                if (value <= 0) {
                    // Вывод сообщения об ошибке и просьбе повторить ввод
                    System.out.println("Ошибка: значение должно быть положительным целым числом. Попробуйте ещё раз.");
                    // Переход к следующей итерации цикла ввода
                    continue;
                }

                // Проверка превышения максимально допустимого значения
                if (value > MAX_LIMIT) {
                    // Вывод предупреждения об отсутствии данных за запрошенное число суток
                    System.out.println("Внимание: данных за " + value + " суток нет.");
                    // Вывод информации о максимально доступном числе суток
                    System.out.println("Максимально доступно " + MAX_LIMIT + " суток, поэтому для расчетов будут использоваться только они.");
                    // Ограничение значения limit максимально допустимым
                    value = MAX_LIMIT;
                }

                // Возврат корректного значения limit
                return value;

            // Начало блока catch — перехват ошибки преобразования нечисловой строки
            } catch (NumberFormatException e) {
                // Вывод сообщения о том, что введено не целое число
                System.out.println("Ошибка: введено не целое число. Попробуйте ещё раз.");
            }
        }
    }
}