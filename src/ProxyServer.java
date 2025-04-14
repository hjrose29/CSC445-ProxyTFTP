// ProxyServer.java
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class ProxyServer {
    private static final int PORT = 27690;
    private static final int CACHE_SIZE = 1; // Modify for more caching
    private static double dropRate = 0;
    private static int windowSize = 1;


    private static Map<String, byte[]> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public static void main(String[] args) {

        for (String arg : args) {
            if (arg.startsWith("--drop=")) {
                dropRate = Double.parseDouble(arg.substring(7));
            } else if (arg.startsWith("--window=")) {
                windowSize = Integer.parseInt(arg.substring(9));
            }
        }
        if(dropRate !=0){
            System.out.println("Simulating packet drop rate: " + (dropRate * 100) + "%");
        }
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Proxy Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ProxyHandler(clientSocket, dropRate, windowSize)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ProxyHandler implements Runnable {
        private Socket clientSocket;
        private double dropRate;
        private Random random = new Random();
        private int windowSize;
        public ProxyHandler(Socket clientSocket, double dropRate, int windowSize) {
            this.clientSocket = clientSocket;
            this.dropRate = dropRate;
            this.windowSize = windowSize;
        }

        public void run() {
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {


                int senderId = in.readInt();
                int clientKey = in.readInt();
                int sharedKey = senderId ^ clientKey;

                System.out.println("Key: " + sharedKey);

                int urlLength = in.readInt();
                byte[] urlBytes = new byte[urlLength];
                in.readFully(urlBytes);
                String url = new String(urlBytes);
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
                    int blockSize = 512;
                    //int sequence = 0;
                    int totalPackets = (int) Math.ceil(data.length / (double) blockSize);
                    int base = 0;
                    int nextSeqNum = 0;

                    Map<Integer, TftpPacket> sentPackets = new HashMap<>();
                    Set<Integer> acked = new HashSet<>();
                    //vars for timeout
                    Map<Integer, Long> sendTimes = new HashMap<>(); 
                    int timeoutMillis = 1000; 

                    while (base < totalPackets) {

                        // Fill the window
                        while (nextSeqNum < base + windowSize && nextSeqNum < totalPackets) {
                            int start = nextSeqNum * blockSize;
                            int end = Math.min(start + blockSize, data.length);
                            byte[] chunk = Arrays.copyOfRange(data, start, end);                            
                            byte[] encryptedChunk = xorBytes(chunk, sharedKey);
                            TftpPacket packet = new TftpPacket(TftpPacket.OPCODE_DATA, nextSeqNum, encryptedChunk);
                    
                            if (random.nextDouble() >= dropRate) {
                                out.write(packet.toBytes());
                                out.flush();
                                System.out.println("Sent packet: " + nextSeqNum);
                            } else {
                                System.out.println("Oops packet dropped seq: " + nextSeqNum);
                            }
                    
                            sentPackets.put(nextSeqNum, packet);
                            sendTimes.put(nextSeqNum, System.currentTimeMillis());
                            nextSeqNum++;
                        }
                    
                        // Try to receive ACK
                        if (in.available() >= 10) {
                            byte[] ackBytes = new byte[10];
                            if (Client.readFully(in, ackBytes)) {
                                TftpPacket ack = TftpPacket.fromBytes(ackBytes);
                                if (ack.getOpcode() == TftpPacket.OPCODE_ACK) {
                                    int ackSeq = ack.getSequenceNumber();
                                    System.out.println("ACK received for seq: " + ackSeq);
                                    acked.add(ackSeq);

                                    // Slide base forward only if all packets before it are ACKed
                                    while (acked.contains(base)) {
                                        base++;
                                    }
                                }
                            }
                        }
                    
                        // Check for unACKed packets that timed out
                        long now = System.currentTimeMillis();
                        List<Integer> toResend = new ArrayList<>();
                    
                        for (Map.Entry<Integer, TftpPacket> entry : sentPackets.entrySet()) {
                            int seq = entry.getKey();
                            if (!acked.contains(seq)) {
                                long sentAt = sendTimes.get(seq);
                                if (now - sentAt > timeoutMillis) {
                                    toResend.add(seq);
                                }
                            }
                        }
                    
                        for (int seq : toResend) {
                            TftpPacket pkt = sentPackets.get(seq);
                            System.out.println("Timeout: Resending packet " + seq);
                            out.write(pkt.toBytes());
                            out.flush();
                            sendTimes.put(seq, System.currentTimeMillis());
                        }
                    
                        // ✅ Sleep to avoid tight loop
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                    }
                    
        
                    // ✅ Once all ACKs received, send END
                    TftpPacket endPacket = new TftpPacket(TftpPacket.OPCODE_END, -1, new byte[0]);
                    out.write(endPacket.toBytes());
                    out.flush();
        
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

        private static byte[] xorBytes(byte[] data, int key) {
            byte[] result = new byte[data.length];
            byte[] keyBytes = ByteBuffer.allocate(4).putInt(key).array();

            for (int i = 0; i < data.length; i++) {
                result[i] = (byte) (data[i] ^ keyBytes[i % 4]);
            }
            return result;
        }
    }
}
