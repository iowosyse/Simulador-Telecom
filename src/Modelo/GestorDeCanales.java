package Modelo;

import java.util.HashMap;
import java.util.Map;

/**
 * Clase singleton encargada de establecer comunicación entre Emisor y Receptor.
 * Administra una lista de los canales activos en la simulación.
 * */
public class GestorDeCanales {
    private static GestorDeCanales gestor;
    private final Map<Integer, Canal> canales;

    private GestorDeCanales() {
        canales = new HashMap<>();
        System.out.println("Gestor Singleton inicializado");
    }

    public static synchronized  GestorDeCanales getInstance() {
        if (gestor == null)
            gestor = new GestorDeCanales();

        return gestor;
    }

    /**
     * Obtiene un canal por su frecuencia.
     * Si el canal no existe, lo crea y lo registra.
     * Si ya existe, devuelve la instancia existente.
     *
     * @param id El número de frecuencia (ej. 1, 2, 3...)
     * @return El objeto Canal compartido para ese ID.
     */
    public Canal getCanal(int id) {
        return canales.computeIfAbsent(id, k -> new Canal(id));
    }
}
