package com.simulador.scheduler;

import java.util.List;

import com.simulador.models.ColaListos;
import com.simulador.models.Proceso;

public class RoundRobin implements Planificador {

    @Override
    public Proceso seleccionarSiguienteProceso(ColaListos colaListos, List<Proceso> procesos) {
        //Round Robin usa una cola FIFO simple para seleccionar al siguiente.
        return colaListos.quitar();
    }

    @Override
    public boolean esExpropiativo() {
        //Es expropiativo porque el quantum puede interrumpir un proceso.
        return true;
    }
}