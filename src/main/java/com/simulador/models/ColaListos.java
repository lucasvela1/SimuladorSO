package com.simulador.models;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Representa la cola de procesos en estado "LISTO".
 */
public class ColaListos {

    private Queue<Proceso> cola;

    public ColaListos() {
        this.cola = new LinkedList<>();
    }

    public void agregar(Proceso proceso) {
        proceso.setEstado("LISTO");
        this.cola.add(proceso);
    }

    public Proceso quitar() {
        return this.cola.poll(); // poll() devuelve null si la cola está vacía
    }

    public boolean estaVacia() {
        return this.cola.isEmpty();
    }
    
    public Queue<Proceso> getCola() {
        return cola;
    }
}