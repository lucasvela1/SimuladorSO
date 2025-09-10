package com.simulador;

import java.util.ArrayList;
import java.util.List;

import com.simulador.models.ColaListos;
import com.simulador.models.ColaListosPrioridad;
import com.simulador.models.ColaListosSPN;
import com.simulador.models.ColaListosSRT;
import com.simulador.models.EstadoCPU;
import com.simulador.models.Evento;
import com.simulador.models.Metricas;
import com.simulador.models.Proceso;
import com.simulador.models.SystemParams;
import com.simulador.scheduler.Planificador;
import com.simulador.scheduler.PrioridadExterna;
import com.simulador.scheduler.RoundRobin;
import com.simulador.scheduler.SPN;
import com.simulador.scheduler.SRTN;

public class Simulador {

    private int tiempoActual;
    private List<Proceso> procesos;
    private SystemParams params;
    private Planificador planificador;
    private EstadoCPU cpu;
    private ColaListos colaPrincipal;
    private List<Proceso> colaBloqueados;
    private List<Evento> log;
    private Metricas metricas;
    private boolean simulacionTerminada;

    public Simulador(List<Proceso> procesos, Planificador planificador, SystemParams params) {
        this.tiempoActual = 0;
        this.procesos = procesos;
        this.planificador = planificador;
        this.params = params;

        if (planificador instanceof SRTN) {
            this.colaPrincipal = new ColaListosSRT();
        } else if (planificador instanceof PrioridadExterna) {
            this.colaPrincipal = new ColaListosPrioridad();
        } else if (planificador instanceof SPN){
            this.colaPrincipal = new ColaListosSPN();
        } else {
            this.colaPrincipal = new ColaListos();
        }
        
        this.cpu = new EstadoCPU();
        this.colaBloqueados = new ArrayList<>();
        this.log = new ArrayList<>();
        this.metricas = new Metricas();
        this.simulacionTerminada = false;
    }

    public void iniciar() {
        registrarEvento(null, "INICIO_SIMULACION", "La simulación ha comenzado.");
        while (!simulacionTerminada) {
            ejecutarCiclo();
        }
        int tiempoFinal = tiempoActual > 0 ? tiempoActual - 1 : 0;
        registrarEvento(null, "FIN_SIMULACION", "La simulación ha terminado en t=" + tiempoFinal);
        calcularMetricasFinales(); 
    }

    private void ejecutarCiclo() {
        actualizarColaBloqueados();
        if (planificador.esExpropiativo()) {
            verificarInterrupcion();
        }

        procesarLlegadas();
        if (planificador.esExpropiativo()) {
            verificarInterrupcion();
        }

        gestionarCPU();

        for (Proceso p : colaPrincipal.getCola()) {
            p.setTiempoEnEstadoListo(p.getTiempoEnEstadoListo() + 1);
        }

        verificarCondicionDeFin();
        
        if (!simulacionTerminada) {
            tiempoActual++;
        }
    }

    private void verificarInterrupcion() {
        if (planificador instanceof RoundRobin) {
            return;
        }

        if (!cpu.estaOciosa() && cpu.getTiempoRestanteTIP() == 0 && cpu.getTiempoRestanteTCP() == 0) {
            Proceso actual = cpu.getProcesoActual();
            Proceso proximoEnCola = colaPrincipal.verSiguiente();
            boolean debeInterrumpir = false;

            if (proximoEnCola != null) {
                if (planificador instanceof SRTN) {
                    if (proximoEnCola.getTiempoRestanteRafagaCPU() < actual.getTiempoRestanteRafagaCPU()) {
                        debeInterrumpir = true;
                    }
                } else if (planificador instanceof PrioridadExterna) {
                    if (proximoEnCola.getPrioridadExterna() < actual.getPrioridadExterna()) {
                        debeInterrumpir = true;
                    }
                }
            }

            if (debeInterrumpir) {
                registrarEvento(actual.getPid(), "INTERRUPCION", "Proceso " + actual.getNombre() + " interrumpido por " + proximoEnCola.getNombre());
                actual.setEstado("LISTO");
                colaPrincipal.agregar(actual);
                cpu.liberar();
            }
        }
    }

    private void procesarLlegadas() {
        for (Proceso p : procesos) {
            if (p.getEstado().equals("NO_LLEGADO") && p.getTiempoArribo() <= tiempoActual) {
                p.setEstado("NUEVO");
                colaPrincipal.agregar(p);
                registrarEvento(p.getPid(), "ARRIBO_PROCESO", "El proceso " + p.getNombre() + " ha arribado y se encola.");
            }
        }
    }

    private void actualizarColaBloqueados() {
        List<Proceso> desbloqueados = new ArrayList<>();
        for (Proceso p : colaBloqueados) {
            p.setTiempoRestanteES(p.getTiempoRestanteES() - 1);
            if (p.getTiempoRestanteES() <= 0) {
                p.setEstado("LISTO");
                colaPrincipal.agregar(p);
                desbloqueados.add(p);
                registrarEvento(p.getPid(), "BLOQUEADO_A_LISTO", "Proceso " + p.getNombre() + " terminó E/S y se re-encola.");
            }
        }
        colaBloqueados.removeAll(desbloqueados);
    }

    private void gestionarCPU() {
        if (cpu.getTiempoRestanteTIP() > 0) {
            cpu.setTiempoRestanteTIP(cpu.getTiempoRestanteTIP() - 1);
            metricas.incrementarTiempoCPU_OS();
            if (cpu.getTiempoRestanteTIP() == 0) {
                Proceso p = cpu.getProcesoADespachar();
                if (p != null) {
                    registrarEvento(p.getPid(), "FIN_TIP", "Proceso " + p.getNombre() + " completó TIP. Verificando planificador.");
                    p.setEstado("LISTO");
                    Proceso ganador = decidirProximoIncumbente(p);
                    iniciarDespachoOAdmision(ganador);
                }
            }
            return; 
        }

        if (cpu.getTiempoRestanteTCP() > 0) {
            cpu.setTiempoRestanteTCP(cpu.getTiempoRestanteTCP() - 1);
            metricas.incrementarTiempoCPU_OS();
            if (cpu.getTiempoRestanteTCP() == 0) {
                Proceso p = cpu.getProcesoADespachar();
                if (p != null) { // Fin de un TCP
                    Proceso ganador = decidirProximoIncumbente(p);
                    if (ganador.getPid() == p.getPid()) {
                        cpu.asignarProceso(ganador, params.getQuantum());
                        registrarEvento(ganador.getPid(), "DESPACHO_PROCESO", "Proceso " + ganador.getNombre() + " pasa a ejecución.");
                    } else {
                        iniciarDespachoOAdmision(ganador);
                    }
                    return; // Retornar después de tomar la decisión post-TCP
                }
                // Si p es null, fue un TFP. No retornamos para que se busque trabajo inmediatamente.
            } else {
                return; // TCP/TFP sigue en curso.
            }
        }

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
                    return;
                } else {
                    actual.setEstado("BLOQUEADO");
                    actual.setTiempoRestanteES(actual.getDuracionRafagaES());
                    actual.setTiempoRestanteRafagaCPU(actual.getDuracionRafagaCPU());
                    colaBloqueados.add(actual);
                    registrarEvento(actual.getPid(), "EJECUCION_A_BLOQUEADO", "Proceso " + actual.getNombre() + " inicia E/S.");
                    cpu.liberar();
                }
            } else if (cpu.getQuantumRestante() <= 0 && planificador instanceof RoundRobin) {
                actual.setEstado("LISTO");
                colaPrincipal.agregar(actual);
                registrarEvento(actual.getPid(), "FIN_QUANTUM", "Proceso " + actual.getNombre() + " vuelve a la fila por fin de quantum.");
                cpu.liberar();
            }
        }

        if (cpu.estaOciosa()) {
            Proceso proximo = colaPrincipal.quitar();
            if(proximo != null) {
                iniciarDespachoOAdmision(proximo);
            } else {
                metricas.incrementarTiempoCPUDesocupada();
            }
        }
    }

    private Proceso decidirProximoIncumbente(Proceso p) {
        if (!planificador.esExpropiativo()) {
            return p;
        }

        Proceso proximoEnCola = colaPrincipal.verSiguiente();
        boolean debeSerExpropiado = false;
        if (proximoEnCola != null) {
            if (planificador instanceof PrioridadExterna) {
                if (proximoEnCola.getPrioridadExterna() < p.getPrioridadExterna()) debeSerExpropiado = true;
            } else if (planificador instanceof SRTN) {
                if (proximoEnCola.getTiempoRestanteRafagaCPU() < p.getTiempoRestanteRafagaCPU()) debeSerExpropiado = true;
            }
        }

        if (debeSerExpropiado) {
            registrarEvento(p.getPid(), "INCUMBENTE_EXPROPIADO", "Proceso " + p.getNombre() + " es expropiado por " + proximoEnCola.getNombre());
            colaPrincipal.agregar(p);
            return colaPrincipal.quitar();
        } else {
            return p;
        }
    }

    private void iniciarDespachoOAdmision(Proceso p) {
        registrarEvento(p.getPid(), "PROCESO_SELECCIONADO", "Proceso " + p.getNombre() + " seleccionado por el planificador.");
        if (p.getEstado().equals("NUEVO")) {
            cpu.setProcesoADespachar(p);
            cpu.setTiempoRestanteTIP(params.getTip());
            registrarEvento(p.getPid(), "INICIO_TIP", "Proceso " + p.getNombre() + " es seleccionado para admisión (TIP).");
        } else { 
            despacharProceso(p);
        }
    }

    private void despacharProceso(Proceso p) {
        cpu.setProcesoADespachar(p);
        cpu.setTiempoRestanteTCP(params.getTcp());
        registrarEvento(p.getPid(), "INICIO_TCP", "Iniciando cambio de contexto para " + p.getNombre());
    }
    
    private void verificarCondicionDeFin() {
        long procesosTerminados = procesos.stream().filter(p -> p.getEstado().equals("TERMINADO")).count();
        if (procesosTerminados == procesos.size() && cpu.estaOciosa() && cpu.getTiempoRestanteTCP() == 0 && cpu.getTiempoRestanteTIP() == 0) {
            this.simulacionTerminada = true;
        }
    }

    private void registrarEvento(Integer pid, String tipo, String mensaje) {
        this.log.add(new Evento(tiempoActual, pid, tipo, mensaje));
    }

    private void calcularMetricasFinales() {
        int sumaTR = 0;
        for (Proceso p : procesos) {
            int tr = p.getTiempoFinEjecucion() - p.getTiempoArribo();
            sumaTR += tr;
        }
        int trt = procesos.stream().mapToInt(Proceso::getTiempoFinEjecucion).max().orElse(0)
                  - procesos.stream().mapToInt(Proceso::getTiempoArribo).min().orElse(0);
        double tmrt = (procesos.isEmpty()) ? 0 : (double) sumaTR / procesos.size();
        metricas.setTiempoRetornoTanda(trt);
        metricas.setTiempoMedioRetornoTanda(tmrt); 
    }

    //Getters
    public List<Evento> getLog() { return log; }
    public Metricas getMetricas() { return metricas; }
    public List<Proceso> getProcesos() { return procesos; }
}