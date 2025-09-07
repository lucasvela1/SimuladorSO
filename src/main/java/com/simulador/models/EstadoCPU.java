package com.simulador.models;

/**
 * Modela el estado actual del procesador.
 * Puede estar ejecutando un proceso de usuario o realizando
 * tareas del sistema operativo (TIP, TCP, TFP).
 */
public class EstadoCPU {

    private Proceso procesoActual;       // Proceso actualmente en ejecución (usuario)
    private int quantumRestante;         // Quantum restante del proceso actual
    private int tiempoRestanteTCP;       // Tiempo restante de cambio de contexto (TCP o TFP)
    private int tiempoRestanteTIP;       // Tiempo restante de ingreso de proceso (TIP)
    private Proceso procesoADespachar;   // Proceso que está por ser asignado al CPU

    public EstadoCPU() {
        this.procesoActual = null;
        this.quantumRestante = 0;
        this.tiempoRestanteTCP = 0;
        this.tiempoRestanteTIP = 0;
        this.procesoADespachar = null;
    }

    /**
     * Indica si el CPU está libre de ejecutar procesos de usuario.
     */
    public boolean estaOciosa() {
        return procesoActual == null;
    }

    /**
     * Asigna un proceso al CPU para su ejecución (estado EJECUCION).
     * El quantum empieza a descontarse desde aquí.
     */
    public void asignarProceso(Proceso proceso, int quantum) {
        this.procesoActual = proceso;
        this.quantumRestante = quantum;
        proceso.setEstado("EJECUCION");
    }

    /**
     * Libera el CPU del proceso actual (ej. fin de ráfaga, bloqueo, terminación).
     */
    public void liberar() {
        this.procesoActual = null;
        this.quantumRestante = 0;
    }

    // --- Getters y Setters ---

    public Proceso getProcesoActual() {
        return procesoActual;
    }

    public void setProcesoActual(Proceso procesoActual) {
        this.procesoActual = procesoActual;
    }

    public int getQuantumRestante() {
        return quantumRestante;
    }

    public void setQuantumRestante(int quantumRestante) {
        this.quantumRestante = quantumRestante;
    }

    public int getTiempoRestanteTCP() {
        return tiempoRestanteTCP;
    }

    public void setTiempoRestanteTCP(int tiempoRestanteTCP) {
        this.tiempoRestanteTCP = tiempoRestanteTCP;
    }

    public int getTiempoRestanteTIP() {
        return tiempoRestanteTIP;
    }

    public void setTiempoRestanteTIP(int tiempoRestanteTIP) {
        this.tiempoRestanteTIP = tiempoRestanteTIP;
    }

    public Proceso getProcesoADespachar() {
        return procesoADespachar;
    }

    public void setProcesoADespachar(Proceso procesoADespachar) {
        this.procesoADespachar = procesoADespachar;
    }
}
