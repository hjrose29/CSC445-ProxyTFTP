// TftpPacket.java
import java.nio.ByteBuffer;

public class TftpPacket {
    public static final short OPCODE_DATA = 3;
    public static final short OPCODE_ACK = 4;
    public static final short OPCODE_END = 5;

    private short opcode;
    private int sequenceNumber;
    private byte[] data;

    public TftpPacket(short opcode, int sequenceNumber, byte[] data) {
        this.opcode = opcode;
        this.sequenceNumber = sequenceNumber;
        this.data = data;
    }

    public short getOpcode() {
        return opcode;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public byte[] getData() {
        return data;
    }

    // Serialize the packet to bytes for sending
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + 4 + data.length); // now includes 4-byte data length
        buffer.putShort(opcode);
        buffer.putInt(sequenceNumber);
        buffer.putInt(data.length);  // include data length
        buffer.put(data);
        return buffer.array();
    }
    

    // Deserialize from bytes to a TftpPacket object
    public static TftpPacket fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        short opcode = buffer.getShort();
        int seq = buffer.getInt();
        int len = buffer.getInt();
    
        byte[] data = new byte[len];
        buffer.get(data);
    
        return new TftpPacket(opcode, seq, data);
    }
    
}
