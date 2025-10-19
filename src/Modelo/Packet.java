package Modelo;

import java.util.Arrays;

public class Packet {
    private int sequenceNumber;
    private byte[] payload;
    private boolean ack;
    private boolean header;
    private int checksum;

    // Constructor para paquetes de DATOS
    public Packet(int sequenceNumber, byte[] payload) {
        this.sequenceNumber = sequenceNumber;
        this.payload = Arrays.copyOf(payload, payload.length);
        this.ack = false;
        this.header = false;
        this.checksum = calcularChecksum(this.payload);
    }

    // Constructor para paquetes de ACK
    public Packet(int sequenceNumber, boolean isAck) {
        this.sequenceNumber = sequenceNumber;
        this.ack = isAck;
        this.header = false;
        this.payload = null;
        this.checksum = sequenceNumber;
    }

    // Constructor para el paquete de HEADER
    public Packet(int totalPackets) {
        this.sequenceNumber = -1;
        this.ack = false;
        this.header = true;
        this.payload = java.nio.ByteBuffer.allocate(4).putInt(totalPackets).array();
        this.checksum = calcularChecksum(this.payload);
    }

    /**
     * Crea una copia idéntica (clon) de otro paquete.
     */
    public Packet(Packet original) {
        this.sequenceNumber = original.sequenceNumber;
        this.ack = original.ack;
        this.header = original.header;
        this.checksum = original.checksum; // Copia la firma original

        // Copia el payload para que podamos corromperlo sin dañar el original
        if (original.payload != null) {
            this.payload = Arrays.copyOf(original.payload, original.payload.length);
        } else {
            this.payload = null;
        }
    }
    // ------------------------------------------

    /**
     * Calcula un checksum simple sumando todos los bytes.
     */
    private int calcularChecksum(byte[] data) {
        if (data == null) return 0;
        int sum = 0;
        for (byte b : data) {
            sum += b;
        }
        return sum;
    }

    /**
     * Comprueba si el paquete está corrupto.
     */
    public boolean isCorrupt() {
        if (this.ack) {
            return this.checksum != this.sequenceNumber;
        } else {
            // Compara la firma original (checksum) con la firma del payload actual
            return this.checksum != calcularChecksum(this.payload);
        }
    }

    // --- Getters ---
    public int getSequenceNumber() { return sequenceNumber; }
    public byte[] getPayload() { return payload; }
    public boolean isAck() { return ack; }
    public boolean isHeader() { return header; }
    public int getChecksum() { return checksum; }
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