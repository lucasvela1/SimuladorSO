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
    private List<Proceso> procesos;             // Lista de todos los procesos definidos en la tanda
    private SystemParams params;                // Parámetros del sistema (TIP, TFP, TCP, Quantum)
    
    private Planificador planificador;          // Estrategia de planificación seleccionada
    private EstadoCPU cpu;                      // Estado actual del procesador
    private ColaListos colaListos;              // Cola de procesos listos
    private List<Proceso> colaBloqueados;       // Cola de procesos bloqueados (esperando E/S)
    private List<Proceso> colaNuevos;           // Procesos que arribaron pero aún no ingresan al sistema (TIP)

    // --- Resultados de la Simulación ---
    private List<Evento> log;                   // Registro de eventos
    private Metricas metricas;                  // Métricas acumuladas
    private boolean simulacionTerminada;

    /**
     * Constructor para inicializar el simulador.
     */
    public Simulador(List<Proceso> procesos, Planificador planificador, SystemParams params) {
        this.tiempoActual = 0;
        this.procesos = procesos;
        this.planificador = planificador;
        this.params = params;

        // Inicializar componentes internos
        this.cpu = new EstadoCPU();
        this.colaListos = new ColaListos();
        this.colaBloqueados = new ArrayList<>();
        this.colaNuevos = new ArrayList<>();
        this.log = new ArrayList<>();
        this.metricas = new Metricas();
        this.simulacionTerminada = false;
    }

    /**
     * Ejecuta la simulación completa hasta que todos los procesos finalicen.
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
     * Ejecuta un ciclo (tick de reloj) de la simulación.
     */
    private void ejecutarCiclo() {
        // 1. Actualizar procesos bloqueados (si terminan su E/S → listos)
        actualizarColaBloqueados();
        
        // 2. Procesar llegada de nuevos procesos (cuando arriba su tiempo)
        procesarLlegadas();
        
        // 3. Procesos nuevos pagan TIP antes de entrar a listos
        actualizarColaNuevos();

        // 4. Gestionar la CPU (ejecución, cambios de contexto, etc.)
        gestionarCPU();

        // 5. Aumentar contador de espera en cola de listos
        for (Proceso p : colaListos.getCola()) {
            p.setTiempoEnEstadoListo(p.getTiempoEnEstadoListo() + 1);
        }

        // 6. Verificar condición de fin
        verificarCondicionDeFin();
    }

    /**
     * Marca los procesos que llegan al sistema según su tiempo de arribo.
     */
    private void procesarLlegadas() {
        for (Proceso p : procesos) {
            if (p.getEstado().equals("NO_LLEGADO") && p.getTiempoArribo() <= tiempoActual) {
                p.setEstado("NUEVO");
                colaNuevos.add(p);
                registrarEvento(p.getPid(), "ARRIBO_PROCESO", "El proceso " + p.getNombre() + " ha arribado.");
            }
        }
    }

    /**
     * Los procesos en estado NUEVO deben pagar TIP (tiempo de ingreso al sistema).
     * TIP se ejecuta con CPU y cuenta como tiempo de SO, no como quantum del proceso.
     */
/**
     * Si la CPU no está ocupada con un TIP, busca un proceso nuevo y lo
     * prepara para que la CPU lo admita.
     */
    private void actualizarColaNuevos() {
        // Solo intentamos admitir un nuevo proceso si la CPU no está ya ocupada con un TIP
        if (!colaNuevos.isEmpty() && cpu.getTiempoRestanteTIP() == 0 && cpu.estaOciosa() && cpu.getTiempoRestanteTCP() == 0) {
            Proceso p = colaNuevos.get(0); // Tomamos el primero de la cola de nuevos

            // Preparamos al CPU para que maneje el TIP
            cpu.setProcesoADespachar(p);
            cpu.setTiempoRestanteTIP(params.getTip());
            registrarEvento(p.getPid(), "INICIO_TIP", "Proceso " + p.getNombre() + " inicia TIP.");
            
            // IMPORTANTE: Lo removemos de 'nuevos' para no procesarlo de nuevo.
            // La CPU ahora se encargará de él.
            colaNuevos.remove(p); 
        }
    }
    /**
     * Disminuye tiempos de E/S y mueve procesos bloqueados a listos cuando corresponde.
     */
    private void actualizarColaBloqueados() {
        List<Proceso> desbloqueados = new ArrayList<>();
        for (Proceso p : colaBloqueados) {
            p.setTiempoRestanteES(p.getTiempoRestanteES() - 1);

            if (p.getTiempoRestanteES() <= 0) {
                colaListos.agregar(p);
                desbloqueados.add(p);
                registrarEvento(p.getPid(), "BLOQUEADO_A_LISTO", "Proceso " + p.getNombre() + " terminó E/S.");
            }
        }
        colaBloqueados.removeAll(desbloqueados);
    }

    /**
     * Lógica central de gestión de la CPU:
     * - Ejecuta TIP, TCP, TFP como tiempo de SO.
     * - Ejecuta quantum de procesos en ejecución.
     * - Decide cuándo despachar nuevos procesos.
     */
     /**
     * Lógica central de gestión de la CPU:
     * - Ejecuta TIP, TCP, TFP como tiempo de SO.
     * - Ejecuta quantum de procesos en ejecución.
     * - Decide cuándo despachar nuevos procesos.
     */
    private void gestionarCPU() {
        // Caso 1: CPU atendiendo TIP (tiempo de ingreso al sistema)
        if (cpu.getTiempoRestanteTIP() > 0) {
            cpu.setTiempoRestanteTIP(cpu.getTiempoRestanteTIP() - 1);
            metricas.incrementarTiempoCPU_OS();

            if (cpu.getTiempoRestanteTIP() == 0) {
                Proceso p = cpu.getProcesoADespachar();
                if (p != null) {
                    colaListos.agregar(p);
                    registrarEvento(p.getPid(), "FIN_TIP", "Proceso " + p.getNombre() + " completó TIP y pasa a Listos.");
                    cpu.setProcesoADespachar(null);
                }
            }
            // Ya no es necesario el return aquí, permite que el ciclo continúe
            // por si justo en este tick la CPU queda libre y puede iniciar un TCP.
        }

        // Caso 2: CPU atendiendo TCP/TFP (cambio de contexto o fin de proceso)
        if (cpu.getTiempoRestanteTCP() > 0) {
            cpu.setTiempoRestanteTCP(cpu.getTiempoRestanteTCP() - 1);
            metricas.incrementarTiempoCPU_OS();

            if (cpu.getTiempoRestanteTCP() == 0) {
                Proceso p = cpu.getProcesoADespachar();
                if (p != null) {
                    cpu.asignarProceso(p, params.getQuantum());
                    registrarEvento(p.getPid(), "DESPACHO_PROCESO", "Proceso " + p.getNombre() + " pasa a ejecución.");
                }
            }
            return; // El TCP consume este ciclo completo
        }

        // Caso 3: CPU ejecutando un proceso
        if (!cpu.estaOciosa()) {
            Proceso actual = cpu.getProcesoActual();
            actual.setTiempoRestanteRafagaCPU(actual.getTiempoRestanteRafagaCPU() - 1);
            cpu.setQuantumRestante(cpu.getQuantumRestante() - 1);

            // ¿Terminó la ráfaga?
            if (actual.getTiempoRestanteRafagaCPU() <= 0) {
                actual.setRafagasRestantes(actual.getRafagasRestantes() - 1);
                registrarEvento(actual.getPid(), "FIN_RAFAGA_CPU", "Proceso " + actual.getNombre() + " terminó ráfaga de CPU.");

                if (actual.getRafagasRestantes() <= 0) {
                    // Proceso terminado → paga TFP antes de salir
                    actual.setEstado("TERMINADO");
                    actual.setTiempoFinEjecucion(tiempoActual);
                    registrarEvento(actual.getPid(), "PROCESO_TERMINADO", "Proceso " + actual.getNombre() + " ha finalizado.");

                    cpu.liberar();
                    cpu.setTiempoRestanteTCP(params.getTfp()); // TFP usa mismo canal que TCP
                    cpu.setProcesoADespachar(null);
                } else {
                    // Le quedan ráfagas → va a bloqueado (E/S)
                    actual.setEstado("BLOQUEADO");
                    actual.setTiempoRestanteES(actual.getDuracionRafagaES());
                    actual.setTiempoRestanteRafagaCPU(actual.getDuracionRafagaCPU());
                    colaBloqueados.add(actual);
                    registrarEvento(actual.getPid(), "EJECUCION_A_BLOQUEADO", "Proceso " + actual.getNombre() + " inicia E/S.");
                    cpu.liberar();
                }
            }
            // ¿Se agotó el quantum? (solo aplica Round Robin)
            else if (cpu.getQuantumRestante() <= 0) {
                colaListos.agregar(actual);
                registrarEvento(actual.getPid(), "FIN_QUANTUM", "Proceso " + actual.getNombre() + " vuelve a listos por fin de quantum.");
                cpu.liberar();
            }
            return; // La ejecución de proceso consumió el ciclo
        }

        // Caso 4: CPU libre → buscar siguiente proceso a ejecutar
        if (cpu.estaOciosa() && cpu.getTiempoRestanteTCP() == 0 && cpu.getTiempoRestanteTIP() == 0) {
            Proceso proximo = planificador.seleccionarSiguienteProceso(colaListos, procesos);
            if (proximo != null) {
                despacharProceso(proximo);
            } else {
                metricas.incrementarTiempoCPUDesocupada();
            }
        }
    }


    /**
     * Inicia un cambio de contexto antes de ejecutar un proceso.
     */
    private void despacharProceso(Proceso p) {
        cpu.setTiempoRestanteTCP(params.getTcp());
        cpu.setProcesoADespachar(p);
        registrarEvento(p.getPid(), "INICIO_TCP", "Iniciando cambio de contexto para " + p.getNombre());
    }
    
    /**
     * La simulación termina cuando todos los procesos han finalizado
     * y no queda TIP/TCP en ejecución.
     */
    private void verificarCondicionDeFin() {
        long procesosTerminados = procesos.stream().filter(p -> p.getEstado().equals("TERMINADO")).count();
        if (procesosTerminados == procesos.size() && cpu.estaOciosa() && cpu.getTiempoRestanteTCP() == 0 && cpu.getTiempoRestanteTIP() == 0) {
            this.simulacionTerminada = true;
        }
    }

    private void registrarEvento(Integer pid, String tipo, String mensaje) {
        this.log.add(new Evento(tiempoActual, pid, tipo, mensaje));
    }

    /**
     * Calcula las métricas finales de la simulación:
     * - TRp, TRn y tiempo en listo por proceso.
     * - TRt y TMRt de la tanda.
     * - Uso de CPU (procesos, SO, desocupada).
     */
    private void calcularMetricasFinales() {
        int sumaTR = 0;
        double sumaTRN = 0;

        for (Proceso p : procesos) {
            int tr = p.getTiempoFinEjecucion() - p.getTiempoArribo();
            double trn = (double) tr / (p.getCantidadRafagasCPU() * p.getDuracionRafagaCPU());

            sumaTR += tr;
            sumaTRN += trn;

            System.out.println("Proceso " + p.getPid() + " (" + p.getNombre() + "):");
            System.out.println("  TRp = " + tr);
            System.out.println("  TRn = " + String.format("%.2f", trn));
            System.out.println("  Tiempo en Listo = " + p.getTiempoEnEstadoListo());
        }

        int trt = procesos.stream().mapToInt(Proceso::getTiempoFinEjecucion).max().orElse(0)
                 - procesos.stream().mapToInt(Proceso::getTiempoArribo).min().orElse(0);
        double tmrt = (double) sumaTR / procesos.size();

        metricas.setTiempoRetornoTanda(trt);
        metricas.setTiempoMedioRetornoTanda(tmrt);

        System.out.println("\n==== METRICAS DE LA TANDA ====");
        System.out.println("TRt = " + trt);
        System.out.println("TMRt = " + String.format("%.2f", tmrt));

        System.out.println("\n==== USO DE CPU ====");
        int totalTiempo = tiempoActual;
        int cpuProc = totalTiempo - (metricas.getTiempoCPUDesocupada() + metricas.getTiempoCPU_OS());
        System.out.println("CPU Desocupada = " + metricas.getTiempoCPUDesocupada());
        System.out.println("CPU SO = " + metricas.getTiempoCPU_OS());
        System.out.println("CPU Procesos = " + cpuProc);
        System.out.println("Porcentajes: Desocupada=" 
            + (100.0 * metricas.getTiempoCPUDesocupada() / totalTiempo) + "%, SO=" 
            + (100.0 * metricas.getTiempoCPU_OS() / totalTiempo) + "%, Procesos=" 
            + (100.0 * cpuProc / totalTiempo) + "%");
    }

    // --- Getters ---
    public List<Evento> getLog() {
        return log;
    }

    public Metricas getMetricas() {
        return metricas;
    }
}
