package com.simulador.models;


//Representa un evento ocurrido en un instante de tiempo específico durante la simulación.
public class Evento {

    private int tiempo;
    private Integer pid; //Puede ser nulo si el evento es del sistema
    private String tipoEvento; //Ejemplos: "ARRIBO", "FIN_CPU", "FIN_ES", "TIP", "TFP", "TCP"
    private String mensaje;

    public Evento(int tiempo, Integer pid, String tipoEvento, String mensaje) {
        this.tiempo = tiempo;
        this.pid = pid;
        this.tipoEvento = tipoEvento;
        this.mensaje = mensaje;
    }
    
    @Override
    public String toString() {
        String pidStr = (pid != null) ? "PID(" + pid + ")" : "Sistema";
        return String.format("t=%-4d | %-10s | %-18s | %s", tiempo, pidStr, tipoEvento, mensaje);
    }
    //Getters y Setters
    public int getTiempo() {
        return tiempo;
    }

    public void setTiempo(int tiempo) {
        this.tiempo = tiempo;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
    
    
}