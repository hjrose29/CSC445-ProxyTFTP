import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class Client {
    private static String PROXY_HOST = "pi.cs.oswego.edu";
    private static final int PROXY_PORT = 27690;

    public static void main(String[] args) {
        String[] urls = {
            "https://cdn.britannica.com/55/2155-050-604F5A4A/lion.jpg?w=900"
        };

        for (String arg : args) {
            if (arg.startsWith("--host=")) {
                PROXY_HOST = arg.substring(7);
            } 
        }

        System.out.println("Running on: " + PROXY_HOST);
        for (String url : urls) {
            try (
                Socket socket = new Socket(PROXY_HOST, PROXY_PORT);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())
            ) {


                //Encryption
                Random random = new Random();
                int senderId = 1111; 
                int clientNonce = random.nextInt();

                out.writeInt(senderId);
                out.writeInt(clientNonce);
                out.flush();

                int sharedKey = senderId ^ clientNonce;
                System.out.println("Key: " + sharedKey);
                // Send URL (first send length, then the URL bytes)
                byte[] urlBytes = url.getBytes();
                out.writeInt(urlBytes.length);
                out.write(urlBytes);

                Map<Integer, byte[]> receivedChunks = new TreeMap<>();
                InputStream rawIn = socket.getInputStream();

                while (true) {
                    // Step 1: Read header
                    byte[] header = new byte[10];
                    if (!readFully(rawIn, header)) {
                        System.out.println("Failed to read header - stream closed?");
                        break;
                    }
                
                    ByteBuffer headerBuf = ByteBuffer.wrap(header);
                    short opcode = headerBuf.getShort();
                    int sequence = headerBuf.getInt();
                    int length = headerBuf.getInt();
                
                    if (opcode == TftpPacket.OPCODE_DATA) {
                        byte[] data = new byte[length];
                        if (!readFully(rawIn, data)) {
                            System.out.println("Failed to read data chunk of length: " + length);
                            break;
                        }
                        byte[] decrypted = xorBytes(data, sharedKey);
                        receivedChunks.put(sequence, decrypted);
                        TftpPacket ack = new TftpPacket(TftpPacket.OPCODE_ACK, sequence, new byte[0]);
                        out.write(ack.toBytes());
                        out.flush();
                    }
                    else if (opcode == TftpPacket.OPCODE_END) {
                        System.out.println("Received END of transmission.");
                        break;
                    } else {
                        System.out.println("Unknown opcode: " + opcode);
                        break;
                    }
                }

                // Reassemble and save file
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                for (byte[] chunk : receivedChunks.values()) {
                    result.write(chunk);
                }
                byte[] completeFile = result.toByteArray();
                System.out.println("[RESULT] Received file: " + completeFile.length + " bytes");

                String saveDir = "../static/";
                String fileName = saveDir + "image_" + System.currentTimeMillis() + ".jpg";
                try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                    fileOut.write(completeFile);
                    System.out.println("Saved image as: " + fileName);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public static boolean readFully(InputStream in, byte[] buffer) throws IOException {
        int bytesRead = 0;
        while (bytesRead < buffer.length) {
            int result = in.read(buffer, bytesRead, buffer.length - bytesRead);
            if (result == -1) return false;
            bytesRead += result;
        }
        return true;
    }
    
    private static byte[] xorBytes(byte[] data, int key) {
        byte[] result = new byte[data.length];
        byte[] keyBytes = ByteBuffer.allocate(4).putInt(key).array();
    
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ keyBytes[i % 4]);
        }
    
        return result;
    }
    
}
