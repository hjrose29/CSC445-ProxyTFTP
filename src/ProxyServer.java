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
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

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
                    int sequence = 0;
                    int totalPackets = (int) Math.ceil(data.length / (double) blockSize);
                    int windowSize = 8;
                    int base = 0;
                    int nextSeqNum = 0;

                    Map<Integer, TftpPacket> sentPackets = new HashMap<>();
                    Set<Integer> acked = new HashSet<>();
                    //vars for timeout
                    Map<Integer, Long> sendTimes = new HashMap<>(); 
                    int timeoutMillis = 1000; 

                    while (base < totalPackets) {

                        // fill window
                        while (nextSeqNum < base + windowSize && nextSeqNum < totalPackets) {
                            int start = nextSeqNum * blockSize;
                            int end = Math.min(start + blockSize, data.length);
                            byte[] chunk = Arrays.copyOfRange(data, start, end);
        
                            TftpPacket packet = new TftpPacket(TftpPacket.OPCODE_DATA, nextSeqNum, chunk);
                            out.write(packet.toBytes());
                            out.flush();
                            sentPackets.put(nextSeqNum, packet);
                            sendTimes.put(nextSeqNum, System.currentTimeMillis());
        
                            System.out.println("Sent packet: " + nextSeqNum);
                            nextSeqNum++;
                        }
        
                        // Wait for ACK
                        byte[] ackBytes = new byte[10];
                        if (Client.readFully(in, ackBytes)) {
                            TftpPacket ack = TftpPacket.fromBytes(ackBytes);
                            if (ack.getOpcode() == TftpPacket.OPCODE_ACK) {
                                int ackSeq = ack.getSequenceNumber();
                                System.out.println("ACK received for seq: " + ackSeq);
        
                                if (ackSeq >= base) {
                                    base = ackSeq + 1;
                                    acked.add(ackSeq);
                                }
                                // Check for any unACKed packets that timed out
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
                                // Resend timed-out packets
                                for (int seq : toResend) {
                                    TftpPacket pkt = sentPackets.get(seq);
                                    System.out.println("Timeout: Resending packet " + seq);
                                    out.write(pkt.toBytes());
                                    out.flush();
                                    sendTimes.put(seq, System.currentTimeMillis()); // reset timer
                                }

                            }
                        }
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
    }
}
