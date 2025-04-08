// Client.java
import java.io.*;
import java.net.*;

public class Client {
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 8080;

    public static void main(String[] args) {

        String[] urls = {"https://cdn.britannica.com/55/2155-050-604F5A4A/lion.jpg?w=300","https://cdn.britannica.com/55/2155-050-604F5A4A/lion.jpg?w=300"};

        for(String url: urls)
        {
            try (Socket socket = new Socket(PROXY_HOST, PROXY_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            InputStream in = socket.getInputStream()) {
        
                out.println(url);
                byte[] data = in.readAllBytes();
                System.out.println("Received " + data.length + " bytes from proxy");

                // Save to file (e.g., image1.jpg, image2.jpg, etc.)
                String saveDir = "../static/";
                String fileName = saveDir + "image_" + System.currentTimeMillis() + ".jpg"; 
                try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                    fileOut.write(data);
                    System.out.println("Saved image as: " + fileName);
                }

        
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
