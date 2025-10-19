package Controlador;

import Modelo.Canal;
import Modelo.GestorDeCanales;
import Modelo.Packet;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.layout.Pane;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class Receptor {
    @FXML Slider sliderCanal;
    @FXML Pane anchorPane;
    @FXML Pane animationPane;

    @FXML private Slider sliderDescifrado;
    @FXML private ProgressBar barraDeProgreso;
    @FXML private Label lblMensajeRecibido;
    @FXML private Label lblEstado;

    private Canal canalActual;

    // --- Estado de Recepción ---
    private int totalPaquetesEsperados = 0;
    private int proximoPaqueteEsperado = 0;
    private final Map<Integer, Packet> bufferRecepcion = new HashMap<>();
    private final Map<Integer, byte[]> payloadOrdenado = new TreeMap<>();

    @FXML
    public void initialize() {
        barraDeProgreso.setStyle("-fx-progress-color: #28a745;");
        sliderCanal.valueProperty().addListener((obs, v, n) -> sintonizarCanal(n.intValue()));
        sintonizarCanal((int) sliderCanal.getValue());
        // Inicializa la UI
        resetearEstadoRecepcion();
    }

    private void sintonizarCanal(int id) {
        if (canalActual != null) {
            canalActual.desconectarReceptor();
        }
        canalActual = GestorDeCanales.getInstance().getCanal(id);
        canalActual.conectarReceptor(this, animationPane);
        System.out.println("Receptor sintonizado en Canal " + id);

        resetearEstadoRecepcion();
    }

    private void resetearEstadoRecepcion() {
        totalPaquetesEsperados = 0;
        proximoPaqueteEsperado = 0;
        bufferRecepcion.clear();
        payloadOrdenado.clear();
        if (barraDeProgreso != null) barraDeProgreso.setProgress(0.0);
        if (lblMensajeRecibido != null) lblMensajeRecibido.setText("---");
        if (lblEstado != null) lblEstado.setText("Esperando...");
    }

    /**
     * MÉTODO PRINCIPAL DE RECEPCIÓN
     * Llamado por la clase Canal.
     */
    public void recibirPaquete(Packet paquete) {
        if (paquete.isAck()) return;

        if (paquete.isHeader()) {
            resetearEstadoRecepcion();
            totalPaquetesEsperados = paquete.getTotalPacketsFromHeader();
            if (lblEstado != null) lblEstado.setText("Recibiendo trama (0/" + totalPaquetesEsperados + ")");
            enviarAck(paquete.getSequenceNumber()); // Enviar ACK para -1
            return;
        }

        if (totalPaquetesEsperados == 0) return;

        int seq = paquete.getSequenceNumber();
        enviarAck(seq);

        if (seq < proximoPaqueteEsperado || payloadOrdenado.containsKey(seq)) {
            // Duplicado, ignorar
        }
        else if (seq == proximoPaqueteEsperado) {
            payloadOrdenado.put(seq, paquete.getPayload());
            proximoPaqueteEsperado++;
            revisarBuffer();
        }
        else { // seq > proximoPaqueteEsperado
            // Fuera de orden
            bufferRecepcion.put(seq, paquete);
        }

        actualizarProgreso();
    }

    private void revisarBuffer() {
        while (bufferRecepcion.containsKey(proximoPaqueteEsperado)) {
            Packet paqueteDelBuffer = bufferRecepcion.remove(proximoPaqueteEsperado);
            payloadOrdenado.put(paqueteDelBuffer.getSequenceNumber(), paqueteDelBuffer.getPayload());
            proximoPaqueteEsperado++;
        }

        if (payloadOrdenado.size() == totalPaquetesEsperados) {
            ensamblarMensajeFinal();
        }
    }

    private void ensamblarMensajeFinal() {
        if (lblEstado != null) lblEstado.setText("Trama completa. Ensamblando...");

        int tamanoTotal = payloadOrdenado.values().stream().mapToInt(b -> b.length).sum();
        java.nio.ByteBuffer bufferFinal = java.nio.ByteBuffer.allocate(tamanoTotal);
        for (byte[] payload : payloadOrdenado.values()) {
            bufferFinal.put(payload);
        }

        String mensajeCifrado = new String(bufferFinal.array(), StandardCharsets.UTF_8).trim();

        int claveDescifrado = (int) sliderDescifrado.getValue();

        // Descifra el mensaje
        String mensajeFinal = descifrarCesar(mensajeCifrado, claveDescifrado);

        lblMensajeRecibido.setText(mensajeFinal);
        if (lblEstado != null) lblEstado.setText("¡Mensaje Recibido!");
    }

    private void actualizarProgreso() {
        if (totalPaquetesEsperados == 0) return;
        double progreso = (double) payloadOrdenado.size() / totalPaquetesEsperados;
        barraDeProgreso.setProgress(progreso);
        if (lblEstado != null) lblEstado.setText("Recibiendo trama (" + payloadOrdenado.size() + "/" + totalPaquetesEsperados + ")");
    }

    private void enviarAck(int seqNum) {
        if (canalActual != null) {
            // Usa el constructor de ACK (isAck = true)
            // (Asegúrate que tu constructor de Packet sea 'public Packet(int seq, boolean isAck)')
            canalActual.enviarPaquete(new Packet(seqNum, true));
        }
    }

    /**
     * Descifra un texto César. Es la misma función que cifrar,
     * pero con un desplazamiento negativo.
     */
    private String descifrarCesar(String textoCifrado, int desplazamiento) {
        // Descifrar es lo mismo que cifrar con la clave opuesta
        int desplazamientoOpuesto = -desplazamiento;

        StringBuilder textoPlano = new StringBuilder();
        final int asciiInicio = 32;
        final int asciiFinal = 126;
        final int rango = asciiFinal - asciiInicio + 1; // 95 caracteres

        for (char c : textoCifrado.toCharArray()) {
            if (c >= asciiInicio && c <= asciiFinal) {
                int indiceActual = c - asciiInicio;
                int indiceNuevo = (indiceActual + desplazamientoOpuesto % rango + rango) % rango;
                char nuevoCaracter = (char) (asciiInicio + indiceNuevo);
                textoPlano.append(nuevoCaracter);
            } else {
                textoPlano.append(c); // Añadir caracteres no cifrables (como padding nulo)
            }
        }
        return textoPlano.toString();
    }
}