package com.simulador.models;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Representa la cola de procesos en estado "LISTO" (o la fila única de planificación).
 * Para un FCFS estricto, debe ser una cola FIFO (First-In, First-Out).
 * Cuando un proceso se añade, siempre va al final.
 */
public class ColaListos {

    private Queue<Proceso> cola;

    public ColaListos() {
        // Usamos LinkedList, que es una implementación de cola FIFO simple y perfecta.
        this.cola = new LinkedList<>();
    }

    public void agregar(Proceso proceso) {
        // El método add() en una LinkedList siempre añade el elemento al final.
        this.cola.add(proceso);
    }

    public Proceso quitar() {
        // El método poll() quita y devuelve el elemento del principio de la fila.
        return this.cola.poll();
    }

    public boolean estaVacia() {
        return this.cola.isEmpty();
    }
    
    public Queue<Proceso> getCola() {
        return cola;
    }
}