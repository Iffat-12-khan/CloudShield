package CloudShield;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import controller.ApiController;
import database.DBConnection;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * File Location: src/CloudShield/Main.java
 * Short Purpose: Main entry point for CloudShield with built-in embedded HTTP server.
 *                Enables 1-click execution for local testing, demo, and BCA viva presentations
 *                without needing external Tomcat or web server installation.
 * Connections:
 *   - Serves static frontend files from the web/ directory.
 *   - Routes /api/* requests directly into ApiController.
 *   - Tests live PostgreSQL database connection on startup.
 */
public class Main {

    private static final int PORT = 8080;
    private static final ApiController apiController = new ApiController();

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  CloudShield - Cloud Resource Management, Deadlock Detection    ");
        System.out.println("                   & Cybersecurity Threat Detection               ");
        System.out.println("==================================================================");

        // Test database availability
        boolean isPgActive = DBConnection.isPostgresAvailable();
        if (isPgActive) {
            System.out.println("[Status] PostgreSQL Database: CONNECTED (Live Mode)");
        } else {
            System.out.println("[Status] PostgreSQL Database: OFFLINE / FALLBACK");
            System.out.println("         (Running with seeded in-memory store for viva demonstration)");
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // API Route Handler
            server.createContext("/api", new ApiHttpHandler());

            // Static Web Assets Handler
            server.createContext("/", new StaticFileHandler());

            server.setExecutor(null); // default executor
            server.start();

            System.out.println("------------------------------------------------------------------");
            System.out.println(">>> Application Server running at: http://localhost:" + PORT);
            System.out.println(">>> Demo Accounts (Username / Password):");
            System.out.println("    1. Admin:    admin / admin123  (Full system control)");
            System.out.println("    2. Operator: john  / john123   (VMs & Resource management)");
            System.out.println("    3. Viewer:   alex  / alex123   (Read-only telemetry)");
            System.out.println("------------------------------------------------------------------");
            System.out.println("Press Ctrl+C to terminate server.");

        } catch (IOException e) {
            System.err.println("Fatal Error starting HTTP Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handler for /api/* requests.
     */
    static class ApiHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

            // Extract query parameters
            Map<String, String> queryParams = new HashMap<>();
            if (query != null && !query.isEmpty()) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        queryParams.put(pair[0], pair[1]);
                    }
                }
            }

            // Extract headers
            Map<String, String> headers = new HashMap<>();
            for (Map.Entry<String, java.util.List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    headers.put(entry.getKey().toLowerCase(), entry.getValue().get(0));
                }
            }

            // Dispatch to controller
            ApiController.ApiResponse response = apiController.handleRequest(
                method, path, queryParams, headers, exchange.getRequestBody(), clientIp
            );

            // CORS headers for local web development
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.getResponseHeaders().set("Content-Type", response.contentType);

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            byte[] bytes = response.body.getBytes("UTF-8");
            exchange.sendResponseHeaders(response.statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Handler for serving HTML, CSS, JavaScript, and static media files.
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Prevent path traversal
            if (path.contains("..")) {
                sendNotFound(exchange);
                return;
            }

            // Search in web/ or WebContent/
            Path filePath = Paths.get("web" + path);
            if (!Files.exists(filePath)) {
                filePath = Paths.get("WebContent" + path);
            }
            if (!Files.exists(filePath)) {
                filePath = Paths.get(path.startsWith("/") ? path.substring(1) : path);
            }

            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                String mimeType = getMimeType(filePath.toString());
                exchange.getResponseHeaders().set("Content-Type", mimeType);
                byte[] fileBytes = Files.readAllBytes(filePath);
                exchange.sendResponseHeaders(200, fileBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileBytes);
                }
            } else {
                sendNotFound(exchange);
            }
        }

        private void sendNotFound(HttpExchange exchange) throws IOException {
            String msg = "<h1>404 Not Found - CloudShield</h1>";
            byte[] bytes = msg.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String getMimeType(String path) {
            String p = path.toLowerCase();
            if (p.endsWith(".html")) return "text/html; charset=UTF-8";
            if (p.endsWith(".css")) return "text/css; charset=UTF-8";
            if (p.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (p.endsWith(".json")) return "application/json; charset=UTF-8";
            if (p.endsWith(".png")) return "image/png";
            if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
            if (p.endsWith(".svg")) return "image/svg+xml";
            return "application/octet-stream";
        }
    }
}
