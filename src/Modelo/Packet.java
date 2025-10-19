package Modelo;

public class Packet {
    private int sequenceNumber;
    private byte[] payload;
    private boolean ack;

    public Packet(int sequenceNumber, byte[] payload) {
        this.sequenceNumber = sequenceNumber;
        this.payload = payload;
        this.ack = false;
    }

    // Constructor para paquetes de ACK
    public Packet(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
        this.ack = true;
        this.payload = null; // Los ACKs no necesitan datos
    }

    public int getSequenceNumber() { return sequenceNumber; }
    public byte[] getPayload() { return payload; }
    public boolean isAck() { return ack; }
}
