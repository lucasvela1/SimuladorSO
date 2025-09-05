package com.simulador.models;

/**
 * Almacena y calcula las métricas de rendimiento de la simulación.
 */
public class Metricas {

    private int tiempoCPUDesocupada;
    private int tiempoCPU_OS; // Tiempo consumido por TIP, TFP, TCP
    
    // Métricas de la tanda
    private int tiempoRetornoTanda;
    private double tiempoMedioRetornoTanda;

    public Metricas() {
        this.tiempoCPUDesocupada = 0;
        this.tiempoCPU_OS = 0;
        this.tiempoRetornoTanda = 0;
        this.tiempoMedioRetornoTanda = 0.0;
    }

    public void incrementarTiempoCPUDesocupada() {
        this.tiempoCPUDesocupada++;
    }

    public int getTiempoCPUDesocupada() {
        return tiempoCPUDesocupada;
    }

    public void setTiempoCPUDesocupada(int tiempoCPUDesocupada) {
        this.tiempoCPUDesocupada = tiempoCPUDesocupada;
    }

    public int getTiempoCPU_OS() {
        return tiempoCPU_OS;
    }

    public void setTiempoCPU_OS(int tiempoCPU_OS) {
        this.tiempoCPU_OS = tiempoCPU_OS;
    }

    public int getTiempoRetornoTanda() {
        return tiempoRetornoTanda;
    }

    public void setTiempoRetornoTanda(int tiempoRetornoTanda) {
        this.tiempoRetornoTanda = tiempoRetornoTanda;
    }

    public double getTiempoMedioRetornoTanda() {
        return tiempoMedioRetornoTanda;
    }

    public void setTiempoMedioRetornoTanda(double tiempoMedioRetornoTanda) {
        this.tiempoMedioRetornoTanda = tiempoMedioRetornoTanda;
    }

    public void incrementarTiempoCPU_OS() {
        this.tiempoCPU_OS++;
    }
    
    
}