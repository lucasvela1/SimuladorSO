package com.simulador.models;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Representa la cola de procesos para el planificador SRT.
 * Usa una PriorityQueue para asegurar que el proceso con el menor
 * tiempo restante de ráfaga de CPU siempre esté al frente.
 */
public class ColaListosSRT extends ColaListos {

    public ColaListosSRT() {
        // El comparador ordena por el tiempo restante de la ráfaga de CPU actual.
        super.cola = new PriorityQueue<>(Comparator.comparingInt(Proceso::getTiempoRestanteRafagaCPU));
    }
    
    /**
     * Método clave para SRT: permite ver el mejor proceso sin quitarlo.
     * @return El proceso con el menor tiempo restante, o null si la cola está vacía.
     */
    public Proceso verSiguiente() {
        return super.cola.peek();
    }
}