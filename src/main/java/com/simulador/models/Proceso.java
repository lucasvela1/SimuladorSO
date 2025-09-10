package com.simulador.models;

import com.google.gson.annotations.SerializedName;

//SerializedName es una instruccion para Gson para mapear los nombres de los atributos JSON a los atributos Java
public class Proceso {
    @SerializedName("nombre")
    private String nombre;

    @SerializedName("tiempo_arribo")
    private int tiempoArribo;

    @SerializedName("cantidad_rafagas_cpu")
    private int cantidadRafagasCPU;

    @SerializedName("duracion_rafaga_cpu")
    private int duracionRafagaCPU;

    @SerializedName("duracion_rafaga_es")
    private int duracionRafagaES; 

    @SerializedName("prioridad_externa")
    private int prioridadExterna;
    
    //Atributos de estado
    private int rafagasRestantes;
    private int tiempoRestanteRafagaCPU;
    private int tiempoRestanteES;
    private int pid;
    private String estado;
    private int tiempoFinEjecucion;
    private int tiempoEnEstadoListo;
    private int tiempoRestanteTIP;
    private boolean fueInterrumpido;

    public Proceso() {
        //Vacio para el Gson    
    }

    public Proceso(Proceso otro) {
      //Copiamos los atributos de definición
      this.nombre = otro.nombre;
      this.tiempoArribo = otro.tiempoArribo;
      this.cantidadRafagasCPU = otro.cantidadRafagasCPU;
      this.duracionRafagaCPU = otro.duracionRafagaCPU;
      this.duracionRafagaES = otro.duracionRafagaES;
      this.prioridadExterna = otro.prioridadExterna;

      //También copiamos los atributos de estado 
      this.pid = otro.pid;
      this.estado = otro.estado;
      this.rafagasRestantes = otro.rafagasRestantes;
      this.tiempoRestanteRafagaCPU = otro.tiempoRestanteRafagaCPU;
      this.tiempoRestanteES = otro.tiempoRestanteES;
      this.tiempoRestanteTIP = otro.tiempoRestanteTIP;
      this.fueInterrumpido = otro.fueInterrumpido;
    
      //métricas
      this.tiempoFinEjecucion = otro.tiempoFinEjecucion;
      this.tiempoEnEstadoListo = otro.tiempoEnEstadoListo;
    }


    public void inicializarParaSimulacion(int pid) {
        this.pid = pid;
        this.estado = "NO_LLEGADO";
        this.rafagasRestantes = this.cantidadRafagasCPU;
        this.tiempoRestanteRafagaCPU = this.duracionRafagaCPU;
        this.tiempoRestanteES = this.duracionRafagaES;
        this.tiempoFinEjecucion = 0;
        this.tiempoEnEstadoListo = 0;
        this.tiempoRestanteTIP = 0;
        this.fueInterrumpido = false;
    }
    
    //Getters y Setters
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
    
    public boolean GetfueInterrumpido() {
        return fueInterrumpido;
    }

    public void setFueInterrumpido(boolean fueInterrumpido) {
        this.fueInterrumpido = fueInterrumpido;
    }
}