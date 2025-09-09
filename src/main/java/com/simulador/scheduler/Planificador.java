package com.simulador.scheduler;

import java.util.List;

import com.simulador.models.ColaListos;
import com.simulador.models.Proceso;


//Interfaz que define las estrategias de planificación.

public interface Planificador {
    //Selecciona el siguiente proceso a ejecutar de la cola de listos, regresa el proceso seleccionado o null si no hay ninguno.
    Proceso seleccionarSiguienteProceso(ColaListos colaListos, List<Proceso> procesos);

    boolean esExpropiativo();
}