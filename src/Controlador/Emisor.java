package Controlador;

import Modelo.Canal;
import Modelo.GestorDeCanales;
import Modelo.Packet;
import Modelo.Trama;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
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

    private static final int TAMAÑO_VENTANA = 5;
    private static final Duration TIMEOUT_DURACION = Duration.seconds(5);

    private List<Packet> tramaPendiente;
    private int ventanaBase = 0;
    private int proximoSeqNum = 0;

    /**
     * El "Panel de Control de Alarmas".
     * Almacena un timer activo para cada paquete que está en vuelo.
     * Clave: sequenceNumber, Valor: El objeto PauseTransition (la "alarma").
     */
    private final Map<Integer, PauseTransition> timersActivos = new HashMap<>();

    /**
     * Almacén de ACKs que han llegado "fuera de orden" dentro de la ventana actual.
     * Ejemplo: Si recibimos ACK 2 y 3, pero aún no el 0, se guardan aquí.
     */
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
        canalActual = GestorDeCanales.getInstance().getCanal(id);
        canalActual.conectarEmisor(this);
        System.out.println("Emisor sintonizado en Canal " + id);
    }

    /**
     * Inicia el proceso de envío de la trama.
     */
    public void enviarPressed() {
        String mensaje = cifrarCesar(txtMensaje.getText(), (int) sliderCifrado.getValue());
        if (mensaje.isEmpty()) return; // No enviar mensajes vacíos

        byte[] cargaUtilTotal = mensaje.getBytes(StandardCharsets.UTF_8);

        int tamañoPayload = 10;

        Trama trama = new Trama(cargaUtilTotal, tamañoPayload);
        this.tramaPendiente = trama.getPackets();

        ventanaBase = 0;
        proximoSeqNum = 0;
        timersActivos.values().forEach(PauseTransition::stop); // Detiene timers viejos
        timersActivos.clear();
        acksRecibidosEnVentana.clear();

        enviarBtn.setDisable(true);

        enviarVentana();
    }

    /**
     * Envía todos los paquetes nuevos que quepan en la ventana actual.
     */
    private void enviarVentana() {
        while (proximoSeqNum < tramaPendiente.size() && proximoSeqNum < ventanaBase + TAMAÑO_VENTANA) {

            Packet paqueteAEnviar = tramaPendiente.get(proximoSeqNum);

            System.out.println("EMISOR: Enviando paquete seq=" + paqueteAEnviar.getSequenceNumber());
            if (canalActual != null) {
                canalActual.enviarPaquete(paqueteAEnviar);
                iniciarTimerPara(paqueteAEnviar);
            }
            proximoSeqNum++;
        }
    }

    /**
     * Inicia un temporizador individual (alarma) para un paquete específico.
     */
    private void iniciarTimerPara(Packet paquete) {
        int seq = paquete.getSequenceNumber();

        PauseTransition timer = new PauseTransition(TIMEOUT_DURACION);

        timer.setOnFinished(e -> {
            System.out.println("EMISOR: ¡TIMEOUT! para seq=" + seq + ". Retransmitiendo...");
            timersActivos.remove(seq); // Quita esta alarma (ya sonó)

            if (canalActual != null) {
                // Vuelve a enviar el MISMO paquete
                canalActual.enviarPaquete(paquete);
                // Inicia una NUEVA alarma para este reintento
                iniciarTimerPara(paquete);
            }
        });

        timer.play();
        timersActivos.put(seq, timer);
    }

    /**
     * Método PÚBLICO que el CANAL llamará cuando un ACK llegue.
     * Este es el núcleo de la lógica de Repetición Selectiva.
     */
    public void recibirAck(Packet ack) {
        if (!ack.isAck()) return; // Ignorar si no es un ACK

        int seq = ack.getSequenceNumber();
        System.out.println("EMISOR: Recibido ACK para seq=" + seq);

        // 1. Buscar la alarma correspondiente
        PauseTransition timer = timersActivos.get(seq);

        // 2. Si hay una alarma para él, la desactivamos
        if (timer != null) {
            timer.stop();
            timersActivos.remove(seq); // Quitarla del panel de control
        }

        // 3. Si el ACK está dentro de la ventana actual...
        if (seq >= ventanaBase && seq < ventanaBase + TAMAÑO_VENTANA) {
            // Marcarlo como "recibido"
            acksRecibidosEnVentana.add(seq);

            // 4. ¡Intentar DESLIZAR la ventana!
            // Mueve la "base" de la ventana hacia adelante por todos
            // los paquetes consecutivos que ya han sido confirmados.
            while (acksRecibidosEnVentana.contains(ventanaBase)) {
                acksRecibidosEnVentana.remove(ventanaBase); // Quitarlo del set
                ventanaBase++; // ¡DESLIZA!
                System.out.println("EMISOR: Ventana deslizada a base=" + ventanaBase);
            }

            // 5. Enviar nuevos paquetes que ahora caben en la ventana deslizada
            enviarVentana();
        }

        // 6. Comprobar si terminamos
        if (ventanaBase == tramaPendiente.size()) {
            System.out.println("EMISOR: Trama completa enviada y confirmada.");
            enviarBtn.setDisable(false); // Reactiva el botón
        }
    }


    /**
     * Cifra un texto usando el Cifrado César en un rango extendido (ASCII 32-126).
     */
    private String cifrarCesar(String texto, int desplazamiento) {
        StringBuilder textoCifrado = new StringBuilder();

        final int asciiInicio = 32;
        final int asciiFinal = 126;
        final int rango = asciiFinal - asciiInicio + 1; // 95 caracteres

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