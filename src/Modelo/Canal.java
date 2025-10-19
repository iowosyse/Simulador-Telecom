package Modelo;

import Controlador.Emisor;
import Controlador.Receptor;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import java.util.Random;

/**
 * Canal es el motor de simulación.
 * Gestiona la pérdida, latencia (desorden) y animación de paquetes.
 */
public class Canal {

    // --- Conexiones ---
    private final int frecuencia;
    private Emisor emisorConectado;
    private Receptor receptorConectado;

    /** El "lienzo" (prestado por el Receptor) donde se dibujará la animación. */
    private Pane panelDeAnimacion;

    // --- Parámetros de Simulación ---
    private final Random random = new Random();
    /** Probabilidad de 0.0 (0%) a 1.0 (100%) de que un paquete se pierda. */
    private static final double PROBABILIDAD_PERDIDA = 0.25;
    /** Probabilidad de que un paquete de DATOS o HEADER se corrompa. */
    private static final double PROBABILIDAD_CORRUPCION = 0.25;
    /** Delay mínimo antes de que un paquete "aparezca" en el canal. */
    private static final int LATENCIA_MIN_MS = 100;
    /** Delay máximo antes de que un paquete "aparezca" en el canal. */
    private static final int LATENCIA_MAX_MS = 600;
    /** Cuánto tarda (fijo) un paquete en cruzar la pantalla. */
    private static final int DURACION_VIAJE_MS = 450;

    public Canal(int frecuencia) {
        this.frecuencia = frecuencia;
        System.out.println("CANAL " + frecuencia + ": Creado.");
    }

    public int getFrecuencia() {
        return this.frecuencia;
    }


    // --- Métodos de Conexión ---
    public void conectarEmisor(Emisor emisor) {
        this.emisorConectado = emisor;
        System.out.println("CANAL " + frecuencia + ": Emisor conectado.");
    }

    public void desconectarEmisor() {
        this.emisorConectado = null;
        System.out.println("CANAL " + frecuencia + ": Emisor desconectado.");
    }

    public void conectarReceptor(Receptor receptor, Pane panel) {
        this.receptorConectado = receptor;
        this.panelDeAnimacion = panel;
        System.out.println("CANAL " + frecuencia + ": Receptor conectado.");
    }

    public void desconectarReceptor() {
        this.receptorConectado = null;
        if (this.panelDeAnimacion != null) {
            this.panelDeAnimacion.getChildren().clear();
        }
        this.panelDeAnimacion = null;
        System.out.println("CANAL " + frecuencia + ": Receptor desconectado.");
    }

    // --- Motor de Simulación ---
    public boolean enviarPaquete(Packet paquete) {
        if (paquete.isAck()) {
            if (emisorConectado == null) {
                System.out.println("CANAL " + frecuencia + ": ACK " + paquete.getSequenceNumber() + " perdido (Emisor desconectado).");
                return false;
            }
        } else {
            if (receptorConectado == null || panelDeAnimacion == null) {
                System.out.println("CANAL " + frecuencia + ": Paquete " + paquete.getSequenceNumber() + " RECHAZADO (Receptor desconectado).");
                return false;
            }
        }

        if (random.nextDouble() < PROBABILIDAD_PERDIDA) {
            System.out.println("CANAL " + frecuencia + ": ¡PAQUETE " + paquete.getSequenceNumber() + " PERDIDO! (simulado)");
            return true;
        }

        int latencia = LATENCIA_MIN_MS + random.nextInt(LATENCIA_MAX_MS - LATENCIA_MIN_MS);
        PauseTransition delay = new PauseTransition(Duration.millis(latencia));

        delay.setOnFinished(e -> {
            iniciarAnimacion(paquete);
        });
        delay.play();

        return true;
    }

    /**
     * Helper privado para crear y ejecutar la animación visual.
     */
    private void iniciarAnimacion(Packet paquete) {
        // La decisión de corrupción se toma ANTES de dibujar
        final boolean seCorrompera = !paquete.isAck() && random.nextDouble() < PROBABILIDAD_CORRUPCION;

        Platform.runLater(() -> {
            if (panelDeAnimacion == null) return;

            // 1. Elegir color basado en el estado
            Color colorPaquete;
            if (paquete.isAck()) {
                colorPaquete = Color.rgb(74, 255, 166); // Verde (ACK)
            } else if (seCorrompera) {
                colorPaquete = Color.rgb(255, 87, 87); // Rojo (CORRUPTO)
            } else {
                colorPaquete = Color.rgb(97, 190, 253); // Azul (Datos OK)
            }

            Circle visual = new Circle(14, colorPaquete);
            visual.setStroke(Color.BLACK);

            double startX, endX;
            if (paquete.isAck()) {
                startX = panelDeAnimacion.getWidth() - 20;
                endX = 20;
            } else {
                startX = 20;
                endX = panelDeAnimacion.getWidth() - 20;
            }

            visual.setLayoutX(startX);
            visual.setLayoutY(panelDeAnimacion.getHeight() / 2 + random.nextInt(80) - 40);

            panelDeAnimacion.getChildren().add(visual);

            TranslateTransition tt = new TranslateTransition(Duration.millis(DURACION_VIAJE_MS), visual);
            tt.setToX(endX - startX);

            tt.setOnFinished(event -> {
                if (panelDeAnimacion != null) {
                    panelDeAnimacion.getChildren().remove(visual);
                }
                entregarPaquete(paquete, seCorrompera);
            });

            tt.play();
        });
    }

    /**
     * Simula ruido volteando un bit aleatorio en el payload.
     * ADVERTENCIA: Esto modifica el objeto Packet.
     */
    private void corromperPaquete(Packet p) {
        byte[] payload = p.getPayload();
        if (payload != null && payload.length > 0) {
            int byteIndex = random.nextInt(payload.length);
            int bitIndex = random.nextInt(8);
            payload[byteIndex] = (byte) (payload[byteIndex] ^ (1 << bitIndex));
            System.out.println("CANAL " + frecuencia + ": Bit-flip en byte " + byteIndex);
        }
    }

    /**
     * Helper privado para entregar lógicamente el paquete al destinatario.
     * @param paqueteOriginal El paquete *original* (limpio) del Emisor.
     * @param seCorrompera La decisión tomada en iniciarAnimacion.
     */
    private void entregarPaquete(Packet paqueteOriginal, boolean seCorrompera) { // <-- MODIFICADO

        // --- LÓGICA DE CLONACIÓN ---
        Packet paqueteADeliverar;
        if (seCorrompera) {
            System.out.println("CANAL " + frecuencia + ": ¡PAQUETE " + paqueteOriginal.getSequenceNumber() + " CORRUPTO! (simulado)");
            // 1. Crea un clon
            paqueteADeliverar = new Packet(paqueteOriginal);
            // 2. Corrompe el clon
            corromperPaquete(paqueteADeliverar);
        } else {
            // 3. El paquete está limpio, se entrega el original
            paqueteADeliverar = paqueteOriginal;
        }
        // -------------------------

        if (paqueteADeliverar.isAck()) {
            if (emisorConectado != null) {
                System.out.println("CANAL " + frecuencia + ": Entregando ACK " + paqueteADeliverar.getSequenceNumber() + " al Emisor.");
                emisorConectado.recibirAck(paqueteADeliverar);
            }
        } else {
            if (receptorConectado != null) {
                if (paqueteADeliverar.isHeader()) {
                    System.out.println("CANAL " + frecuencia + ": Entregando HEADER al Receptor.");
                } else {
                    System.out.println("CANAL " + frecuencia + ": Entregando Paquete " + paqueteADeliverar.getSequenceNumber() + " al Receptor.");
                }
                // Entrega el clon corrupto o el original limpio
                receptorConectado.recibirPaquete(paqueteADeliverar);
            }
        }
    }
}