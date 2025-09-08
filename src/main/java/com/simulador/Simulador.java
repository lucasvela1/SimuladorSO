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
    private List<Proceso> procesos;           // Lista de todos los procesos definidos en la tanda
    private SystemParams params;              // Parámetros del sistema (TIP, TFP, TCP, Quantum)
    
    private Planificador planificador;        // Estrategia de planificación seleccionada
    private EstadoCPU cpu;                    // Estado actual del procesador
    private ColaListos colaPrincipal;         // Fila única de planificación, ordenada por orden de llegada (FIFO).
    private List<Proceso> colaBloqueados;     // Cola de procesos bloqueados (esperando E/S)

    // --- Resultados de la Simulación ---
    private List<Evento> log;                 // Registro de eventos
    private Metricas metricas;                // Métricas acumuladas
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
        this.colaPrincipal = new ColaListos(); // Usa una cola FIFO (LinkedList)
        this.colaBloqueados = new ArrayList<>();
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
        }
        
        int tiempoFinal = tiempoActual > 0 ? tiempoActual - 1 : 0;
        registrarEvento(null, "FIN_SIMULACION", "La simulación ha terminado en t=" + tiempoFinal);
        calcularMetricasFinales();
    }

    /**
     * Ejecuta un ciclo (tick de reloj) de la simulación.
     */
    private void ejecutarCiclo() {
        // 1. Actualizar procesos bloqueados (si terminan su E/S → se re-encolan en la fila principal)
        actualizarColaBloqueados();
        
        // 2. Procesar llegada de nuevos procesos (cuando arriban → se encolan en la fila principal)
        procesarLlegadas();
        
        // 3. Gestionar la CPU
        gestionarCPU();

        // 4. Aumentar contador de espera en la fila principal
        for (Proceso p : colaPrincipal.getCola()) {
            p.setTiempoEnEstadoListo(p.getTiempoEnEstadoListo() + 1);
        }

        // 5. Verificar condición de fin
        verificarCondicionDeFin();
        
        // 6. Avanzar el tiempo
        if (!simulacionTerminada) {
            tiempoActual++;
        }
    }

    /**
     * Marca los procesos que llegan al sistema y los encola en la fila principal.
     */
    private void procesarLlegadas() {
        for (Proceso p : procesos) {
            if (p.getEstado().equals("NO_LLEGADO") && p.getTiempoArribo() <= tiempoActual) {
                p.setEstado("NUEVO");
                colaPrincipal.agregar(p); // Se añade a la fila única.
                registrarEvento(p.getPid(), "ARRIBO_PROCESO", "El proceso " + p.getNombre() + " ha arribado y se encola.");
            }
        }
    }

    /**
     * Disminuye tiempos de E/S y mueve procesos a la fila principal cuando corresponde.
     */
    private void actualizarColaBloqueados() {
        List<Proceso> desbloqueados = new ArrayList<>();
        for (Proceso p : colaBloqueados) {
            p.setTiempoRestanteES(p.getTiempoRestanteES() - 1);

            if (p.getTiempoRestanteES() <= 0) {
                p.setEstado("LISTO"); 
                colaPrincipal.agregar(p); // Se re-encola en la fila única, al final.
                desbloqueados.add(p);
                registrarEvento(p.getPid(), "BLOQUEADO_A_LISTO", "Proceso " + p.getNombre() + " terminó E/S y se re-encola.");
            }
        }
        colaBloqueados.removeAll(desbloqueados);
    }

    /**
     * Lógica central de gestión de la CPU con el modelo de Fila Única y ejecución post-TIP inmediata.
     */
    private void gestionarCPU() {
        // --- PARTE 1: GESTIONAR LA CPU SI ESTÁ OCUPADA ---

        // Atendiendo TIP (Tiempo de Ingreso de Proceso)
        if (cpu.getTiempoRestanteTIP() > 0) {
            cpu.setTiempoRestanteTIP(cpu.getTiempoRestanteTIP() - 1);
            metricas.incrementarTiempoCPU_OS();
            if (cpu.getTiempoRestanteTIP() == 0) {
                Proceso p = cpu.getProcesoADespachar();
                if (p != null) {
                    registrarEvento(p.getPid(), "FIN_TIP", "Proceso " + p.getNombre() + " completó TIP.");
                    p.setEstado("LISTO");
                    despacharProceso(p); // Inmediatamente se despacha para ejecución (inicia TCP).
                }
            }
            return; 
        }

        // Atendiendo TCP/TFP (Tiempo de Cambio de Proceso / Fin de Proceso)
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
            return;
        }

        // Ejecutando un proceso de usuario
        if (!cpu.estaOciosa()) {
            Proceso actual = cpu.getProcesoActual();
            actual.setTiempoRestanteRafagaCPU(actual.getTiempoRestanteRafagaCPU() - 1);
            cpu.setQuantumRestante(cpu.getQuantumRestante() - 1);

            if (actual.getTiempoRestanteRafagaCPU() <= 0) {
                actual.setRafagasRestantes(actual.getRafagasRestantes() - 1);
                registrarEvento(actual.getPid(), "FIN_RAFAGA_CPU", "Proceso " + actual.getNombre() + " terminó ráfaga de CPU.");
                if (actual.getRafagasRestantes() <= 0) {
                    actual.setEstado("TERMINADO");
                    actual.setTiempoFinEjecucion(tiempoActual);
                    registrarEvento(actual.getPid(), "PROCESO_TERMINADO", "Proceso " + actual.getNombre() + " ha finalizado.");
                    cpu.liberar();
                    cpu.setTiempoRestanteTCP(params.getTfp());
                    cpu.setProcesoADespachar(null);
                } else {
                    actual.setEstado("BLOQUEADO");
                    actual.setTiempoRestanteES(actual.getDuracionRafagaES());
                    actual.setTiempoRestanteRafagaCPU(actual.getDuracionRafagaCPU());
                    colaBloqueados.add(actual);
                    registrarEvento(actual.getPid(), "EJECUCION_A_BLOQUEADO", "Proceso " + actual.getNombre() + " inicia E/S.");
                    cpu.liberar();
                }
            } else if (cpu.getQuantumRestante() <= 0) {
                actual.setEstado("LISTO");
                colaPrincipal.agregar(actual);
                registrarEvento(actual.getPid(), "FIN_QUANTUM", "Proceso " + actual.getNombre() + " vuelve a la fila por fin de quantum.");
                cpu.liberar();
            }
        }

        // --- PARTE 2: DECIDIR QUÉ HACER SI LA CPU ESTÁ LIBRE ---
        if (cpu.estaOciosa()) {
            Proceso proximo = colaPrincipal.quitar(); 

            if (proximo != null) {
                if (proximo.getEstado().equals("NUEVO")) {
                    // Si es NUEVO, necesita ser admitido (pagar TIP).
                    cpu.setProcesoADespachar(proximo);
                    cpu.setTiempoRestanteTIP(params.getTip());
                    registrarEvento(proximo.getPid(), "INICIO_TIP", "Proceso " + proximo.getNombre() + " es seleccionado para admisión (TIP).");
                    metricas.incrementarTiempoCPU_OS();
                } else { // Si no es NUEVO, es LISTO.
                    // Si está LISTO, necesita ejecutarse (pagar TCP).
                    despacharProceso(proximo);
                    metricas.incrementarTiempoCPU_OS();
                }
            } else {
                // La fila principal está vacía, no hay nada que hacer.
                metricas.incrementarTiempoCPUDesocupada();
            }
        }
    }

    /**
     * Inicia un cambio de contexto antes de ejecutar un proceso.
     */
    private void despacharProceso(Proceso p) {
        cpu.setProcesoADespachar(p); // Guardamos el proceso a despachar
        cpu.setTiempoRestanteTCP(params.getTcp()); // Asignamos el tiempo de cambio de contexto
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
            double trn = (p.getCantidadRafagasCPU() * p.getDuracionRafagaCPU() > 0) ?
                         (double) tr / (p.getCantidadRafagasCPU() * p.getDuracionRafagaCPU()) : 0;

            sumaTR += tr;
            sumaTRN += trn;

            System.out.println("Proceso " + p.getPid() + " (" + p.getNombre() + "):");
            System.out.println("  TRp = " + tr);
            System.out.println("  TRn = " + String.format("%.2f", trn));
            System.out.println("  Tiempo en Fila = " + p.getTiempoEnEstadoListo());
        }

        int trt = procesos.stream().mapToInt(Proceso::getTiempoFinEjecucion).max().orElse(0)
                  - procesos.stream().mapToInt(Proceso::getTiempoArribo).min().orElse(0);
        double tmrt = (procesos.isEmpty()) ? 0 : (double) sumaTR / procesos.size();

        metricas.setTiempoRetornoTanda(trt);
        metricas.setTiempoMedioRetornoTanda(tmrt);

        System.out.println("\n==== METRICAS DE LA TANDA ====");
        System.out.println("TRt = " + trt);
        System.out.println("TMRt = " + String.format("%.2f", tmrt));

        System.out.println("\n==== USO DE CPU ====");
        int totalTiempo = tiempoActual > 0 ? tiempoActual - 1 : 0;
        int cpuProc = totalTiempo - (metricas.getTiempoCPUDesocupada() + metricas.getTiempoCPU_OS());
        System.out.println("Tiempo Total = " + totalTiempo);
        System.out.println("CPU Desocupada = " + metricas.getTiempoCPUDesocupada());
        System.out.println("CPU SO = " + metricas.getTiempoCPU_OS());
        System.out.println("CPU Procesos = " + cpuProc);
        
        if (totalTiempo > 0) {
            System.out.println("Porcentajes: Desocupada=" 
                + String.format("%.2f", (100.0 * metricas.getTiempoCPUDesocupada() / totalTiempo)) + "%, SO=" 
                + String.format("%.2f", (100.0 * metricas.getTiempoCPU_OS() / totalTiempo)) + "%, Procesos=" 
                + String.format("%.2f", (100.0 * cpuProc / totalTiempo)) + "%");
        }
    }

    // --- Getters ---
    public List<Evento> getLog() {
        return log;
    }

    public Metricas getMetricas() {
        return metricas;
    }
}