package com.simulador.models;


//Almacena los parámetros de configuración del sistema operativo (TIP, TFP, etc.).
public class SystemParams {

    private int tip; //Tiempo de ingreso de proceso
    private int tfp; //Tiempo de finalización de proceso
    private int tcp; //Tiempo de cambio de proceso
    private int quantum; //Para Round Robin

    public SystemParams(int tip, int tfp, int tcp, int quantum) {
        this.tip = tip;
        this.tfp = tfp;
        this.tcp = tcp;
        this.quantum = quantum;
    }
    //Getters y Setters
    public int getTip() {
        return tip;
    }

    public void setTip(int tip) {
        this.tip = tip;
    }

    public int getTfp() {
        return tfp;
    }

    public void setTfp(int tfp) {
        this.tfp = tfp;
    }

    public int getTcp() {
        return tcp;
    }

    public void setTcp(int tcp) {
        this.tcp = tcp;
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        this.quantum = quantum;
    }

    
}