package Modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class Trama {
    private final List<Packet> packets;

    public Trama(byte[] cargaUtilTotal, int tamanoPayload) {
        this.packets = new ArrayList<>();
        segmentar(cargaUtilTotal, tamanoPayload);
    }

    private void segmentar(byte[] cargaUtilTotal, int tamanoPayload) {
        int numPaquetesDatos = (int) Math.ceil((double) cargaUtilTotal.length / tamanoPayload);
        Packet headerPacket = new Packet(numPaquetesDatos);
        this.packets.add(headerPacket);
        // ------------------------------------

        int tamanoTotalConPadding = numPaquetesDatos * tamanoPayload;
        byte[] datosConPadding = new byte[tamanoTotalConPadding];
        System.arraycopy(cargaUtilTotal, 0, datosConPadding, 0, cargaUtilTotal.length);

        int sequence = 0;
        for (int i = 0; i < datosConPadding.length; i += tamanoPayload) {
            byte[] chunk = Arrays.copyOfRange(datosConPadding, i, i + tamanoPayload);
            packets.add(new Packet(sequence, chunk)); // seq 0, 1, 2...
            sequence++;
        }
    }

    public List<Packet> getPackets() {
        return packets;
    }
}