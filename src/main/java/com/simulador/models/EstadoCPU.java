package com.simulador.models;

/**
 * Modela el estado actual del procesador (CPU).
 */
public class EstadoCPU {

    private Proceso procesoActual;
    private int quantumRestante;
    private int tiempoRestanteTCP;
    private Proceso procesoADespachar;

    public EstadoCPU() {
        this.procesoActual = null;
        this.quantumRestante = 0;
        this.tiempoRestanteTCP = 0;
        this.procesoADespachar = null;
    }

    public boolean estaOciosa() {
        return procesoActual == null;
    }

    public void asignarProceso(Proceso proceso, int quantum) {
        this.procesoActual = proceso;
        this.quantumRestante = quantum;
        proceso.setEstado("EJECUCION");
    }

    public void liberar() {
        this.procesoActual = null;
        this.quantumRestante = 0;
    }

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

    public Proceso getProcesoADespachar() {
        return procesoADespachar;
    }

    public void setProcesoADespachar(Proceso procesoADespachar) {
        this.procesoADespachar = procesoADespachar;
    }

    
}