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

/*
    El motor principal que ejecuta la simulación paso a paso.
    Gestiona el tiempo, los estados de los procesos y la interacción con el planificador.
 */

public class Simulador {

    //Atributos del Estado de la Simulación
    private int tiempoActual;
    private List<Proceso> procesos;
    private SystemParams params;
    
    private Planificador planificador;
    private EstadoCPU cpu;
    private ColaListos colaPrincipal;
    private List<Proceso> colaBloqueados;

    //Resultados de la Simulación
    private List<Evento> log;
    private Metricas metricas;
    private boolean simulacionTerminada;

    /*
      Constructor para inicializar el simulador.
      Selecciona la implementación de cola correcta según la estrategia de planificación.
     */
    public Simulador(List<Proceso> procesos, Planificador planificador, SystemParams params) {
        this.tiempoActual = 0;
        this.procesos = procesos;
        this.planificador = planificador;
        this.params = params;

        //Decide qué tipo de cola crear basado en la CLASE del planificador.
        if (planificador instanceof SRTN) {
            this.colaPrincipal = new ColaListosSRT();
        } else if (planificador instanceof PrioridadExterna) {
            this.colaPrincipal = new ColaListosPrioridad();
        } else if (planificador instanceof SPN){
            this.colaPrincipal = new ColaListosSPN();
        } else {
            //Para FCFS y RoundRobin se usa la cola FIFO por defecto.
            this.colaPrincipal = new ColaListos();
        }
        
        //Inicializar el resto
        this.cpu = new EstadoCPU();
        this.colaBloqueados = new ArrayList<>();
        this.log = new ArrayList<>();
        this.metricas = new Metricas();
        this.simulacionTerminada = false;
    }

    
    //Ejecuta la simulación completa hasta que todos los procesos finalicen.
    
    public void iniciar() {
        registrarEvento(null, "INICIO_SIMULACION", "La simulación ha comenzado.");
        while (!simulacionTerminada) {
            ejecutarCiclo();
        }
        int tiempoFinal = tiempoActual > 0 ? tiempoActual - 1 : 0;
        registrarEvento(null, "FIN_SIMULACION", "La simulación ha terminado en t=" + tiempoFinal);
        calcularMetricasFinales(); 
    }

    
    //Ejecuta un ciclo (tick de reloj) de la simulación.
    private void ejecutarCiclo() {
        //1. Actualizar bloqueados. Si alguien se desbloquea, puede causar una interrupción.
        actualizarColaBloqueados();
        if (planificador.esExpropiativo()) {
            verificarInterrupcion();
        }

        //2. Procesar llegadas. Si alguien llega, puede causar una interrupción.
        procesarLlegadas();
        if (planificador.esExpropiativo()) {
            verificarInterrupcion();
        }

        //3. Gestionar la CPU 
        gestionarCPU();

        //4. Aumentar contador de espera en la fila principal
        for (Proceso p : colaPrincipal.getCola()) {
            p.setTiempoEnEstadoListo(p.getTiempoEnEstadoListo() + 1);
        }

        //5. Verificar condición de fin
        verificarCondicionDeFin();
        
        //6. Avanzar el tiempo
        if (!simulacionTerminada) {
            tiempoActual++;
        }
    }

    /*
        Maneja la lógica de interrupción (preemption) para planificadores expropiativos
        basados en comparación de procesos (SRTN, Prioridad).
     */
    private void verificarInterrupcion() {
        //Si el planificador es Round Robin, salimos. Su expropiación es por quantum, no por comparación.
        if (planificador instanceof RoundRobin) {
            return;
        }

        //Solo se interrumpe un proceso de usuario. No se interrumpen TIP, TCP o TFP
        if (!cpu.estaOciosa() && cpu.getTiempoRestanteTIP() == 0 && cpu.getTiempoRestanteTCP() == 0) {
            Proceso actual = cpu.getProcesoActual();
            Proceso proximoEnCola = null;
            boolean debeInterrumpir = false;

            //Comprobamos qué tipo de planificador estamos usando para aplicar el criterio correcto
            if (planificador instanceof SRTN) {
                proximoEnCola = ((ColaListosSRT) colaPrincipal).verSiguiente();
                //Criterio para SRTN: menor tiempo restante
                if (proximoEnCola != null && proximoEnCola.getTiempoRestanteRafagaCPU() < actual.getTiempoRestanteRafagaCPU()) {
                    debeInterrumpir = true;
                }
            } else if (planificador instanceof PrioridadExterna) {
                proximoEnCola = ((ColaListosPrioridad) colaPrincipal).verSiguiente();
                //Criterio para Prioridad: mayor prioridad (menor número)
                if (proximoEnCola != null && proximoEnCola.getPrioridadExterna() < actual.getPrioridadExterna()) {
                    debeInterrumpir = true;
                }
            }

            //Si se cumple alguna de las condiciones de interrupción
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
                p.setEstado("LISTO");  //Si cumplió su E/S, pasa a listo
                colaPrincipal.agregar(p);
                desbloqueados.add(p);
                registrarEvento(p.getPid(), "BLOQUEADO_A_LISTO", "Proceso " + p.getNombre() + " terminó E/S y se re-encola.");
            }
        }
        colaBloqueados.removeAll(desbloqueados); //Sacamos de bloqueados a los que se desbloquearon
    }

    private void gestionarCPU() {
        if (cpu.getTiempoRestanteTIP() > 0) {
           cpu.setTiempoRestanteTIP(cpu.getTiempoRestanteTIP() - 1);
           metricas.incrementarTiempoCPU_OS();
           if (cpu.getTiempoRestanteTIP() == 0) {
              Proceso p = cpu.getProcesoADespachar();
              if (p != null) {
                  registrarEvento(p.getPid(), "FIN_TIP", "Proceso " + p.getNombre() + " completó TIP. Verificando si debe continuar.");
                  p.setEstado("LISTO");

                  // Lógica de incumbencia: 'p' solo es expropiado si hay alguien con prioridad ESTRICTAMENTE MAYOR.
                  Proceso proximoEnCola = colaPrincipal.verSiguiente();
                  boolean debeSerExpropiado = false;
                  if (proximoEnCola != null) {
                      if (planificador instanceof PrioridadExterna) {
                          if (proximoEnCola.getPrioridadExterna() < p.getPrioridadExterna()) {
                              debeSerExpropiado = true;
                            }
                        } else if (planificador instanceof SRTN) {
                            if (proximoEnCola.getTiempoRestanteRafagaCPU() < p.getTiempoRestanteRafagaCPU()) {
                               debeSerExpropiado = true;
                            }
                        }
                    }

                    if (debeSerExpropiado) {
                      // 'p' es expropiado justo después de ser admitido.
                      registrarEvento(p.getPid(), "FIN_TIP_EXPROPIADO", "Proceso " + p.getNombre() + " expropiado por " + proximoEnCola.getNombre() + " antes de iniciar TCP.");
                      colaPrincipal.agregar(p);
                      Proceso proximo = colaPrincipal.quitar();
                      despacharProceso(proximo);
                    } else {
                      // 'p' gana el empate o tiene mayor prioridad, procede a su TCP.
                      despacharProceso(p);
                    }
                }
            }
            return; 
        }

        if (cpu.getTiempoRestanteTCP() > 0) {
          cpu.setTiempoRestanteTCP(cpu.getTiempoRestanteTCP() - 1);
          metricas.incrementarTiempoCPU_OS();
          if (cpu.getTiempoRestanteTCP() == 0) {
              Proceso p = cpu.getProcesoADespachar();
              if (p != null) {
                  if (!planificador.esExpropiativo()) {
                      cpu.asignarProceso(p, params.getQuantum());
                      registrarEvento(p.getPid(), "DESPACHO_PROCESO", "Proceso " + p.getNombre() + " pasa a ejecución.");
                      return;
                    } else {
                       Proceso proximoEnCola = colaPrincipal.verSiguiente();
                       boolean debeSerExpropiado = false;
                       if (proximoEnCola != null) {
                          if (planificador instanceof PrioridadExterna) {
                              if (proximoEnCola.getPrioridadExterna() < p.getPrioridadExterna()) {
                                  debeSerExpropiado = true;
                                }
                            } else if (planificador instanceof SRTN) {
                               if (proximoEnCola.getTiempoRestanteRafagaCPU() < p.getTiempoRestanteRafagaCPU()) {
                                  debeSerExpropiado = true;
                                }
                            }
                        }

                        if (debeSerExpropiado) {
                          registrarEvento(p.getPid(), "FIN_TCP_EXPROPIADO", "Fin de TCP para " + p.getNombre() + ", pero es expropiado por " + proximoEnCola.getNombre());
                          p.setEstado("LISTO");
                          colaPrincipal.agregar(p);
                          Proceso proximo = colaPrincipal.quitar();
                          despacharProceso(proximo);
                          return;
                        } else {
                          cpu.asignarProceso(p, params.getQuantum());
                          registrarEvento(p.getPid(), "DESPACHO_PROCESO", "Proceso " + p.getNombre() + " pasa a ejecución.");
                          return;
                        }
                    }
                }
            } else {
               return; 
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
            seleccionarYDespacharProximo();
        }
    }

    /**
     * Lógica centralizada para elegir el siguiente proceso de la cola de listos
     * y decidir si debe iniciar un TIP (si es nuevo) o un TCP (si ya está listo).
     */
    private void seleccionarYDespacharProximo() {
        Proceso proximo = colaPrincipal.quitar();
        if (proximo != null) {
            registrarEvento(proximo.getPid(), "PROCESO_SELECCIONADO", "Proceso " + proximo.getNombre() + " seleccionado por el planificador.");
            if (proximo.getEstado().equals("NUEVO")) {
                cpu.setProcesoADespachar(proximo);
                cpu.setTiempoRestanteTIP(params.getTip());
                registrarEvento(proximo.getPid(), "INICIO_TIP", "Proceso " + proximo.getNombre() + " es seleccionado para admisión (TIP).");
            } else { 
                despacharProceso(proximo);
            }
        } else {
            metricas.incrementarTiempoCPUDesocupada();
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
    public List<Evento> getLog() {
        return log;
    }

    public Metricas getMetricas() {
        return metricas;
    }
    public List<Proceso> getProcesos() {
        return procesos;
    }
}