package com.simulador.scheduler;

import java.util.List;

import com.simulador.models.ColaListos;
import com.simulador.models.Proceso;

public class SRTN implements Planificador {

    @Override
    public Proceso seleccionarSiguienteProceso(ColaListos colaListos, List<Proceso> procesos) {
        //Round Robin toma el siguiente proceso siempre
        if (colaListos.estaVacia()) {
            return null;
        }
        
        return colaListos.quitar();
    }
}