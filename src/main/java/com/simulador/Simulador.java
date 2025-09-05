package com.simulador;

import java.util.ArrayList;
import java.util.List;

import com.simulador.models.ColaListos;
import com.simulador.models.EstadoCPU;
import com.simulador.models.Evento;
import com.simulador.models.Metricas;
import com.simulador.models.Proceso;
import com.simulador.models.SystemParams;
import com.simulador.scheduler.Planificador;

/**
 * El motor principal que ejecuta la simulación paso a paso.
 * Gestiona el tiempo, los estados de los procesos y la interacción con el planificador.
 */
public class Simulador {

    // --- Atributos del Estado de la Simulación ---
    private int tiempoActual;
    private List<Proceso> procesos; // La lista maestra de todos los procesos
    private SystemParams params; // Parámetros como TIP, TFP, TCP
    
    private Planificador planificador;
    private EstadoCPU cpu;
    private ColaListos colaListos;
    private List<Proceso> colaBloqueados;
    private List<Proceso> colaNuevos; // Procesos que han llegado pero aún no pasan a Listos (por el TIP)

    // --- Resultados de la Simulación ---
    private List<Evento> log;
    private Metricas metricas;
    private boolean simulacionTerminada;

    /**
     * Constructor para inicializar el simulador.
     * @param procesos La lista de procesos a simular.
     * @param planificador La estrategia de planificación elegida.
     * @param params Los parámetros del sistema (TIP, TFP, TCP, Quantum).
     */
    public Simulador(List<Proceso> procesos, Planificador planificador, SystemParams params) {
        this.tiempoActual = 0;
        this.procesos = procesos;
        this.planificador = planificador;
        this.params = params;

        // Inicializar componentes
        this.cpu = new EstadoCPU();
        this.colaListos = new ColaListos();
        this.colaBloqueados = new ArrayList<>();
        this.colaNuevos = new ArrayList<>();
        this.log = new ArrayList<>();
        this.metricas = new Metricas();
        this.simulacionTerminada = false;

        // Clonamos la lista de procesos para no modificar la original
        this.procesos.forEach(p -> p.inicializarParaSimulacion(tiempoActual));
    }

    /**
     * El método principal que ejecuta la simulación completa.
     */
    public void iniciar() {
        registrarEvento(null, "INICIO_SIMULACION", "La simulación ha comenzado.");

        while (!simulacionTerminada) {
            ejecutarCiclo();
            tiempoActual++;
        }

        registrarEvento(null, "FIN_SIMULACION", "La simulación ha terminado en t=" + tiempoActual);
        calcularMetricasFinales();
    }

    /**
     * Ejecuta un único "tick" de tiempo en la simulación.
     * Sigue el orden de procesamiento de eventos acordado.
     */
    private void ejecutarCiclo() {
        // 1. Verificar si un proceso en ejecución termina o se bloquea
        // (Esta lógica estaría dentro del manejo del CPU)

        // 2. Verificar procesos bloqueados que pasan a listos
        actualizarColaBloqueados();
        
        // 3. Verificar procesos nuevos que llegan al sistema y pasan a la cola de nuevos
        procesarLlegadas();
        
        // 4. Verificar procesos en cola de nuevos que pasan a listos (cumplido el TIP)
        actualizarColaNuevos();

        // 5. Gestionar la CPU (correr proceso, atender interrupciones, despachar)
        gestionarCPU();
        
        // 6. Comprobar si la simulación debe terminar
        verificarCondicionDeFin();
    }
    
    private void procesarLlegadas() {
        for (Proceso p : procesos) {
            if (p.getTiempoArribo() == tiempoActual) {
                p.setEstado("NUEVO");
                colaNuevos.add(p);
                registrarEvento(p.getPid(), "ARRIBO_PROCESO", "El proceso " + p.getNombre() + " ha arribado.");
            }
        }
    }
    
    private void actualizarColaNuevos() {
      List<Proceso> procesosListos = new ArrayList<>();
      for (Proceso p : colaNuevos) {
        // Si el proceso acaba de llegar, asignamos el tiempo de admisión
        if (p.getTiempoRestanteTIP() == 0) {
            p.setTiempoRestanteTIP(params.getTip());
        }

        p.setTiempoRestanteTIP(p.getTiempoRestanteTIP() - 1); // Decrementamos el tiempo de espera

        if (p.getTiempoRestanteTIP() <= 0) {
            colaListos.agregar(p); // El proceso pasa a la cola de listos
            procesosListos.add(p);
            registrarEvento(p.getPid(), "NUEVO_A_LISTO", "Proceso " + p.getNombre() + " admitido en el sistema.");
        }
        }
    // Removemos de la cola de nuevos a los que ya pasaron a listos
      colaNuevos.removeAll(procesosListos);
   }

    private void actualizarColaBloqueados() {
       List<Proceso> procesosDesbloqueados = new ArrayList<>();
       for (Proceso p : colaBloqueados) {
         p.setTiempoRestanteES(p.getTiempoRestanteES() - 1); // Decrementamos su tiempo de E/S

         if (p.getTiempoRestanteES() <= 0) {
            colaListos.agregar(p); // Termina E/S y pasa a listos
            procesosDesbloqueados.add(p);
            registrarEvento(p.getPid(), "BLOQUEADO_A_LISTO", "Proceso " + p.getNombre() + " terminó E/S.");
         }
        }
      // Removemos de la cola de bloqueados a los que ya terminaron su E/S
      colaBloqueados.removeAll(procesosDesbloqueados);
    }

   private void gestionarCPU() {
    // Caso 1: La CPU está en medio de un cambio de contexto (TCP)
    if (cpu.getTiempoRestanteTCP() > 0) {
        cpu.setTiempoRestanteTCP(cpu.getTiempoRestanteTCP() - 1);
        metricas.incrementarTiempoCPU_OS(); // El cambio de contexto consume tiempo de SO

        // Si el cambio de contexto termina JUSTO AHORA
        if (cpu.getTiempoRestanteTCP() == 0) {
            Proceso p = cpu.getProcesoADespachar();
            cpu.asignarProceso(p, params.getQuantum()); // Asignamos el proceso a la CPU
            registrarEvento(p.getPid(), "DESPACHO_PROCESO", "Proceso " + p.getNombre() + " pasa a ejecución.");
        }
        return; // No hacemos más nada en este ciclo
    }

    // Caso 2: La CPU tiene un proceso en ejecución
    if (!cpu.estaOciosa()) {
        Proceso actual = cpu.getProcesoActual();
        actual.setTiempoRestanteRafagaCPU(actual.getTiempoRestanteRafagaCPU() - 1);
        cpu.setQuantumRestante(cpu.getQuantumRestante() - 1);

        // Verificamos si la ráfaga de CPU terminó
        if (actual.getTiempoRestanteRafagaCPU() <= 0) {
            actual.setRafagasRestantes(actual.getRafagasRestantes() - 1);
            registrarEvento(actual.getPid(), "FIN_RAFAGA_CPU", "Proceso " + actual.getNombre() + " terminó ráfaga de CPU.");

            // Si no le quedan más ráfagas, el proceso TERMINA
            if (actual.getRafagasRestantes() <= 0) {
                actual.setEstado("TERMINADO");
                registrarEvento(actual.getPid(), "PROCESO_TERMINADO", "Proceso " + actual.getNombre() + " ha finalizado.");
                cpu.liberar();
            } else {
                // Si aún le quedan ráfagas, se va a BLOQUEADO por E/S
                actual.setEstado("BLOQUEADO");
                actual.setTiempoRestanteES(actual.getDuracionRafagaES());
                colaBloqueados.add(actual);
                registrarEvento(actual.getPid(), "EJECUCION_A_BLOQUEADO", "Proceso " + actual.getNombre() + " inicia E/S.");
                cpu.liberar();
            }
        }
        // Verificamos si se acabó el Quantum (para Round Robin)
        else if (cpu.getQuantumRestante() <= 0) {
            // La ráfaga no terminó, pero el quantum sí. El proceso vuelve a la cola de listos.
            colaListos.agregar(actual);
            registrarEvento(actual.getPid(), "FIN_QUANTUM", "Proceso " + actual.getNombre() + " vuelve a listos por fin de quantum.");
            cpu.liberar();
        }
    }

    // Caso 3: La CPU está libre y no está cambiando de contexto
        if (cpu.estaOciosa() && cpu.getTiempoRestanteTCP() == 0) {
           Proceso proximo = planificador.seleccionarSiguienteProceso(colaListos, procesos);
           if (proximo != null) {
              despacharProceso(proximo); // Iniciamos el proceso de despacho
            } else {
              metricas.incrementarTiempoCPUDesocupada(); // Nadie en la cola de listos, CPU ociosa.
            }
        }
    }

    private void despacharProceso(Proceso p) {
       // En lugar de asignar directamente, iniciamos el temporizador del cambio de contexto
       cpu.setTiempoRestanteTCP(params.getTcp());
       cpu.setProcesoADespachar(p);
       registrarEvento(p.getPid(), "INICIO_CONMUTACION", "Iniciando cambio de contexto para " + p.getNombre());
    }
    
    private void verificarCondicionDeFin() {
        // La simulación termina cuando no hay procesos en ejecución, ni en listos,
        // ni en bloqueados, y todos los procesos originales han llegado y terminado.
        long procesosTerminados = procesos.stream().filter(p -> p.getEstado().equals("TERMINADO")).count();
        if (procesosTerminados == procesos.size()) {
            this.simulacionTerminada = true;
        }
    }

    private void registrarEvento(Integer pid, String tipo, String mensaje) {
        this.log.add(new Evento(tiempoActual, pid, tipo, mensaje));
    }

    private void calcularMetricasFinales() {
        // Lógica para calcular todos los tiempos de retorno, promedios, etc.
    }

    // --- Getters para que la UI obtenga los resultados ---
    public List<Evento> getLog() {
        return log;
    }

    public Metricas getMetricas() {
        return metricas;
    }
}