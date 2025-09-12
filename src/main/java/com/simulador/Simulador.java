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
        procesarLlegadas();
        actualizarColaBloqueados();
        if (planificador.esExpropiativo()) {
            verificarInterrupcion();
        }
        gestionarCPU();

        //El que sale del bloqueo recién interrumpe en el siguiente ciclo (porque sale en el mismo ciclo que consume si no)

        for (Proceso p : colaPrincipal.getCola()) {
            p.setTiempoEnEstadoListo(p.getTiempoEnEstadoListo() + 1);
        }

        verificarCondicionDeFin();

        if (!simulacionTerminada) {
            tiempoActual++;
        }
    }
    
    //LÓGICA DE FINALIZACIÓN DE OVERHEADS
    private void onFinTIP(Proceso p) {
        registrarEvento(p.getPid(), "FIN_TIP", "Proceso " + p.getNombre() + " completó TIP.");
        p.setEstado("LISTO");
        Proceso ganador = decidirProximoIncumbente(p);
        iniciarDespachoOAdmision(ganador);
    }

    private boolean onFinTCP(Proceso p) {
        Proceso ganador = decidirProximoIncumbente(p);
        if (ganador == p) {
            cpu.asignarProceso(ganador, params.getQuantum());
            registrarEvento(ganador.getPid(), "DESPACHO_PROCESO", "Proceso " + ganador.getNombre() + " pasa a ejecución.");
            return false;
        } else {
            iniciarDespachoOAdmision(ganador);
            return true;
        }
    }

    private void onFinTFP() {
        if (this.ultimoProcesoTerminado != null) {
            this.ultimoProcesoTerminado.setTiempoFinEjecucion(tiempoActual);
            registrarEvento(this.ultimoProcesoTerminado.getPid(), "FIN TFP", "Proceso " + this.ultimoProcesoTerminado.getNombre() + " ha finalizado TFP");
            this.ultimoProcesoTerminado = null;
        }
    }

    private void gestionarCPU() {
        //Manejar TIP en curso
        if (cpu.getTiempoRestanteTIP() > 0) {
            cpu.setTiempoRestanteTIP(cpu.getTiempoRestanteTIP() - 1);
            metricas.incrementarTiempoCPU_OS();
            if (cpu.getTiempoRestanteTIP() == 0) {
                onFinTIP(cpu.getProcesoADespachar());
            }
            return;
        }

        //Manejar TCP y TFP en curso
        if (cpu.getTiempoRestanteTCP() > 0) {
            cpu.setTiempoRestanteTCP(cpu.getTiempoRestanteTCP() - 1);
            metricas.incrementarTiempoCPU_OS();
            if (cpu.getTiempoRestanteTCP() == 0) {
                Proceso p = cpu.getProcesoADespachar();
                if (p != null) { //Fin de un TCP
                    if (onFinTCP(p)) {
                        return; //Hay cambio de incumbente, se inicia otro overhead y se sale.
                    }
                } else { //Fin de un TFP
                    onFinTFP();
                    return;
                }
            } else {
                return; //El TCP/TFP sigue en curso.
            }
        }

        //Si la CPU está Ociosa, buscar nuevo trabajo
        if (cpu.estaOciosa()) {
            Proceso proximo = colaPrincipal.quitar();
            if (proximo != null) {
                iniciarDespachoOAdmision(proximo);
            } else {
                metricas.incrementarTiempoCPUDesocupada();
                return; //No hay procesos listos ni en ejecución, fin del trabajo de CPU en este ciclo.
            }
        }

        //Si después de todo lo anterior la CPU tiene un proceso, se ejecuta.
        //Esto permite la ejecución en el mismo ciclo para overheads de duración cero.
        if (!cpu.estaOciosa()) {
            Proceso actual = cpu.getProcesoActual();
            actual.setTiempoRestanteRafagaCPU(actual.getTiempoRestanteRafagaCPU() - 1);
            registrarEvento(actual.getPid(), "EJECUCION", "Proceso " + actual.getNombre() + " resta ejecutar " + actual.getTiempoRestanteRafagaCPU());
            cpu.setQuantumRestante(cpu.getQuantumRestante() - 1);

            if (actual.getTiempoRestanteRafagaCPU() <= 0) { //Termino su rafaga
                actual.setRafagasRestantes(actual.getRafagasRestantes() - 1);
                registrarEvento(actual.getPid(), "FIN_RAFAGA_CPU", "Proceso " + actual.getNombre() + " terminó ráfaga de CPU.");
                
                cpu.liberar();
                
                if (actual.getRafagasRestantes() <= 0) {
                    actual.setEstado("TERMINADO");
                    this.ultimoProcesoTerminado = actual;
                    registrarEvento(actual.getPid(), "PROCESO_TERMINADO", "Proceso " + actual.getNombre() + " ha finalizado.");
                    cpu.setProcesoADespachar(null); //Marcar que el próximo es TFP
                    
                    if (params.getTfp() == 0) {
                        onFinTFP();
                    } else {
                        cpu.setTiempoRestanteTCP(params.getTfp());
                    }
                } else {
                    actual.setEstado("BLOQUEADO");
                    actual.setTiempoRestanteES(actual.getDuracionRafagaES());
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
        }
    }
    //El método que verifica si el proceso actual debe ser interrumpido o no
    private void verificarInterrupcion() {
        if (planificador instanceof RoundRobin) return;
        
        if (!cpu.estaOciosa() && cpu.getTiempoRestanteTIP() == 0 && cpu.getTiempoRestanteTCP() == 0) {
            Proceso actual = cpu.getProcesoActual();
            Proceso proximoEnCola = colaPrincipal.verSiguiente();
            boolean debeInterrumpir = false;
            if (proximoEnCola != null) {
                if (planificador instanceof SRTN && proximoEnCola.getTiempoRestanteRafagaCPU() < actual.getTiempoRestanteRafagaCPU()) {
                    debeInterrumpir = true;
                } else if (planificador instanceof PrioridadExterna && proximoEnCola.getPrioridadExterna() > actual.getPrioridadExterna()) {
                    debeInterrumpir = true;
                }
            }
            if (debeInterrumpir) {
                if (actual!=null && proximoEnCola != null) { //Validación solo para que no me salte el warning 
                    registrarEvento(actual.getPid(), "INTERRUPCION", "Proceso " + actual.getNombre() + " interrumpido por " + proximoEnCola.getNombre());
                    actual.setEstado("LISTO");
                    actual.setFueInterrumpido(true);
                }                   
                colaPrincipal.agregar(actual);
                cpu.liberar();
            }
        }
    }

    //El método que decide si el proceso actual debe ser expropiado o no
    private Proceso decidirProximoIncumbente(Proceso p) {
        if (!planificador.esExpropiativo()) return p;
        
        Proceso proximoEnCola = colaPrincipal.verSiguiente();
        boolean debeSerExpropiado = false;
        if (proximoEnCola != null) {
            if (planificador instanceof PrioridadExterna && proximoEnCola.getPrioridadExterna() > p.getPrioridadExterna()) {
                debeSerExpropiado = true; //Si la prioridad del de la cola es mayor, expropia. SOLO SI ES MAYOR, SI ES IGUAL SIGUE EL MISMO
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
    //El metodo que inicia el despacho o admisión de un proceso a la CPU dependiendo de su estado
    private void iniciarDespachoOAdmision(Proceso p) {
        if (!p.GetfueInterrumpido()) { //Esto es para mantener la duración de rafaga que llevaba
            p.setTiempoRestanteRafagaCPU(p.getDuracionRafagaCPU()); //No fue interrumpido, arranca nueva ráfaga
        }
        p.setFueInterrumpido(false);

        registrarEvento(p.getPid(), "PROCESO_SELECCIONADO", "Proceso " + p.getNombre() + " seleccionado por el planificador.");
        
        cpu.setProcesoADespachar(p);
        
        if (p.getEstado().equals("NUEVO")) { //Si es nuevo le hacemos TIP, si no, TCP
            registrarEvento(p.getPid(), "INICIO_TIP", "Proceso " + p.getNombre() + " es seleccionado para admisión (TIP).");
            if (params.getTip() == 0) {
                onFinTIP(p);
            } else {
                cpu.setTiempoRestanteTIP(params.getTip());
            }
        } else {
            registrarEvento(p.getPid(), "INICIO_TCP", "Iniciando cambio de contexto para " + p.getNombre());
            if (params.getTcp() == 0) {
                onFinTCP(p);
            } else {
                cpu.setTiempoRestanteTCP(params.getTcp());
            }
        }
    }
    //El método que procesa las llegadas de nuevos procesos en el tiempo actual
    private void procesarLlegadas() {
        for (Proceso p : procesos) {
            if (p.getEstado().equals("NO_LLEGADO") && p.getTiempoArribo() <= tiempoActual) {
                p.setEstado("NUEVO");
                colaPrincipal.agregar(p); //Verificamos todos los procesos disponibles para ver cuáles deberían arribar en el T actual
                registrarEvento(p.getPid(), "ARRIBO_PROCESO", "El proceso " + p.getNombre() + " ha arribado y se encola.");
            }
        }
    }
    //El método que actualiza la cola de bloqueados, moviendo los que terminaron E/S a la cola de listos
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
    //El método que verifica si se cumplen las condiciones de fin de simulación
    private void verificarCondicionDeFin() {
        long procesosTerminados = procesos.stream().filter(p -> p.getEstado().equals("TERMINADO")).count();
        if (procesosTerminados == procesos.size() && cpu.estaOciosa() && cpu.getTiempoRestanteTCP() == 0 && cpu.getTiempoRestanteTIP() == 0) {
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
    public List<Proceso> getProcesos() { return procesos; }
}