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
    private static final double PROBABILIDAD_PERDIDA = 0.15; // 15% de pérdida
    /** Delay mínimo antes de que un paquete "aparezca" en el canal. */
    private static final int LATENCIA_MIN_MS = 200; // 0.2 seg
    /** Delay máximo antes de que un paquete "aparezca" en el canal. */
    private static final int LATENCIA_MAX_MS = 1500; // 1.5 seg
    /** Cuánto tarda (fijo) un paquete en cruzar la pantalla. */
    private static final int DURACION_VIAJE_MS = 1000; // 1 seg

    public Canal(int frecuencia) {
        this.frecuencia = frecuencia;
        System.out.println("CANAL " + frecuencia + ": Creado.");
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
     * Este es el método principal llamado por Emisor (para DATOS)
     * y Receptor (para ACKs) para enviar un paquete.
     */
    public void enviarPaquete(Packet paquete) {
        // --- 1. Verificación de Conexión (¿Hay alguien al otro lado?) ---
        if (paquete.isAck()) {
            // Es un ACK (de Receptor a Emisor)
            if (emisorConectado == null) {
                System.out.println("CANAL " + frecuencia + ": ACK " + paquete.getSequenceNumber() + " perdido (Emisor desconectado).");
                return;
            }
        } else {
            // Es un paquete de DATOS (de Emisor a Receptor)
            if (receptorConectado == null || panelDeAnimacion == null) {
                System.out.println("CANAL " + frecuencia + ": Paquete " + paquete.getSequenceNumber() + " perdido (Receptor desconectado).");
                return;
            }
        }

        // --- 2. Simulación de Pérdida (La "moneda") ---
        if (random.nextDouble() < PROBABILIDAD_PERDIDA) {
            System.out.println("CANAL " + frecuencia + ": ¡PAQUETE " + paquete.getSequenceNumber() + " PERDIDO! (simulado)");
            // El paquete simplemente desaparece. No se anima. No se entrega.
            // El timer del Emisor eventualmente sonará.
            return;
        }

        // --- 3. Simulación de Desorden (Latencia Aleatoria) ---
        // Se añade un retraso aleatorio *antes* de que la animación comience.
        // Así es como el paquete 3 puede "aparecer" antes que el 2.
        int latencia = LATENCIA_MIN_MS + random.nextInt(LATENCIA_MAX_MS - LATENCIA_MIN_MS);
        PauseTransition delay = new PauseTransition(Duration.millis(latencia));

        // Esto se ejecutará DESPUÉS de que el retraso (latencia) haya terminado
        delay.setOnFinished(e -> {
            // ¡El paquete "aparece" en el canal y comienza su viaje!
            iniciarAnimacion(paquete);
        });
        delay.play();
    }

    /**
     * Helper privado para crear y ejecutar la animación visual.
     */
    private void iniciarAnimacion(Packet paquete) {
        // Las operaciones de UI (crear círculos, animar) DEBEN
        // ejecutarse en el hilo de la aplicación de JavaFX.
        Platform.runLater(() -> {

            // 1. Crear el objeto visual (el "paquete")
            Circle visual = new Circle(8, paquete.isAck() ? Color.LIMEGREEN : Color.DODGERBLUE);
            visual.setStroke(Color.BLACK);

            // 2. Definir inicio y fin de la animación
            double startX, endX;

            if (paquete.isAck()) {
                // ACK: Viaja de Derecha (Receptor) a Izquierda (Emisor)
                startX = panelDeAnimacion.getWidth() - 20;
                endX = 20;
            } else {
                // DATOS: Viaja de Izquierda (Emisor) a Derecha (Receptor)
                startX = 20;
                endX = panelDeAnimacion.getWidth() - 20;
            }

            // Colocar el círculo en su posición inicial
            visual.setLayoutX(startX);
            // Añadir un "jitter" vertical para que no vayan todos por la misma línea
            visual.setLayoutY(panelDeAnimacion.getHeight() / 2 + random.nextInt(80) - 40);

            // 3. Añadir el círculo al panel para que sea visible
            panelDeAnimacion.getChildren().add(visual);

            // 4. Crear la animación de traslación
            TranslateTransition tt = new TranslateTransition(Duration.millis(DURACION_VIAJE_MS), visual);
            tt.setToX(endX - startX); // Movimiento relativo a su posición inicial

            // 5. Definir qué hacer cuando la animación TERMINE (el paquete "llegó")
            tt.setOnFinished(event -> {
                panelDeAnimacion.getChildren().remove(visual); // Quitar el círculo de la pantalla
                entregarPaquete(paquete); // Entregar el paquete lógicamente
            });

            // 6. ¡Iniciar la animación!
            tt.play();
        });
    }

    /**
     * Helper privado para entregar lógicamente el paquete al destinatario.
     */
    private void entregarPaquete(Packet paquete) {
        // Comprueba la bandera "isAck" para saber a quién entregarlo
        if (paquete.isAck()) {
            // Es un ACK, va para el Emisor (si sigue conectado)
            if (emisorConectado != null) {
                System.out.println("CANAL " + frecuencia + ": Entregando ACK " + paquete.getSequenceNumber() + " al Emisor.");
                // Llama al método de callback en el Emisor
                emisorConectado.recibirAck(paquete);
            }
        } else {
            // Es un paquete de DATOS, va para el Receptor (si sigue conectado)
            if (receptorConectado != null) {
                System.out.println("CANAL " + frecuencia + ": Entregando Paquete " + paquete.getSequenceNumber() + " al Receptor.");
                // Llama al método de callback en el Receptor
                //TODO: escribir el método de recibirPaquete() de la clase Receptor
                receptorConectado.recibirPaquete(paquete);
            }
        }
    }
}