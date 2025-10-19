package Modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays; // Importa Arrays

public class Trama {
    private final List<Packet> packets;

    public Trama(byte[] cargaUtilTotal, int tanañoPayload) {
        this.packets = new ArrayList<>();
        segmentar(cargaUtilTotal, tanañoPayload);
    }

    private void segmentar(byte[] cargaUtilTotal, int tamañoPayload) {
        int numPaquetes = (int) Math.ceil((double) cargaUtilTotal.length / tamañoPayload);

        int tamañoTotalConPadding = numPaquetes * tamañoPayload;

        byte[] datosConPadding = new byte[tamañoTotalConPadding];

        System.arraycopy(cargaUtilTotal, 0, datosConPadding, 0, cargaUtilTotal.length);

        int sequence = 0;
        for (int i = 0; i < datosConPadding.length; i += tamañoPayload) {

            byte[] chunk = Arrays.copyOfRange(datosConPadding, i, i + tamañoPayload);

            packets.add(new Packet(sequence, chunk));
            sequence++;
        }
    }

    public List<Packet> getPackets() {
        return packets;
    }
}