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
    private final List<Proceso> procesos;
    private final SystemParams params;
    private final Planificador planificador;
    private final EstadoCPU cpu;
    private final ColaListos colaPrincipal;
    private final List<Proceso> colaBloqueados;
    private final List<Evento> log;
    private final Metricas metricas; //Pongo final porque se asigna en el constructor y no cambia más, pero se le pueden cambiar sus atributos
    private boolean simulacionTerminada;
    private Proceso ultimoProcesoTerminado;

    public Simulador(List<Proceso> procesos, Planificador planificador, SystemParams params) {
        this.tiempoActual = 0;
        this.procesos = procesos;
        this.planificador = planificador;
        this.params = params;
        this.ultimoProcesoTerminado = null;

        if (planificador instanceof SRTN) {
            this.colaPrincipal = new ColaListosSRT();
        } else if (planificador instanceof PrioridadExterna) {
            this.colaPrincipal = new ColaListosPrioridad();
        } else if (planificador instanceof SPN) {
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
        int tiempoFinal = tiempoActual;
        registrarEvento(null, "FIN_SIMULACION", "La simulación ha terminado en t=" + tiempoFinal);
        calcularMetricasFinales();
    }

    private void ejecutarCiclo() {
        //Actualizar llegadas
        procesarLlegadas();
        actualizarColaBloqueados();
        //Verificar si el nuevo estado causa una interrupción.
        if (planificador.esExpropiativo()) {
            verificarInterrupcion();
        }
        //Gestionar la CPU con la información más reciente.
        gestionarCPU();

        //El que sale del bloqueo recién interrumpe en el siguiente ciclo (porque sale en el mismo ciclo que consume si no)


        //Actualizar métricas de espera y avanzar el tiempo.
        for (Proceso p : colaPrincipal.getCola()) {
            p.setTiempoEnEstadoListo(p.getTiempoEnEstadoListo() + 1);
        }

        verificarCondicionDeFin();

        if (!simulacionTerminada) {
            tiempoActual++;
        }
    }
    
    private void gestionarCPU() {
        // Manejar TIP (Tiempo de Ingreso de Proceso)
        if (cpu.getTiempoRestanteTIP() > 0) {
            cpu.setTiempoRestanteTIP(cpu.getTiempoRestanteTIP() - 1);
            metricas.incrementarTiempoCPU_OS();
            if (cpu.getTiempoRestanteTIP() == 0) {
                Proceso p = cpu.getProcesoADespachar();
                registrarEvento(p.getPid(), "FIN_TIP", "Proceso " + p.getNombre() + " completó TIP.");
                p.setEstado("LISTO");
                Proceso ganador = decidirProximoIncumbente(p);
                iniciarDespachoOAdmision(ganador);
            }
            return;
        }

        // Manejar TCP (Cambio de Contexto) y TFP (Finalización)
        if (cpu.getTiempoRestanteTCP() > 0) {
            cpu.setTiempoRestanteTCP(cpu.getTiempoRestanteTCP() - 1);
            metricas.incrementarTiempoCPU_OS();
            if (cpu.getTiempoRestanteTCP() == 0) {
                Proceso p = cpu.getProcesoADespachar();
                if (p != null) { //Fin de un TCP
                    Proceso ganador = decidirProximoIncumbente(p);
                    if (ganador == p) {
                        cpu.asignarProceso(ganador, params.getQuantum());
                        registrarEvento(ganador.getPid(), "DESPACHO_PROCESO", "Proceso " + ganador.getNombre() + " pasa a ejecución.");
                        //NO hay return para que la ejecución comience en este mismo ciclo.
                    } else {
                        iniciarDespachoOAdmision(ganador);
                        return; //Hay cambio de incumbente, se inicia otro overhead y se sale.
                    }
                } else { // Fin de un TFP
                    if (this.ultimoProcesoTerminado != null) {
                        this.ultimoProcesoTerminado.setTiempoFinEjecucion(tiempoActual);
                        registrarEvento(this.ultimoProcesoTerminado.getPid(), "FIN TFP", "Proceso " + this.ultimoProcesoTerminado.getNombre() + " ha finalizado TFP");
                        this.ultimoProcesoTerminado = null;
                    }
                    return; 
                }
            } else {
                return; // El TCP/TFP sigue en curso.
            }
        }

        //Ejecución normal de un proceso en CPU
        if (!cpu.estaOciosa()) {
            Proceso actual = cpu.getProcesoActual();
            actual.setTiempoRestanteRafagaCPU(actual.getTiempoRestanteRafagaCPU() - 1);
            registrarEvento(actual.getPid(), "EJECUCION", "Proceso " + actual.getNombre() + " resta ejecutar " + actual.getTiempoRestanteRafagaCPU());
            cpu.setQuantumRestante(cpu.getQuantumRestante() - 1);

            if (actual.getTiempoRestanteRafagaCPU() <= 0) { //Termino su rafaga
                actual.setRafagasRestantes(actual.getRafagasRestantes() - 1);
                registrarEvento(actual.getPid(), "FIN_RAFAGA_CPU", "Proceso " + actual.getNombre() + " terminó ráfaga de CPU.");
                
                cpu.liberar();
                //Verificamos si terminó porque se bloqueó o terminó
                if (actual.getRafagasRestantes() <= 0) {
                    actual.setEstado("TERMINADO");
                    this.ultimoProcesoTerminado = actual;
                    registrarEvento(actual.getPid(), "PROCESO_TERMINADO", "Proceso " + actual.getNombre() + " ha finalizado.");
                    cpu.setProcesoADespachar(null); //Marcar que el próximo es TFP
                    cpu.setTiempoRestanteTCP(params.getTfp());
                } else {
                    actual.setEstado("BLOQUEADO");
                    actual.setTiempoRestanteES(actual.getDuracionRafagaES()); //+1 porque se consume en el mismo ciclo
                    colaBloqueados.add(actual);
                    registrarEvento(actual.getPid(), "EJECUCION_A_BLOQUEADO", "Proceso " + actual.getNombre() + " inicia E/S.");
                }
            } else if (cpu.getQuantumRestante() <= 0 && planificador instanceof RoundRobin) { //Si no terminó su rafaga, pero si su quantum, se manda de nuevo a la cola
                actual.setEstado("LISTO");
                actual.setFueInterrumpido(true);
                colaPrincipal.agregar(actual);
                registrarEvento(actual.getPid(), "FIN_QUANTUM", "Proceso " + actual.getNombre() + " vuelve a la fila por fin de quantum.");
                cpu.liberar();
            }
            return; //Después de ejecutar, el trabajo de la CPU en este ciclo terminó.
        }

        //Si la CPU está Ociosa, buscar nuevo trabajo
        if (cpu.estaOciosa()) {
            Proceso proximo = colaPrincipal.quitar();
            if (proximo != null) {
                iniciarDespachoOAdmision(proximo);
            } else {
                metricas.incrementarTiempoCPUDesocupada();
            }
        }
    }
    
    private void verificarInterrupcion() {
        if (planificador instanceof RoundRobin) return; //Porque es premtivo pero usa el Quantum para interrumpir
        
        if (!cpu.estaOciosa() && cpu.getTiempoRestanteTIP() == 0 && cpu.getTiempoRestanteTCP() == 0) {
            Proceso actual = cpu.getProcesoActual();
            Proceso proximoEnCola = colaPrincipal.verSiguiente();
            boolean debeInterrumpir = false;
            if (proximoEnCola != null) {
                if (planificador instanceof SRTN && proximoEnCola.getTiempoRestanteRafagaCPU() < actual.getTiempoRestanteRafagaCPU()) {
                    debeInterrumpir = true;
                } else if (planificador instanceof PrioridadExterna && proximoEnCola.getPrioridadExterna() < actual.getPrioridadExterna()) {
                    debeInterrumpir = true;
                }
            }
            if (debeInterrumpir) {
                if (actual!=null && proximoEnCola != null) { //Validación solo para que no me salte el warning 
                  registrarEvento(actual.getPid(), "INTERRUPCION", "Proceso " + actual.getNombre() + " interrumpido por " + proximoEnCola.getNombre());
                  actual.setEstado("LISTO");
                  actual.setFueInterrumpido(true); //OBVIAMENTE, si la variable debeInterrumpir esta en verdadera es porque actual y proximo son NO NULL, pero salta el warning igual
                }              
                colaPrincipal.agregar(actual);
                cpu.liberar();
            }
        }
    }

    private Proceso decidirProximoIncumbente(Proceso p) {
        if (!planificador.esExpropiativo()) return p; //Si no es expropiativo, siempre sigue el mismo proceso.
        
        Proceso proximoEnCola = colaPrincipal.verSiguiente();
        boolean debeSerExpropiado = false;
        if (proximoEnCola != null) {
            if (planificador instanceof PrioridadExterna && proximoEnCola.getPrioridadExterna() < p.getPrioridadExterna()) {
                debeSerExpropiado = true; //Si la prioridad del de la cola es mayor (número menor), expropia. SOLO SI ES MAYOR, SI ES IGUAL SIGUE EL MISMO
            } else if (planificador instanceof SRTN && proximoEnCola.getTiempoRestanteRafagaCPU() < p.getTiempoRestanteRafagaCPU()) {
                debeSerExpropiado = true; //IDem arriba
            }
        }

        if (debeSerExpropiado) {
            if (p!=null && proximoEnCola != null){
              registrarEvento(p.getPid(), "INCUMBENTE_EXPROPIADO", "Proceso " + p.getNombre() + " es expropiado por " + proximoEnCola.getNombre());
              p.setFueInterrumpido(true);
            }
            colaPrincipal.agregar(p);
            return colaPrincipal.quitar();
        }
        return p;
    }

    private void iniciarDespachoOAdmision(Proceso p) {
        if (!p.GetfueInterrumpido()) { //Esto es para mantener la duración de rafaga que llevaba
            p.setTiempoRestanteRafagaCPU(p.getDuracionRafagaCPU()); //No fue interrumpido, arranca nueva ráfaga
        }
        p.setFueInterrumpido(false);

        registrarEvento(p.getPid(), "PROCESO_SELECCIONADO", "Proceso " + p.getNombre() + " seleccionado por el planificador.");
        
        cpu.setProcesoADespachar(p);
        if (p.getEstado().equals("NUEVO")) {
            cpu.setTiempoRestanteTIP(params.getTip());
            registrarEvento(p.getPid(), "INICIO_TIP", "Proceso " + p.getNombre() + " es seleccionado para admisión (TIP).");
        } else { //Si es nuevo le hacemos TIP, si no, TCP
            cpu.setTiempoRestanteTCP(params.getTcp());
            registrarEvento(p.getPid(), "INICIO_TCP", "Iniciando cambio de contexto para " + p.getNombre());
        }
    }

    private void procesarLlegadas() {
        for (Proceso p : procesos) {
            if (p.getEstado().equals("NO_LLEGADO") && p.getTiempoArribo() <= tiempoActual) {
                p.setEstado("NUEVO");
                colaPrincipal.agregar(p); //Verificamos todos los procesos disponibles para ver cuáles deberían arribar en el T actual
                registrarEvento(p.getPid(), "ARRIBO_PROCESO", "El proceso " + p.getNombre() + " ha arribado y se encola.");
            }
        }
    }

    private void actualizarColaBloqueados() {
        List<Proceso> desbloqueados = new ArrayList<>();
        for (Proceso p : colaBloqueados) {
            if (p.getTiempoRestanteES() <= 0) {
                p.setEstado("LISTO");
                if (!p.GetfueInterrumpido()){
                    p.setTiempoRestanteRafagaCPU(p.getDuracionRafagaCPU());
                }
                colaPrincipal.agregar(p);
                desbloqueados.add(p); //Pasa saber cuales sacar luego de la cola de bloqueados
                registrarEvento(p.getPid(), "BLOQUEADO_A_LISTO", "Proceso " + p.getNombre() + " terminó E/S y se re-encola.");
            }
        }
        colaBloqueados.removeAll(desbloqueados);   
        for (Proceso p : colaBloqueados){  
          p.setTiempoRestanteES(p.getTiempoRestanteES() - 1); //Descontamos a cada uno una unidad de tiempo    
        }  
    }

    private void verificarCondicionDeFin() {
        long procesosTerminados = procesos.stream().filter(p -> p.getEstado().equals("TERMINADO")).count(); //Si todos los procesos tienen como estado "Terminado", la simulacion termina
        if (procesosTerminados == procesos.size() && cpu.estaOciosa() && cpu.getTiempoRestanteTCP() == 0 && cpu.getTiempoRestanteTIP() == 0) { //No hay procesos en CPU ni en overhead
            this.simulacionTerminada = true;
        }
    }

    private void registrarEvento(Integer pid, String tipo, String mensaje) {
        this.log.add(new Evento(tiempoActual, pid, tipo, mensaje)); //Lo uso de log, como es una lista lo puedo guardar y exportar
    }

    private void calcularMetricasFinales() {
        int sumaTR = 0;
        for (Proceso p : procesos) {
            int tr = p.getTiempoFinEjecucion() - p.getTiempoArribo();
            sumaTR += tr;
        }
        int trt = procesos.stream().mapToInt(Proceso::getTiempoFinEjecucion).max().orElse(0)
                - procesos.stream().mapToInt(Proceso::getTiempoArribo).min().orElse(0); //Tiempo de retorno de la tanda = tiempo de finalización del último - tiempo de arribo del primero
        double tmrt = (procesos.isEmpty()) ? 0 : (double) sumaTR / procesos.size();
        metricas.setTiempoRetornoTanda(trt);
        metricas.setTiempoMedioRetornoTanda(tmrt);
    }
    
    public List<Evento> getLog() { return log; }
    public Metricas getMetricas() { return metricas; }
    public List<Proceso> getProcesos() { return procesos; } //Los eventos, metricas y procesos de esta simulacion en especifico
}