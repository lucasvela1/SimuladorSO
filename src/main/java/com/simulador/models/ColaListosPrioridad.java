package com.simulador.models;

import java.util.Comparator;
import java.util.PriorityQueue;

/*
   Representa la cola de procesos para el planificador de Prioridad Externa.
   Usa una PriorityQueue para asegurar que el proceso con la mayor prioridad siempre esté al frente.
 */

public class ColaListosPrioridad extends ColaListos {

    public ColaListosPrioridad() {
        // El comparador ordena por el número de prioridad, de mayor a menor.
        super.cola = new PriorityQueue<>(Comparator.comparingInt(Proceso::getPrioridadExterna).reversed());

    }

}