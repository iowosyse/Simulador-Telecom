package Modelo;

public class Packet {
    private int sequenceNumber;
    private byte[] payload;
    private boolean ack;
    private boolean header;

    // Constructor para paquetes de DATOS
    public Packet(int sequenceNumber, byte[] payload) {
        this.sequenceNumber = sequenceNumber;
        this.payload = payload;
        this.ack = false;
        this.header = false; // <-- Es un paquete de datos normal
    }

    // Constructor para paquetes de ACK
    public Packet(int sequenceNumber, boolean isAck) {
        this.sequenceNumber = sequenceNumber;
        this.ack = isAck; // Debería ser 'true'
        this.header = false; // <-- Un ACK no es un header
        this.payload = null;
    }

    // Constructor para el paquete de HEADER
    public Packet(int totalPackets) {
        this.sequenceNumber = -1; // Usamos un seq especial (-1) para el header
        this.ack = false;
        this.header = true; // <-- ¡ESTE ES EL HEADER!

        // Convertimos el número total a bytes para el payload
        this.payload = java.nio.ByteBuffer.allocate(4).putInt(totalPackets).array();
    }

    // --- Getters ---
    public int getSequenceNumber() { return sequenceNumber; }
    public byte[] getPayload() { return payload; }
    public boolean isAck() { return ack; }
    public boolean isHeader() { return header; } // <-- NUEVO GETTER

    /**
     * Helper para decodificar el payload si este paquete es un header.
     * @return El número total de paquetes que anuncia este header.
     */
    public int getTotalPacketsFromHeader() {
        if (!this.header) {
            return 0; // O lanzar una excepción
        }
        // Convierte los 4 bytes del payload de vuelta a un 'int'
        return java.nio.ByteBuffer.wrap(this.payload).getInt();
    }
}