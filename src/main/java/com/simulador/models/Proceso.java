package com.simulador.models;

public class Proceso {
    private String nombre;
    private int tiempoArribo;
    private int cantidadRafagasCPU;
    private int duracionRafagaCPU;
    private int duracionRafagaES; 
    private int prioridadExterna;
    private int rafagasRestantes;
    private int tiempoRestanteRafagaCPU;
    private int tiempoRestanteES;
    private int pid; // Process ID
    private String estado; //  "NUEVO", "LISTO", "EJECUCION", "BLOQUEADO", "TERMINADO"
    private int tiempoFinEjecucion;
    private int tiempoEnEstadoListo;
    private int tiempoRestanteTIP;


    public void inicializarParaSimulacion(int pid) {
        this.pid = pid;
        this.estado = "NO_LLEGADO";
        this.rafagasRestantes = this.cantidadRafagasCPU;
        this.tiempoRestanteRafagaCPU = this.duracionRafagaCPU;
        this.tiempoRestanteES = this.duracionRafagaES;
        this.tiempoFinEjecucion = 0;
        this.tiempoEnEstadoListo = 0;
        this.tiempoRestanteTIP = 0;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getTiempoArribo() {
        return tiempoArribo;
    }

    public void setTiempoArribo(int tiempoArribo) {
        this.tiempoArribo = tiempoArribo;
    }

    public int getCantidadRafagasCPU() {
        return cantidadRafagasCPU;
    }

    public void setCantidadRafagasCPU(int cantidadRafagasCPU) {
        this.cantidadRafagasCPU = cantidadRafagasCPU;
    }

    public int getDuracionRafagaCPU() {
        return duracionRafagaCPU;
    }

    public void setDuracionRafagaCPU(int duracionRafagaCPU) {
        this.duracionRafagaCPU = duracionRafagaCPU;
    }

    public int getDuracionRafagaES() {
        return duracionRafagaES;
    }

    public void setDuracionRafagaES(int duracionRafagaES) {
        this.duracionRafagaES = duracionRafagaES;
    }

    public int getPrioridadExterna() {
        return prioridadExterna;
    }

    public void setPrioridadExterna(int prioridadExterna) {
        this.prioridadExterna = prioridadExterna;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public int getRafagasRestantes() {
        return rafagasRestantes;
    }

    public void setRafagasRestantes(int rafagasRestantes) {
        this.rafagasRestantes = rafagasRestantes;
    }

    public int getTiempoRestanteRafagaCPU() {
        return tiempoRestanteRafagaCPU;
    }

    public void setTiempoRestanteRafagaCPU(int tiempoRestanteRafagaCPU) {
        this.tiempoRestanteRafagaCPU = tiempoRestanteRafagaCPU;
    }

    public int getTiempoRestanteES() {
        return tiempoRestanteES;
    }

    public void setTiempoRestanteES(int tiempoRestanteES) {
        this.tiempoRestanteES = tiempoRestanteES;
    }

    public int getTiempoFinEjecucion() {
        return tiempoFinEjecucion;
    }

    public void setTiempoFinEjecucion(int tiempoFinEjecucion) {
        this.tiempoFinEjecucion = tiempoFinEjecucion;
    }

    public int getTiempoEnEstadoListo() {
        return tiempoEnEstadoListo;
    }

    public void setTiempoEnEstadoListo(int tiempoEnEstadoListo) {
        this.tiempoEnEstadoListo = tiempoEnEstadoListo;
    }

    public int getTiempoRestanteTIP() {
        return tiempoRestanteTIP;
    }

    public void setTiempoRestanteTIP(int tiempoRestanteTIP) {
        this.tiempoRestanteTIP = tiempoRestanteTIP;
    }
    

   
}