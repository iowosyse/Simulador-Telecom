package Controlador;

import Modelo.Canal;
import Modelo.GestorDeCanales;
import Modelo.Packet;
import Modelo.Trama;
import javafx.animation.PauseTransition;
import javafx.application.Platform; // <-- AÑADIDO
import javafx.fxml.FXML;
import javafx.scene.control.Alert; // <-- AÑADIDO
import javafx.scene.control.Alert.AlertType; // <-- AÑADIDO
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Emisor {
    @FXML Button enviarBtn;
    @FXML TextField txtMensaje;
    @FXML Slider sliderCanal;
    @FXML Slider sliderCifrado;

    private Canal canalActual;

    // --- Configuración de la Ventana ---
    private static final int TAMAÑO_VENTANA = 5;
    private static final Duration TIMEOUT_DURACION = Duration.millis(1100);

    // --- Estado de la Transmisión ---
    private List<Packet> tramaPendiente;
    private int ventanaBase = 0;
    private int proximoSeqNum = 0;

    private boolean handshakeCompletado = false;

    private final Map<Integer, PauseTransition> timersActivos = new HashMap<>();
    private final Set<Integer> acksRecibidosEnVentana = new HashSet<>();


    @FXML
    public void initialize() {
        sliderCanal.valueProperty().addListener((obs,
                                                 valorViejo,
                                                 valorNuevo)
                -> sintonizarCanal(valorNuevo.intValue()));
        sintonizarCanal((int) sliderCanal.getValue());
    }

    private void sintonizarCanal(int id) {
        if (canalActual != null) {
            canalActual.desconectarEmisor();
        }
        // Asumiendo que GestorDeCanales.getInstance() es tu método
        canalActual = GestorDeCanales.getInstance().getCanal(id);
        canalActual.conectarEmisor(this);
        System.out.println("Emisor sintonizado en Canal " + id);
    }

    /**
     * Inicia el proceso de envío de la trama.
     */
    public void enviarPressed() {
        String mensaje = cifrarCesar(txtMensaje.getText(), (int) sliderCifrado.getValue());
        if (mensaje.isEmpty()) return;

        byte[] cargaUtilTotal = mensaje.getBytes(StandardCharsets.UTF_8);

        int tamañoPayload = 10;
        Trama trama = new Trama(cargaUtilTotal, tamañoPayload);
        this.tramaPendiente = trama.getPackets();

        // Resetea todo el estado de la transmisión
        ventanaBase = 0;
        proximoSeqNum = 0;
        handshakeCompletado = false;
        abortarTransmision(); // Limpia timers y reactiva el botón
        enviarBtn.setDisable(true); // Desactívalo de nuevo para este envío

        // --- LÓGICA DE HANDSHAKE (MODIFICADA) ---
        if (tramaPendiente.isEmpty()) {
            enviarBtn.setDisable(false);
            return;
        }

        Packet headerPacket = tramaPendiente.get(0);

        System.out.println("EMISOR: Iniciando handshake. Enviando Header (seq=" + headerPacket.getSequenceNumber() + ")");
        if (canalActual != null) {

            // --- ¡COMPROBACIÓN! ---
            boolean enviadoConExito = canalActual.enviarPaquete(headerPacket);

            if (enviadoConExito) {
                // El canal aceptó el paquete, iniciar el timer
                iniciarTimerPara(headerPacket);
            } else {
                // ¡FALLO INMEDIATO! No hay receptor.
                mostrarAlertaError("Error de Envío", "No hay ningún receptor sintonizado en el Canal " + canalActual.getFrecuencia());
                abortarTransmision(); // Resetea el Emisor
            }
        }
    }

    /**
     * Envía todos los paquetes nuevos que quepan en la ventana actual.
     */
    private void enviarVentana() {
        while (proximoSeqNum + 1 < tramaPendiente.size() &&
                proximoSeqNum < ventanaBase + TAMAÑO_VENTANA) {

            Packet paqueteAEnviar = tramaPendiente.get(proximoSeqNum + 1);

            System.out.println("EMISOR: Enviando paquete seq=" + paqueteAEnviar.getSequenceNumber());
            if (canalActual != null) {

                // --- ¡COMPROBACIÓN! ---
                boolean enviadoConExito = canalActual.enviarPaquete(paqueteAEnviar);

                if (enviadoConExito) {
                    iniciarTimerPara(paqueteAEnviar);
                } else {
                    // El receptor debió desconectarse a mitad de la trama.
                    mostrarAlertaError("Error de Conexión", "Se perdió la conexión con el receptor en el Canal " + canalActual.getFrecuencia());
                    abortarTransmision();
                    break; // Salir del bucle 'while'
                }
            }
            proximoSeqNum++;
        }
    }

    /**
     * Inicia un temporizador individual (alarma) para un paquete específico.
     */
    private void iniciarTimerPara(Packet paquete) {
        int seq = paquete.getSequenceNumber();

        if (timersActivos.containsKey(seq)) {
            return;
        }

        PauseTransition timer = new PauseTransition(TIMEOUT_DURACION);

        timer.setOnFinished(e -> {
            System.out.println("EMISOR: ¡TIMEOUT! para seq=" + seq + ". Retransmitiendo...");
            timersActivos.remove(seq);

            if (canalActual != null) {

                // --- ¡COMPROBACIÓN! ---
                boolean retransmitidoConExito = canalActual.enviarPaquete(paquete);

                if (retransmitidoConExito) {
                    // Inicia una NUEVA alarma para este reintento
                    iniciarTimerPara(paquete);
                } else {
                    // El receptor se desconectó mientras esperábamos el ACK.
                    mostrarAlertaError("Error de Conexión", "Se perdió la conexión con el receptor en el Canal " + canalActual.getFrecuencia());
                    abortarTransmision();
                }
            }
        });

        timer.play();
        timersActivos.put(seq, timer);
    }

    /**
     * Método PÚBLICO que el CANAL llamará cuando un ACK llegue.
     */
    public void recibirAck(Packet ack) {
        if (!ack.isAck()) return;

        int seq = ack.getSequenceNumber();
        System.out.println("EMISOR: Recibido ACK para seq=" + seq);

        PauseTransition timer = timersActivos.get(seq);

        if (timer != null) {
            timer.stop();
            timersActivos.remove(seq);
        } else {
            System.out.println("EMISOR: ACK " + seq + " duplicado o inesperado.");
            if(seq >= 0 && !handshakeCompletado) return;
        }

        // CASO A: Es el ACK del Header (seq = -1)
        if (seq == -1 && !handshakeCompletado) {
            System.out.println("EMISOR: Handshake completado. Iniciando ráfaga de datos...");
            handshakeCompletado = true;
            enviarVentana();
        }
        // CASO B: Es un ACK de un paquete de DATOS (seq >= 0)
        else if (seq >= 0 && handshakeCompletado) {
            if (seq >= ventanaBase) {
                acksRecibidosEnVentana.add(seq);

                while (acksRecibidosEnVentana.contains(ventanaBase)) {
                    acksRecibidosEnVentana.remove(ventanaBase);
                    ventanaBase++;
                    System.out.println("EMISOR: Ventana deslizada a base=" + ventanaBase);
                }
                enviarVentana();
            }
        }

        // Comprobación de finalización
        int numPaquetesDatos = tramaPendiente.size() - 1;
        if (handshakeCompletado && ventanaBase == numPaquetesDatos) {
            System.out.println("EMISOR: Trama completa enviada y confirmada.");
            abortarTransmision(); // Limpia todo y reactiva el botón
            handshakeCompletado = false; // Resetea para la próxima trama
        }
    }

    // --- NUEVOS MÉTODOS HELPER ---

    /**
     * Muestra una ventana de diálogo de error al usuario.
     * Debe ejecutarse en el Hilo de Aplicación de JavaFX.
     */
    private void mostrarAlertaError(String titulo, String mensaje) {
        // Asegura que la alerta se muestre en el hilo de UI
        Platform.runLater(() -> {
            Alert alerta = new Alert(AlertType.ERROR);
            alerta.setTitle(titulo);
            alerta.setHeaderText(null);
            alerta.setContentText(mensaje);

            // Asigna la ventana "dueña" (la del Emisor) para centrar la alerta
            if (enviarBtn.getScene() != null) {
                alerta.initOwner(enviarBtn.getScene().getWindow());
            }

            alerta.showAndWait();
        });
    }

    /**
     * Detiene todos los timers, limpia el estado y reactiva el botón de envío.
     */
    private void abortarTransmision() {
        timersActivos.values().forEach(PauseTransition::stop);
        timersActivos.clear();
        acksRecibidosEnVentana.clear();
        handshakeCompletado = false;
        ventanaBase = 0;
        proximoSeqNum = 0;

        // Asegura que el botón se reactive en el hilo de UI
        Platform.runLater(() -> {
            enviarBtn.setDisable(false);
        });

        System.out.println("EMISOR: Transmisión abortada.");
    }

    /**
     * Cifra un texto usando el Cifrado César.
     */
    private String cifrarCesar(String texto, int desplazamiento) {
        StringBuilder textoCifrado = new StringBuilder();

        final int asciiInicio = 32;
        final int asciiFinal = 126;
        final int rango = asciiFinal - asciiInicio + 1;

        for (char c : texto.toCharArray()) {
            if (c >= asciiInicio && c <= asciiFinal) {
                int indiceActual = c - asciiInicio;
                int indiceNuevo = (indiceActual + desplazamiento % rango + rango) % rango;
                char nuevoCaracter = (char) (asciiInicio + indiceNuevo);
                textoCifrado.append(nuevoCaracter);
            } else {
                textoCifrado.append(c);
            }
        }
        return textoCifrado.toString();
    }
}