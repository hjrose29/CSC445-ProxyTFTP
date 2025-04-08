import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Client {
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 8080;

    public static void main(String[] args) {
        String[] urls = {
            "https://cdn.britannica.com/55/2155-050-604F5A4A/lion.jpg?w=300"
        };

        for (String url : urls) {
            try (
                Socket socket = new Socket(PROXY_HOST, PROXY_PORT);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())
            ) {
                // Send URL (first send length, then the URL bytes)
                byte[] urlBytes = url.getBytes();
                out.writeInt(urlBytes.length);
                out.write(urlBytes);

                List<byte[]> receivedChunks = new ArrayList<>();
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
                        receivedChunks.add(data);
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
                for (byte[] chunk : receivedChunks) {
                    result.write(chunk);
                }
                byte[] completeFile = result.toByteArray();
                System.out.println("Received file: " + completeFile.length + " bytes");

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
    
}
