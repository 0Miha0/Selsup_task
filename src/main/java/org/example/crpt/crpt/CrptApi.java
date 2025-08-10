package org.example.crpt.crpt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final long intervalMillis;

    /**
     * Перегруженный конструктор класса CrptApi для внедрения HttpClient.
     *
     * @param timeUnit     Единица измерения интервала времени (например, TimeUnit.SECONDS).
     * @param requestLimit Максимальное количество запросов в указанный интервал времени.
     * @param httpClient   Экземпляр HttpClient для использования.
     * @throws IllegalArgumentException Если requestLimit не является положительным числом.
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit, HttpClient httpClient) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be a positive value");
        }
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.semaphore = new Semaphore(requestLimit);
        this.intervalMillis = timeUnit.toMillis(1);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            int permitsToAdd = requestLimit - semaphore.availablePermits();
            if (permitsToAdd > 0) {
                semaphore.release(permitsToAdd);
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Создает документ для ввода товара в оборот
     * Этот метод блокируется, если превышен лимит запросов, и продолжит выполнение, когда лимит позволяет.
     *
     * @param document     Объект, представляющий структуру документа.
     * @param signature    Строка, содержащая открепленную подпись в base64.
     * @param productGroup Строка, представляющая товарную группу (например, "milk", "shoes").
     * @return String Идентификатор созданного документа или сообщение об ошибке.
     * @throws IOException          Если произошла ошибка при сериализации/десериализации JSON или при отправке HTTP-запроса.
     * @throws InterruptedException Если текущий поток был прерван во время ожидания.
     */
    public String createDocument(Document document, String signature, String productGroup)
            throws IOException, InterruptedException {

        semaphore.acquire();

        String requestBody = objectMapper.writeValueAsString(new CreateDocumentRequest(
                document, signature, productGroup, DocumentFormat.MANUAL, DocType.LP_INTRODUCE_GOODS, objectMapper));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create?pg=" + productGroup))
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return "Документ создан: " + response.body();
        } else {
            return "Ошибка API: " + response.statusCode() + " - " + response.body();
        }
    }

    /**
     * Внутренний класс для представления документа.
     * Может быть расширен для поддержки различных типов документов.
     */
    public static class Document {
        @JsonProperty("description")
        public Description description;
        @JsonProperty("participantInn")
        public String participantInn;
        @JsonProperty("doc_id")
        public String docId;
        @JsonProperty("doc_status")
        public String docStatus;
        @JsonProperty("doc_type")
        public String docType;
        @JsonProperty("importRequest")
        public boolean importRequest;
        @JsonProperty("owner_inn")
        public String ownerInn;
        @JsonProperty("participant_inn")
        public String participantInnField;
        @JsonProperty("producer_inn")
        public String producerInn;
        @JsonProperty("production_date")
        public String productionDate;
        @JsonProperty("production_type")
        public String productionType;
        @JsonProperty("products")
        public List<Product> products;
        @JsonProperty("reg_date")
        public String regDate;
        @JsonProperty("reg_number")
        public String regNumber;


        public Document(Description description, String participantInn, String docId, String docStatus, String docType,
                        boolean importRequest, String ownerInn, String participantInnField, String producerInn,
                        String productionDate, String productionType, List<Product> products, String regDate, String regNumber) {
            this.description = description;
            this.participantInn = participantInn;
            this.docId = docId;
            this.docStatus = docStatus;
            this.docType = docType;
            this.importRequest = importRequest;
            this.ownerInn = ownerInn;
            this.participantInnField = participantInnField;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.productionType = productionType;
            this.products = products;
            this.regDate = regDate;
            this.regNumber = regNumber;
        }

        /**
         * Внутренний класс для описания документа.
         */
        public static class Description {
            @JsonProperty("participantInn")
            public String participantInn;

            public Description(String participantInn) {
                this.participantInn = participantInn;
            }
        }

        /**
         * Внутренний класс для представления продукта в документе.
         */
        public static class Product {
            @JsonProperty("certificate_document")
            public String certificateDocument;
            @JsonProperty("certificate_document_date")
            public String certificateDocumentDate;
            @JsonProperty("certificate_document_number")
            public String certificateDocumentNumber;
            @JsonProperty("owner_inn")
            public String ownerInn;
            @JsonProperty("producer_inn")
            public String producerInn;
            @JsonProperty("production_date")
            public String productionDate;
            @JsonProperty("tnved_code")
            public String tnvedCode;
            @JsonProperty("uit_code")
            public String uitCode;
            @JsonProperty("uitu_code")
            public String uituCode;

            public Product(String certificateDocument, String certificateDocumentDate, String certificateDocumentNumber,
                           String ownerInn, String producerInn, String productionDate, String tnvedCode,
                           String uitCode, String uituCode) {
                this.certificateDocument = certificateDocument;
                this.certificateDocumentDate = certificateDocumentDate;
                this.certificateDocumentNumber = certificateDocumentNumber;
                this.ownerInn = ownerInn;
                this.producerInn = producerInn;
                this.productionDate = productionDate;
                this.tnvedCode = tnvedCode;
                this.uitCode = uitCode;
                this.uituCode = uituCode;
            }
        }
    }

    /**
     * Внутренний класс для создания тела запроса к API.
     */
    private static class CreateDocumentRequest {
        @JsonProperty("document_format")
        public DocumentFormat documentFormat;
        @JsonProperty("product_document")
        public String productDocument;
        @JsonProperty("product_group")
        public String productGroup;
        @JsonProperty("signature")
        public String signature;
        @JsonProperty("type")
        public DocType type;

        public CreateDocumentRequest(Document document, String signature, String productGroup,
                                     DocumentFormat documentFormat, DocType type, ObjectMapper objectMapper) throws JsonProcessingException {
            this.productDocument = objectMapper.writeValueAsString(document);
            this.signature = signature;
            this.productGroup = productGroup;
            this.documentFormat = documentFormat;
            this.type = type;
        }
    }

    /**
     * Перечисление для формата документа.
     */
    public enum DocumentFormat {MANUAL, XML, CSV}

    /**
     * Перечисление для типа документа.
     */
    public enum DocType {LP_INTRODUCE_GOODS}

    /**
     * Метод для корректного завершения работы планировщика.
     * Важно вызывать его при завершении работы приложения, использующего CrptApi.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}


