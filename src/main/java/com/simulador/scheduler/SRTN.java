package com.simulador.scheduler;

import java.util.List;

import com.simulador.models.ColaListos;
import com.simulador.models.Proceso;

public class SRTN implements Planificador {

    @Override
    public Proceso seleccionarSiguienteProceso(ColaListos colaListos, List<Proceso> procesos) {
        if (colaListos.estaVacia()) {
            return null;
        }
        
        return colaListos.quitar();
    }

    @Override
    public boolean esExpropiativo() {
        return true; //SRTN es expropiativo
    }
}