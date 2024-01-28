
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import org.apache.commons.dbcp2.BasicDataSource;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;


public class Main {
    private static final String CPU_USAGE_FILE = "cpuusage.csv";
    private static final String RAM_USAGE_FILE = "ramusage.csv";

    private static final String MONGO_HOST = "localhost";
    private static final int MONGO_PORT = 27017;
    private static final String MONGO_DATABASE = "Virtual_Threads";
    private static final String MONGO_COLLECTION = "temps"; // Adjust collection name

    private static final MongoClient mongoClient = createConnection();
    private static final MongoDatabase mongoDatabase = mongoClient.getDatabase(MONGO_DATABASE);

    public static MongoClient createConnection() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = loggerContext.getLogger("org.mongodb.driver");
        ((ch.qos.logback.classic.Logger) rootLogger).setLevel(Level.OFF);

        String connectionString = "mongodb://localhost:27017"; // Modify as needed

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))

                .build();

        return MongoClients.create(settings);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        System.out.println("Basic Http VT Server started...");
        HttpContext context = server.createContext("/", new CrudHandler());

        server.start();

//        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
//        scheduler.scheduleAtFixedRate(Main::monitorPerformance, 0, 5, TimeUnit.SECONDS);
    }






    private static void monitorPerformance() {
        // Monitor CPU and memory usage
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuUsage = osBean.getSystemLoadAverage();
        //System.out.println("CPU Usage: " + cpuUsage + "%");

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
//        System.out.println("Used Memory: " + usedMemory / (1024 * 1024) + " MB");
//        System.out.println("Max Memory: " + maxMemory / (1024 * 1024) + " MB");

        // Add to CSV files
        writeToFile(CPU_USAGE_FILE, cpuUsage + "," + new Date().toString());
        writeToFile(RAM_USAGE_FILE, usedMemory / (1024 * 1024) + "," + maxMemory / (1024 * 1024) + "," + new Date().toString());
    }

    private static void writeToFile(String fileName, String data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(data);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class CrudHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Runnable run = new Runnable() {
                @Override
                public void run() {


                    String requestMethod = exchange.getRequestMethod();
                    try {


                        if (requestMethod.equalsIgnoreCase("GET")) {
                            handleGetRequest(exchange);
                        } else if (requestMethod.equalsIgnoreCase("POST")) {
                            handlePostRequest(exchange);
                        } else if (requestMethod.equalsIgnoreCase("PUT")) {
                            handlePutRequest(exchange);
                        } else if (requestMethod.equalsIgnoreCase("DELETE")) {
                            handleDeleteRequest(exchange);
                        } else {
                            sendResponse(exchange, 400, "Bad Request");
                        }
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
            };
            //Virtual Threads
            Thread.startVirtualThread(run);
            //Normal Threads
//            Thread thread = new Thread(run);
//            thread.start();
        }

        private void handleGetRequest(HttpExchange exchange) throws IOException {
            // Implement your GET logic here (e.g., retrieve data from MongoDB)
            MongoCollection<Document> collection = mongoDatabase.getCollection(MONGO_COLLECTION);

            try (MongoCursor<Document> cursor = collection.find().iterator()) {
                StringBuilder response = new StringBuilder();
                while (cursor.hasNext()) {
                    Document document = cursor.next();
                    response.append(document.toJson()).append("\n");
                }

                sendResponse(exchange, 200, response.toString());
            }
        }

        private void handlePostRequest(HttpExchange exchange) throws IOException {
            try {
                InputStream requestBody = exchange.getRequestBody();
                InputStreamReader isr = new InputStreamReader(requestBody);
                BufferedReader br = new BufferedReader(isr);
                // Read the entire JSON input
                StringBuilder jsonInputBuilder = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    jsonInputBuilder.append(line);
                }
                String jsonInput = jsonInputBuilder.toString();

                // Trim the JSON input to remove extra characters
                jsonInput = jsonInput.trim();

                // Parse the JSON input into a Document
                Document document = Document.parse(jsonInput);

                // Insert the document into the collection
                mongoDatabase.getCollection(MONGO_COLLECTION).insertOne(document);


                sendResponse(exchange, 200, "POST request handled");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }


        private void handlePutRequest(HttpExchange exchange) throws IOException {
            try {
                // Read the entire JSON input
                String jsonInput = readJsonInput(exchange);

                // Parse the JSON input into a Document
                Document updatedDocument = Document.parse(jsonInput);

                // Extract the "year" field from the updatedDocument
                int yearToUpdate = updatedDocument.getInteger("year");

                // Create a query Document based on the extracted "year" value
                Document queryDocument = new Document("year", yearToUpdate);

                // Replace the document based on the query
                mongoDatabase.getCollection(MONGO_COLLECTION).replaceOne(queryDocument, updatedDocument);

                sendResponse(exchange, 200, "PUT request handled");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }

        private String readJsonInput(HttpExchange exchange) throws IOException {
            // Read the entire JSON input
            try (InputStream requestBody = exchange.getRequestBody();
                 InputStreamReader isr = new InputStreamReader(requestBody);
                 BufferedReader br = new BufferedReader(isr)) {

                StringBuilder jsonInputBuilder = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    jsonInputBuilder.append(line);
                }
                return jsonInputBuilder.toString().trim();
            }
        }


        private void handleDeleteRequest(HttpExchange exchange) throws IOException {
            try {
                InputStream requestBody = exchange.getRequestBody();
                InputStreamReader isr = new InputStreamReader(requestBody);
                BufferedReader br = new BufferedReader(isr);
                // Read the entire JSON input
                StringBuilder jsonInputBuilder = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    jsonInputBuilder.append(line);
                }
                String jsonInput = jsonInputBuilder.toString();

                // Trim the JSON input to remove extra characters
                jsonInput = jsonInput.trim();

                // Parse the JSON input into a Document
                Document document = Document.parse(jsonInput);

                // Extract the "year" field from the Document
                int yearToDelete = document.getInteger("year");

                // Create a query Document based on the extracted "year" value
                Document queryDocument = new Document("year", yearToDelete);

                // Delete the document based on the query
                mongoDatabase.getCollection(MONGO_COLLECTION).deleteOne(queryDocument);

                sendResponse(exchange, 200, "DELETE request handled");

            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }


        private void sendResponse(HttpExchange exchange, int statusCode, String response) {
            try {
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", "application/json"); // Adjust content type as needed

                // Convert the response string to bytes using UTF-8 encoding
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

                // Send the response headers
                exchange.sendResponseHeaders(statusCode, responseBytes.length);

                // Get the response body stream
                OutputStream os = exchange.getResponseBody();

                // Write the response bytes to the stream
                os.write(responseBytes);

                // Ensure that the response body is fully written before closing the stream
                os.flush();

                // Close the response body stream
                os.close();
            } catch (IOException e) {
                // Log the exception or handle it appropriately
                e.printStackTrace();
            }
        }

    }
}
