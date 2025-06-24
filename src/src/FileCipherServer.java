import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FileCipherServer {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/encrypt", FileCipherServer::handleEncrypt);
        server.createContext("/decrypt", FileCipherServer::handleDecrypt);
        server.setExecutor(null);
        System.out.println("Server started on http://localhost:8080");
        server.start();
    }

    private static void handleEncrypt(HttpExchange exchange) throws IOException {
        handleCipher(exchange, true);
    }

    private static void handleDecrypt(HttpExchange exchange) throws IOException {
        handleCipher(exchange, false);
    }

    private static void handleCipher(HttpExchange exchange, boolean encrypt) throws IOException {
        try {
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                Headers headers = exchange.getResponseHeaders();
                headers.add("Access-Control-Allow-Origin", "*");
                headers.add("Access-Control-Allow-Methods", "POST, OPTIONS");
                headers.add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add("Access-Control-Allow-Origin", "*");
            responseHeaders.add("Access-Control-Allow-Methods", "POST, OPTIONS");
            responseHeaders.add("Access-Control-Allow-Headers", "Content-Type");

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            System.out.println("Content-Type: " + contentType);
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Extract boundary string exactly as in header, without adding extra --
            String boundary = contentType.split("boundary=")[1];
            System.out.println("Boundary: " + boundary);

            MultipartFormData form = MultipartFormData.parse(exchange.getRequestBody(), boundary);
            byte[] fileBytes = form.getFile("file");
            String keyMatrixStr = form.getField("keyMatrix");

            System.out.println("Received keyMatrix string: " + keyMatrixStr);
            if (fileBytes == null || keyMatrixStr == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            int[][] keyMatrix = parseMatrix(keyMatrixStr);
            System.out.println("Parsed key matrix size: " + keyMatrix.length);

            String fileText = new String(fileBytes, StandardCharsets.UTF_8).replaceAll("[^A-Za-z]", "").toUpperCase();
            System.out.println("File text after cleanup: '" + fileText + "'");

            int matrixSize = keyMatrix.length;
            while (fileText.length() % matrixSize != 0) {
                fileText += "X";
            }

            String result;
            if (encrypt) {
                result = App.encrypt(fileText, keyMatrix);
            } else {
                int[][] invKey = App.inverseMatrix(keyMatrix);
                result = App.decrypt(fileText, invKey);
            }

            byte[] resultBytes = result.getBytes(StandardCharsets.UTF_8);

            responseHeaders.add("Content-Type", "application/octet-stream");
            responseHeaders.add("Content-Disposition", "attachment; filename=output.txt");

            exchange.sendResponseHeaders(200, resultBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(resultBytes);
            os.close();

        } catch (Exception e) {
            e.printStackTrace();
            String errMsg = "Server error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            byte[] errBytes = errMsg.getBytes(StandardCharsets.UTF_8);
            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add("Content-Type", "text/plain; charset=utf-8");
            responseHeaders.add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(500, errBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(errBytes);
            os.close();
        }
    }

    // Parse matrix from string like "2,4,5;9,2,1;3,17,7"
    private static int[][] parseMatrix(String str) {
        String[] rows = str.trim().split(";");
        int n = rows.length;
        int[][] matrix = new int[n][n];
        for (int i = 0; i < n; i++) {
            String[] vals = rows[i].split(",");
            for (int j = 0; j < n; j++) {
                matrix[i][j] = Integer.parseInt(vals[j].trim());
            }
        }
        return matrix;
    }

    // Minimal multipart/form-data parser with fixed boundary handling
    static class MultipartFormData {
        private final Map<String, String> fields = new HashMap<>();
        private final Map<String, byte[]> files = new HashMap<>();

        public String getField(String name) {
            return fields.get(name);
        }

        public byte[] getFile(String name) {
            return files.get(name);
        }

        public static MultipartFormData parse(InputStream is, String boundary) throws IOException {
            MultipartFormData form = new MultipartFormData();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.ISO_8859_1));
            String line;
            String delimiter = "--" + boundary;          // prepend -- here, since body boundaries start with --
            String delimiterEnd = delimiter + "--";

            while ((line = reader.readLine()) != null) {
                if (line.equals(delimiter)) {
                    String contentDisp = reader.readLine();
                    if (contentDisp == null) break;

                    String name = null, filename = null;
                    if (contentDisp.contains("name=\"")) {
                        int start = contentDisp.indexOf("name=\"") + 6;
                        int end = contentDisp.indexOf("\"", start);
                        name = contentDisp.substring(start, end);
                    }
                    if (contentDisp.contains("filename=\"")) {
                        int start = contentDisp.indexOf("filename=\"") + 10;
                        int end = contentDisp.indexOf("\"", start);
                        filename = contentDisp.substring(start, end);
                    }

                    // Skip Content-Type if exists
                    String nextLine = reader.readLine();
                    if (nextLine != null && nextLine.trim().isEmpty()) {
                        // empty line - proceed
                    } else if (nextLine != null && nextLine.toLowerCase().startsWith("content-type:")) {
                        reader.readLine(); // skip empty line after Content-Type header
                    } else {
                        // no Content-Type header, the line read is empty line already or next
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    while ((line = reader.readLine()) != null && !line.equals(delimiter) && !line.equals(delimiterEnd)) {
                        baos.write(line.getBytes(StandardCharsets.ISO_8859_1));
                        baos.write('\n');
                    }

                    byte[] data = baos.toByteArray();
                    if (filename != null) {
                        form.files.put(name, data);
                    } else {
                        form.fields.put(name, new String(data, StandardCharsets.UTF_8).trim());
                    }

                    if (line == null || line.equals(delimiterEnd)) break;

                    // line now is delimiter or delimiterEnd, loop continues
                }
            }
            return form;
        }
    }
}
