package com.simulador.scheduler;

import java.util.List;

import com.simulador.models.ColaListos;
import com.simulador.models.Proceso;

public class PrioridadExterna implements Planificador {

    @Override
    public Proceso seleccionarSiguienteProceso(ColaListos colaListos, List<Proceso> procesos) {
        // La ColaListosPrioridad ya ordenó los procesos.
        // Solo tenemos que quitar y devolver el de mayor prioridad.
        return colaListos.quitar();
    }

    @Override
    public boolean esExpropiativo() {
        return true;
    }
}