package com.simulador.models;

import java.util.Comparator;
import java.util.PriorityQueue;


public class ColaListosSPN extends ColaListos {

    public ColaListosSPN() {
        // El comparador ordena por la duración de la ráfaga de CPU, de menor a mayor.
        super.cola = new PriorityQueue<>(Comparator.comparingInt(Proceso::getDuracionRafagaCPU));
    }
}