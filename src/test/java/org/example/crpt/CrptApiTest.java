package org.example.crpt;

import org.example.crpt.crpt.CrptApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;

/**
 * Класс для тестирования логики ограничения количества запросов (rate limiting)
 * и базового взаимодействия CrptApi.
 */
class CrptApiTest {

    private CrptApi crptApi;
    private HttpClient mockHttpClient;
    private HttpResponse<String> mockSuccessResponse;

    /**
     * Настройка тестовой среды перед каждым тестом.
     * Создает мок HttpClient и настраивает его на возврат успешного ответа.
     */
    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        mockHttpClient = Mockito.mock(HttpClient.class);
        mockSuccessResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(mockSuccessResponse.statusCode()).thenReturn(200);
        Mockito.when(mockSuccessResponse.body()).thenReturn("{\"document_id\": \"mock_doc_123\"}");
        Mockito.when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockSuccessResponse);
    }

    /**
     * Очистка тестовой среды после каждого теста.
     * Важно вызвать shutdown() для CrptApi, чтобы корректно завершить работу планировщика потоков.
     */
    @AfterEach
    void tearDown() {
        if (crptApi != null) {
            crptApi.shutdown();
        }
    }

    /**
     * Вспомогательный метод для создания минимального фиктивного документа.
     */
    private CrptApi.Document createMinimalDocument() {
        CrptApi.Document.Description description = new CrptApi.Document.Description("testInn");
        return new CrptApi.Document(
                description, "testParticipantInn", "testDocId", "testDocStatus", "testDocType",
                false, "testOwnerInn", "testParticipantInnField", "testProducerInn",
                "2023-01-01", "testProductionType", Collections.emptyList(), // Пустой список продуктов
                Instant.now().toString(), "testRegNumber"
        );
    }

    /**
     * Тестирует базовую функциональность создания документа.
     * Проверяет, что метод createDocument возвращает ожидаемый успешный результат
     * и что HttpClient был вызван.
     */
    @Test
    void testCreateDocumentSuccess() throws IOException, InterruptedException {
        crptApi = new CrptApi(TimeUnit.SECONDS, 1, mockHttpClient);
        CrptApi.Document document = createMinimalDocument();
        String signature = "test_signature";
        String productGroup = "shoes";
        String response = crptApi.createDocument(document, signature, productGroup);
        assertTrue(response.contains("Документ создан:"), "Ответ должен содержать подтверждение создания документа");
        assertTrue(response.contains("mock_doc_123"), "Ответ должен содержать ID мок-документа");
        Mockito.verify(mockHttpClient, Mockito.times(1))
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    /**
     * Тестирует логику ограничения количества запросов (rate limiting).
     * Тест проверяет, что второй запрос блокируется на ожидаемое время.
     */
    @Test
    void testRateLimitingBehavior() throws IOException, InterruptedException {
        long intervalMillis = 1000;
        crptApi = new CrptApi(TimeUnit.SECONDS, 1, mockHttpClient);
        Thread.sleep(50);

        long startTimeFirstRequest = System.currentTimeMillis();
        crptApi.createDocument(createMinimalDocument(), "dummy_sig", "dummy_group");
        long endTimeFirstRequest = System.currentTimeMillis();
        long durationFirstRequest = endTimeFirstRequest - startTimeFirstRequest;
        System.out.println("Продолжительность первого запроса: " + durationFirstRequest + " мс.");
        assertTrue(durationFirstRequest < 100, "Первый запрос должен быть быстрым.");

        long startTimeSecondRequest = System.currentTimeMillis();
        crptApi.createDocument(createMinimalDocument(), "dummy_sig", "dummy_group");
        long endTimeSecondRequest = System.currentTimeMillis();
        long durationSecondRequest = endTimeSecondRequest - startTimeSecondRequest;

        long tolerance = 200;
        long lowerBound = intervalMillis - tolerance;

        System.out.println("Продолжительность второго запроса: " + durationSecondRequest + " мс. Ожидаемый интервал: ~" + intervalMillis + " мс");
        assertTrue(durationSecondRequest >= lowerBound,
                "Второй запрос (" + durationSecondRequest + " мс) должен был ожидать около " + intervalMillis + " мс из-за лимитирования.");
        Mockito.verify(mockHttpClient, Mockito.times(2))
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    /**
     * Тестирует, что происходит при ошибке API (например, статус 401 Unauthorized).
     * HttpClient должен вернуть соответствующую ошибку, и метод createDocument должен ее обработать.
     */
    @Test
    void testCreateDocumentApiError() throws IOException, InterruptedException {
        HttpResponse<String> mockErrorResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(mockErrorResponse.statusCode()).thenReturn(401);
        Mockito.when(mockErrorResponse.body()).thenReturn("{\"error\": \"Unauthorized\"}");

        Mockito.when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockErrorResponse);

        crptApi = new CrptApi(TimeUnit.SECONDS, 1, mockHttpClient);
        String response = crptApi.createDocument(createMinimalDocument(), "dummy_sig", "dummy_group");
        assertTrue(response.contains("Ошибка API: 401"), "Ответ должен указывать на ошибку 401");
        assertTrue(response.contains("Unauthorized"), "Ответ должен содержать сообщение об ошибке API");
        Mockito.verify(mockHttpClient, Mockito.times(1))
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    /**
     * Тестирует выброс исключения InterruptedException при прерывании потока,
     * когда поток заблокирован на semaphore.acquire().
     */
    @Test
    void testInterruptedExceptionPropagation() throws InterruptedException {
        crptApi = new CrptApi(TimeUnit.SECONDS, 1, mockHttpClient);
        try {
            crptApi.createDocument(createMinimalDocument(), "dummy_sig", "dummy_group");
        } catch (IOException e) {
            fail("IOException не ожидался при первом вызове: " + e.getMessage());
        }
        CountDownLatch readyToInterrupt = new CountDownLatch(1);
        AtomicReference<Throwable> caughtException = new AtomicReference<>();

        Thread blockingThread = new Thread(() -> {
            try {
                readyToInterrupt.countDown();
                crptApi.createDocument(createMinimalDocument(), "dummy_sig", "dummy_group");
                fail("InterruptedException не был выброшен, когда ожидался.");
            } catch (InterruptedException e) {
                caughtException.set(e);
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                caughtException.set(e);
                fail("IOException был выброшен вместо InterruptedException: " + e.getMessage());
            }
        });

        blockingThread.start();
        assertTrue(readyToInterrupt.await(5, TimeUnit.SECONDS), "Блокирующий поток не сигнализировал о готовности к прерыванию вовремя.");
        blockingThread.interrupt();
        blockingThread.join(5000);
        assertNotNull(caughtException.get(), "Исключение не было выброшено в блокированном потоке.");
        assertInstanceOf(InterruptedException.class, caughtException.get(), "Ожидалось InterruptedException, но было " + caughtException.get().getClass().getSimpleName());
        assertFalse(blockingThread.isAlive(), "Блокированный поток должен завершиться.");
    }
}
