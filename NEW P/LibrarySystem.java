import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class LibrarySystem {
    
    //MODELS
    static class Book {
        String id;
        String title;
        String author;
        String genre;
        boolean available;
        String borrower;
        String issueDate;
        String dueDate;
        int timesIssued;
        
        Book(String id, String title, String author, String genre) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.genre = genre;
            this.available = true;
            this.borrower = "";
            this.timesIssued = 0;
        }
        
        void issueBook(String borrowerName) {
            this.available = false;
            this.borrower = borrowerName;
            this.issueDate = LocalDate.now().toString();
            this.dueDate = LocalDate.now().plusDays(14).toString();
            this.timesIssued++;
        }
        
        void returnBook() {
            this.available = true;
            this.borrower = "";
            this.issueDate = null;
            this.dueDate = null;
        }
        
        boolean isOverdue() {
            if (available || dueDate == null) return false;
            return LocalDate.now().isAfter(LocalDate.parse(dueDate));
        }
        
        long getOverdueDays() {
            if (!isOverdue()) return 0;
            return LocalDate.now().toEpochDay() - LocalDate.parse(dueDate).toEpochDay();
        }
        
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("title", title);
            map.put("author", author);
            map.put("genre", genre);
            map.put("available", available);
            map.put("borrower", borrower);
            map.put("issueDate", issueDate);
            map.put("dueDate", dueDate);
            map.put("timesIssued", timesIssued);
            map.put("isOverdue", isOverdue());
            map.put("overdueDays", getOverdueDays());
            return map;
        }
    }
    
    static class User {
        String userId;
        String name;
        String email;
        String phone;
        List<String> borrowedBooks;
        
        User(String userId, String name, String email, String phone) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.borrowedBooks = new ArrayList<>();
        }
        
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userId);
            map.put("name", name);
            map.put("email", email);
            map.put("phone", phone);
            map.put("borrowedBooks", borrowedBooks);
            map.put("borrowedCount", borrowedBooks.size());
            return map;
        }
    }
    
    //  DATA STORAGE 
    static List<Book> books = new ArrayList<>();
    static List<User> users = new ArrayList<>();
    
    static void initializeData() {
        books.add(new Book("B001", "The Great Gatsby", "F. Scott Fitzgerald", "Fiction"));
        books.add(new Book("B002", "To Kill a Mockingbird", "Harper Lee", "Classic"));
        books.add(new Book("B003", "1984", "George Orwell", "Dystopian"));
        books.add(new Book("B004", "Pride and Prejudice", "Jane Austen", "Romance"));
        books.add(new Book("B005", "The Hobbit", "J.R.R. Tolkien", "Fantasy"));
        books.add(new Book("B006", "Harry Potter", "J.K. Rowling", "Fantasy"));
        books.add(new Book("B007", "The Alchemist", "Paulo Coelho", "Philosophy"));
        
        users.add(new User("U001", "John Doe", "john@example.com", "1234567890"));
        users.add(new User("U002", "Jane Smith", "jane@example.com", "0987654321"));
    }
    
    //  API HANDLERS
    
    static class CorsHandler implements HttpHandler {
        private final HttpHandler handler;
        
        CorsHandler(HttpHandler handler) {
            this.handler = handler;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Origin", "*");
            headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            handler.handle(exchange);
        }
    }
    
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            if (path.equals("/") || path.equals("/index.html")) {
                // Serve HTML file
                String html = readFile("index.html");
                if (html == null) {
                    html = getDefaultHtml();
                }
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                sendResponse(exchange, html, 200);
            } else if (path.endsWith(".css")) {
                String css = readFile(path.substring(1));
                if (css == null) css = "";
                exchange.getResponseHeaders().set("Content-Type", "text/css");
                sendResponse(exchange, css, 200);
            } else if (path.endsWith(".js")) {
                String js = readFile(path.substring(1));
                if (js == null) js = "";
                exchange.getResponseHeaders().set("Content-Type", "application/javascript");
                sendResponse(exchange, js, 200);
            } else {
                sendResponse(exchange, "404 Not Found", 404);
            }
        }
        
        private String readFile(String filename) {
            try {
                File file = new File(filename);
                if (!file.exists()) return null;
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    content.append(line).append("\n");
                }
                br.close();
                return content.toString();
            } catch (IOException e) {
                return null;
            }
        }
    }
    
    static class BooksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String response = "";
            int statusCode = 200;
            
            try {
                if ("GET".equals(method)) {
                    List<Map<String, Object>> bookMaps = books.stream()
                        .map(Book::toMap)
                        .collect(Collectors.toList());
                    response = toJson(bookMaps);
                    
                } else if ("POST".equals(method)) {
                    Map<String, String> body = parseBody(exchange);
                    Book book = new Book(
                        body.get("id"),
                        body.get("title"),
                        body.get("author"),
                        body.get("genre")
                    );
                    
                    boolean exists = books.stream().anyMatch(b -> b.id.equals(book.id));
                    if (!exists) {
                        books.add(book);
                        response = toJson(Map.of("success", true, "message", "Book added successfully"));
                    } else {
                        response = toJson(Map.of("success", false, "message", "Book ID already exists"));
                        statusCode = 400;
                    }
                }
            } catch (Exception e) {
                response = toJson(Map.of("error", e.getMessage()));
                statusCode = 500;
            }
            
            sendResponse(exchange, response, statusCode);
        }
    }
    
    static class IssueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, toJson(Map.of("error", "Method not allowed")), 405);
                return;
            }
            
            Map<String, String> body = parseBody(exchange);
            String bookId = body.get("bookId");
            String userName = body.get("userName");
            
            Optional<Book> bookOpt = books.stream().filter(b -> b.id.equals(bookId)).findFirst();
            
            if (bookOpt.isEmpty()) {
                sendResponse(exchange, toJson(Map.of("success", false, "message", "Book not found")), 404);
                return;
            }
            
            Book book = bookOpt.get();
            if (!book.available) {
                sendResponse(exchange, toJson(Map.of("success", false, "message", "Book already issued to " + book.borrower)), 400);
                return;
            }
            
            book.issueBook(userName);
            
            // Register user if new
            Optional<User> userOpt = users.stream().filter(u -> u.name.equals(userName)).findFirst();
            if (userOpt.isEmpty()) {
                String userId = "U" + String.format("%03d", users.size() + 1);
                User newUser = new User(userId, userName, "", "");
                newUser.borrowedBooks.add(bookId);
                users.add(newUser);
            } else {
                userOpt.get().borrowedBooks.add(bookId);
            }
            
            sendResponse(exchange, toJson(Map.of(
                "success", true,
                "message", "Book issued successfully",
                "dueDate", book.dueDate
            )), 200);
        }
    }
    
    static class ReturnHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, toJson(Map.of("error", "Method not allowed")), 405);
                return;
            }
            
            Map<String, String> body = parseBody(exchange);
            String bookId = body.get("bookId");
            
            Optional<Book> bookOpt = books.stream().filter(b -> b.id.equals(bookId)).findFirst();
            
            if (bookOpt.isEmpty()) {
                sendResponse(exchange, toJson(Map.of("success", false, "message", "Book not found")), 404);
                return;
            }
            
            Book book = bookOpt.get();
            if (book.available) {
                sendResponse(exchange, toJson(Map.of("success", false, "message", "Book is not issued")), 400);
                return;
            }
            
            long overdueDays = book.getOverdueDays();
            long fine = overdueDays * 5;
            
            // Remove from user's borrowed list
            Optional<User> userOpt = users.stream().filter(u -> u.name.equals(book.borrower)).findFirst();
            if (userOpt.isPresent()) {
                userOpt.get().borrowedBooks.remove(bookId);
            }
            
            book.returnBook();
            
            sendResponse(exchange, toJson(Map.of(
                "success", true,
                "message", "Book returned successfully",
                "overdueDays", overdueDays,
                "fine", fine
            )), 200);
        }
    }
    
    static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String term = params.getOrDefault("term", "").toLowerCase();
            String type = params.getOrDefault("type", "title");
            
            List<Book> results = books.stream()
                .filter(book -> {
                    switch (type) {
                        case "title": return book.title.toLowerCase().contains(term);
                        case "author": return book.author.toLowerCase().contains(term);
                        case "genre": return book.genre.toLowerCase().contains(term);
                        default: return false;
                    }
                })
                .collect(Collectors.toList());
            
            sendResponse(exchange, toJson(results.stream().map(Book::toMap).collect(Collectors.toList())), 200);
        }
    }
    
    static class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("GET".equals(method)) {
                List<Map<String, Object>> userMaps = users.stream()
                    .map(User::toMap)
                    .collect(Collectors.toList());
                sendResponse(exchange, toJson(userMaps), 200);
            } else if ("POST".equals(method)) {
                Map<String, String> body = parseBody(exchange);
                String userId = "U" + String.format("%03d", users.size() + 1);
                User user = new User(userId, body.get("name"), body.get("email"), body.get("phone"));
                users.add(user);
                sendResponse(exchange, toJson(Map.of("success", true, "userId", userId)), 200);
            }
        }
    }
    
    static class StatisticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalBooks", books.size());
            stats.put("availableBooks", books.stream().filter(b -> b.available).count());
            stats.put("issuedBooks", books.stream().filter(b -> !b.available).count());
            stats.put("overdueBooks", books.stream().filter(Book::isOverdue).count());
            stats.put("totalUsers", users.size());
            stats.put("totalIssues", books.stream().mapToInt(b -> b.timesIssued).sum());
            
            if (books.size() > 0) {
                stats.put("availabilityRate", (books.stream().filter(b -> b.available).count() * 100.0) / books.size());
            } else {
                stats.put("availabilityRate", 0);
            }
            
            sendResponse(exchange, toJson(stats), 200);
        }
    }
    
    static class OverdueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Book> overdue = books.stream()
                .filter(Book::isOverdue)
                .collect(Collectors.toList());
            
            sendResponse(exchange, toJson(overdue.stream().map(Book::toMap).collect(Collectors.toList())), 200);
        }
    }
    
    //  UTILITY METHODS
    
    static String toJson(Object obj) {
        if (obj == null) return "null";
        
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(toJson(entry.getValue()));
                i++;
            }
            sb.append("}");
            return sb.toString();
        } else if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        } else if (obj instanceof Number) {
            return obj.toString();
        } else if (obj instanceof Boolean) {
            return obj.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }
    
    static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    static Map<String, String> parseBody(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            body.append(line);
        }
        return parseQueryParams(body.toString());
    }
    
    static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }
    
    static void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
    
    static String getDefaultHtml() {
        return "<!DOCTYPE html><html><head><title>Library System</title></head><body><h1>Library Management System</h1><p>Please ensure index.html file is in the same directory.</p></body></html>";
    }
    
    // ========== MAIN METHOD ==========
    public static void main(String[] args) throws IOException {
        initializeData();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // Serve static files
        server.createContext("/", new StaticFileHandler());
        
        // API endpoints
        server.createContext("/api/books", new CorsHandler(new BooksHandler()));
        server.createContext("/api/issue", new CorsHandler(new IssueHandler()));
        server.createContext("/api/return", new CorsHandler(new ReturnHandler()));
        server.createContext("/api/search", new CorsHandler(new SearchHandler()));
        server.createContext("/api/users", new CorsHandler(new UsersHandler()));
        server.createContext("/api/statistics", new CorsHandler(new StatisticsHandler()));
        server.createContext("/api/overdue", new CorsHandler(new OverdueHandler()));
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("\n==========================================");
        System.out.println("📚 LIBRARY MANAGEMENT SYSTEM");
        System.out.println("==========================================");
        System.out.println("✅ Server started successfully!");
        System.out.println("🌐 Open in browser: http://localhost:8080");
        System.out.println("⏹️  Press Ctrl+C to stop the server");
        System.out.println("==========================================\n");
    }
}