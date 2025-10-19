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
    private static final double PROBABILIDAD_PERDIDA = 0.25; // 25% de pérdida
    /** Delay mínimo antes de que un paquete "aparezca" en el canal. */
    private static final int LATENCIA_MIN_MS = 100; // 0.1 seg
    /** Delay máximo antes de que un paquete "aparezca" en el canal. */
    private static final int LATENCIA_MAX_MS = 600; // 0.6 seg
    /** Cuánto tarda (fijo) un paquete en cruzar la pantalla. */
    private static final int DURACION_VIAJE_MS = 450; // 0.45 seg

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

    /**
     * El Receptor debe "prestar" su panel de animación al canal.
     */
    public void conectarReceptor(Receptor receptor, Pane panel) {
        this.receptorConectado = receptor;
        this.panelDeAnimacion = panel;
        System.out.println("CANAL " + frecuencia + ": Receptor conectado.");
    }

    public void desconectarReceptor() {
        this.receptorConectado = null;
        // Limpia cualquier animación residual del panel
        if (this.panelDeAnimacion != null) {
            this.panelDeAnimacion.getChildren().clear();
        }
        this.panelDeAnimacion = null;
        System.out.println("CANAL " + frecuencia + ": Receptor desconectado.");
    }

    // --- Motor de Simulación ---

    /**
     * Envía un paquete al canal.
     * @return true si el paquete fue aceptado para transmisión (hay un destinatario),
     * false si fue rechazado (no hay destinatario conectado).
     */
    public boolean enviarPaquete(Packet paquete) {
        // --- 1. Verificación de Conexión ---
        if (paquete.isAck()) {
            // Es un ACK (de Receptor a Emisor)
            if (emisorConectado == null) {
                System.out.println("CANAL " + frecuencia + ": ACK " + paquete.getSequenceNumber() + " perdido (Emisor desconectado).");
                return false; // El envío falla
            }
        } else {
            // Es un paquete de DATOS o HEADER (de Emisor a Receptor)
            if (receptorConectado == null || panelDeAnimacion == null) {
                System.out.println("CANAL " + frecuencia + ": Paquete " + paquete.getSequenceNumber() + " RECHAZADO (Receptor desconectado).");
                return false; // El envío falla
            }
        }

        // --- 2. Simulación de Pérdida ---
        if (random.nextDouble() < PROBABILIDAD_PERDIDA) {
            System.out.println("CANAL " + frecuencia + ": ¡PAQUETE " + paquete.getSequenceNumber() + " PERDIDO! (simulado)");
            // El paquete se "acepta" pero se pierde. El Emisor no sabe.
            return true;
        }

        // --- 3. Simulación de Desorden (Latencia Aleatoria) ---
        int latencia = LATENCIA_MIN_MS + random.nextInt(LATENCIA_MAX_MS - LATENCIA_MIN_MS);
        PauseTransition delay = new PauseTransition(Duration.millis(latencia));

        delay.setOnFinished(e -> {
            iniciarAnimacion(paquete);
        });
        delay.play();

        return true; // El envío fue "aceptado"
    }

    /**
     * Helper privado para crear y ejecutar la animación visual.
     */
    private void iniciarAnimacion(Packet paquete) {
        Platform.runLater(() -> {
            // Evita animar si el panel se desconectó mientras esperaba la latencia
            if (panelDeAnimacion == null) return;

            Circle visual = new Circle(14, paquete.isAck() ? Color.rgb(74, 255, 166) :
                    Color.rgb(97, 190, 253));
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
            tt.setToX(endX - startX); // Movimiento relativo a su posición inicial

            tt.setOnFinished(event -> {
                if (panelDeAnimacion != null) {
                    panelDeAnimacion.getChildren().remove(visual);
                }
                entregarPaquete(paquete);
            });

            tt.play();
        });
    }

    /**
     * Helper privado para entregar lógicamente el paquete al destinatario.
     */
    private void entregarPaquete(Packet paquete) {
        if (paquete.isAck()) {
            if (emisorConectado != null) {
                System.out.println("CANAL " + frecuencia + ": Entregando ACK " + paquete.getSequenceNumber() + " al Emisor.");
                emisorConectado.recibirAck(paquete);
            }
        } else {
            if (receptorConectado != null) {
                if (paquete.isHeader()) {
                    System.out.println("CANAL " + frecuencia + ": Entregando HEADER al Receptor.");
                } else {
                    System.out.println("CANAL " + frecuencia + ": Entregando Paquete " + paquete.getSequenceNumber() + " al Receptor.");
                }
                receptorConectado.recibirPaquete(paquete);
            }
        }
    }
}