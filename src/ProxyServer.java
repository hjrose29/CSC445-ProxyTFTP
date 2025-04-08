// ProxyServer.java
import java.io.*;
import java.net.*;
import java.util.*;

public class ProxyServer {
    private static final int PORT = 8080;
    private static final int CACHE_SIZE = 1; // Modify for more caching

    private static Map<String, byte[]> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Proxy Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ProxyHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ProxyHandler implements Runnable {
        private Socket clientSocket;

        public ProxyHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                String url = in.readLine();
                System.out.println("Received request for: " + url);

                byte[] data;
                if (cache.containsKey(url)) {
                    System.out.println("Cache hit for: " + url);
                    data = cache.get(url);
                } else {
                    System.out.println("Fetching from server: " + url);
                    data = fetchFromServer(url);
                    if (data != null) {
                        cache.put(url, data);
                    }
                }

                if (data != null) {
                    out.write(data);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private byte[] fetchFromServer(String url) {
            try {
                URL targetUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
                connection.setRequestMethod("GET");

                try (InputStream inputStream = connection.getInputStream()) {
                    return inputStream.readAllBytes();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
