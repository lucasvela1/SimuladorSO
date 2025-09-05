package com.simulador.scheduler;

import java.util.List;

import com.simulador.models.ColaListos;
import com.simulador.models.Proceso;

/**
 * Interfaz que define el contrato para todas las estrategias de planificación.
 */
public interface Planificador {
    /**
     * Selecciona el siguiente proceso a ejecutar de la cola de listos.
     * @param colaListos La cola de procesos listos actual.
     * @param procesos La lista de todos los procesos (por si se necesita info adicional).
     * @return El proceso seleccionado, o null si no hay ninguno.
     */
    Proceso seleccionarSiguienteProceso(ColaListos colaListos, List<Proceso> procesos);
}