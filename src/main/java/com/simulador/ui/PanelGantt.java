package com.simulador.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import com.simulador.models.Evento;
import com.simulador.models.Proceso;

public class PanelGantt extends JPanel {

    //Atributos de Datos
    private final List<Proceso> procesos; //La lista de procesos a dibujar
    private final List<Evento> eventos;  //El log completo de la simulación
    
    //Atributos de Animación y Estado
    private int tiempoDeDibujo = -1; //Controla hasta qué tiempo se dibuja la animación. -1 dibuja todo.
    private final String[][] estadoEnTiempo; //Matriz que almacena el estado de cada proceso en cada instante de tiempo
    private final int tiempoTotal; //Duración total de la simulación
    
    //Constantes de Dibujo de celdas de estado
    private static final int ROW_HEIGHT = 30;
    private static final int CELL_WIDTH = 15;
    private static final int MARGIN_LEFT = 80;
    private static final int MARGIN_TOP = 40;

    // --- Colores para cada Estado ---
    private final Map<String, Color> stateColors;

    
    public PanelGantt(List<Proceso> procesos, List<Evento> eventos) {
        this.procesos = procesos;
        this.eventos = eventos;

        //Se inicializa el mapa de colores para la leyenda y las barras del diagrama
        stateColors = new HashMap<>();
        stateColors.put("EJECUCION", Color.GREEN);
        stateColors.put("BLOQUEADO", Color.RED);
        stateColors.put("LISTO", Color.YELLOW);
        stateColors.put("NUEVO", Color.CYAN);
        stateColors.put("TIP", Color.LIGHT_GRAY);
        stateColors.put("TCP", Color.GRAY);
        stateColors.put("TFP", Color.DARK_GRAY);
        stateColors.put("TERMINADO", Color.WHITE);
        stateColors.put("NO_LLEGADO", Color.WHITE);

        //Se calcula la matriz de estados completa en el constructor para optimizar el redibujado.
        if (eventos != null && !eventos.isEmpty()) {
            this.tiempoTotal = eventos.get(eventos.size() - 1).getTiempo();
            this.estadoEnTiempo = new String[procesos.size() + 1][tiempoTotal + 2];
            construirMatrizDeEstados();
        } else {
            this.tiempoTotal = 0;
            this.estadoEnTiempo = new String[0][0];
        }
    }
    
    //Verifica si existe un evento específico para un proceso en un tiempo dado.
    private boolean hasEvent(int pid, int t, String eventType) {
        for (Evento e : this.eventos) {
            if (e.getPid() != null && e.getPid() == pid && e.getTiempo() == t && e.getTipoEvento().equals(eventType)) {
                return true;
            }
        }
        return false;
    }

    //Se construye una matriz que indica el estado de cada proceso en cada instante de tiempo.
    private void construirMatrizDeEstados() {
        for (int t = 0; t <= tiempoTotal + 1; t++) {
            //Propagación de estado: Cada proceso mantiene el estado del ciclo anterior por defecto.
            for (Proceso p : procesos) {
                int pid = p.getPid();
                if (estadoEnTiempo[pid][t] == null) { 
                    if (t > 0) {
                        estadoEnTiempo[pid][t] = estadoEnTiempo[pid][t - 1];
                    } else {
                        estadoEnTiempo[pid][t] = "NO_LLEGADO";
                    }
                }
            }
            //Aplicación de eventos: Se revisan los eventos del instante 't' y se actualiza la matriz.
            for (Evento e : eventos) {
                if (e.getTiempo() == t && e.getPid() != null) {
                    int pid = e.getPid();
                    String tipo = e.getTipoEvento();
                    switch (tipo) {
                        case "ARRIBO_PROCESO":
                            estadoEnTiempo[pid][t] = "NUEVO";
                            break;
                        case "INICIO_TIP":
                            //Solo se dibuja el TIP si su duración no es cero.
                            if (!hasEvent(pid, t, "FIN_TIP")) {
                                estadoEnTiempo[pid][t] = "TIP";
                            }
                            break;
                        case "INICIO_TCP":
                            //Solo se dibuja el TCP si no es instantáneo
                             if (!hasEvent(pid, t, "DESPACHO_PROCESO")) {
                                estadoEnTiempo[pid][t] = "TCP";
                            }
                            break;
                        case "EJECUCION":
                        case "DESPACHO_PROCESO":
                            //DESPACHO_PROCESO fuerza el estado a EJECUCION para corregir el desfase del log.
                            estadoEnTiempo[pid][t] = "EJECUCION";
                            break;
                        case "FIN_TIP":
                        case "BLOQUEADO_A_LISTO":
                        case "INTERRUPCION":
                        case "INCUMBENTE_EXPROPIADO":
                            estadoEnTiempo[pid][t] = "LISTO";
                            break;
                        case "FIN TFP":
                            //El tick 't' fue el último del TFP. El siguiente será TERMINADO.
                            estadoEnTiempo[pid][t] = "TFP";
                            if (t + 1 <= tiempoTotal + 1) estadoEnTiempo[pid][t + 1] = "TERMINADO";
                            break;
                        
                        //Transiciones de estado
                        case "EJECUCION_A_BLOQUEADO":
                            estadoEnTiempo[pid][t] = "EJECUCION"; // El tick 't' fue de ejecución.
                            if (t + 1 <= tiempoTotal + 1) estadoEnTiempo[pid][t + 1] = "BLOQUEADO"; //El siguiente es de bloqueo.
                            break;
                        case "PROCESO_TERMINADO":
                            estadoEnTiempo[pid][t] = "EJECUCION"; //El último tick fue de ejecución.
                            if (!hasEvent(pid, t, "FIN TFP")) { //Si el TFP no es instantáneo fue de TFP o Terminado
                                if (t + 1 <= tiempoTotal + 1) estadoEnTiempo[pid][t + 1] = "TFP"; //El siguiente es TFP.
                            } else { // Si el TFP es instantáneo...
                                if (t + 1 <= tiempoTotal + 1) estadoEnTiempo[pid][t + 1] = "TERMINADO"; //El siguiente es TERMINADO.
                            }
                            break;
                        case "FIN_QUANTUM":
                            estadoEnTiempo[pid][t] = "EJECUCION"; //El tick 't' fue de ejecución.
                            if (t + 1 <= tiempoTotal + 1) estadoEnTiempo[pid][t + 1] = "LISTO"; //El siguiente está listo.
                            break;
                    }
                }
            }
        }
    }

      /*
      Actualiza el tiempo hasta el cual se debe dibujar el diagrama.
      Un valor de -1 indica que se debe dibujar todo.
     */
    public void setTiempoDeDibujo(int tiempo) {
        this.tiempoDeDibujo = tiempo;
        this.repaint(); //Solicita que el panel se redibuje con el nuevo límite.
    }

    //metodo principal
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        setBackground(Color.WHITE);

        if (procesos == null || procesos.isEmpty()) {
            g.drawString("No hay datos para mostrar.", 20, 20);
            return;
        }

        //Dibuja las etiquetas de los procesos (filas) y la escala de tiempo (columnas).
        g.setFont(new Font("Arial", Font.BOLD, 12));
        for (int i = 0; i < procesos.size(); i++) {
            g.drawString("P" + (i + 1) + " (" + procesos.get(i).getNombre() + ")", 10, MARGIN_TOP + i * ROW_HEIGHT + ROW_HEIGHT / 2 + 5);
        }
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        for (int t = 0; t <= tiempoTotal; t++) {
            if (t % 5 == 0) {
                g.drawString(String.valueOf(t), MARGIN_LEFT + t * CELL_WIDTH, MARGIN_TOP - 10);
                g.drawLine(MARGIN_LEFT + t * CELL_WIDTH, MARGIN_TOP - 5, MARGIN_LEFT + t * CELL_WIDTH, MARGIN_TOP);
            }
        }

        //Determina hasta qué ciclo de tiempo dibujar, controlado por la animación.
        int limiteDeDibujo = (tiempoDeDibujo == -1) ? tiempoTotal + 1 : tiempoDeDibujo;

        //Itera hasta el límite de dibujo, coloreando cada celda según la matriz de estados.
        for (int i = 0; i < procesos.size(); i++) {
            int pid = procesos.get(i).getPid();
            for (int t = 0; t < limiteDeDibujo; t++) {
                String estado = estadoEnTiempo[pid][t];
                Color color = stateColors.getOrDefault(estado, Color.LIGHT_GRAY);
                g.setColor(color);
                g.fillRect(MARGIN_LEFT + t * CELL_WIDTH, MARGIN_TOP + i * ROW_HEIGHT, CELL_WIDTH, ROW_HEIGHT);
                if (estado != null && !"TERMINADO".equals(estado) && !"NO_LLEGADO".equals(estado)) {
                    g.setColor(Color.BLACK);
                    g.drawRect(MARGIN_LEFT + t * CELL_WIDTH, MARGIN_TOP + i * ROW_HEIGHT, CELL_WIDTH, ROW_HEIGHT);
                }
            }
        }
        
        //Dibuja la leyenda de colores en la parte inferior del panel.
        int legendY = MARGIN_TOP + procesos.size() * ROW_HEIGHT + 40;
        int legendX = MARGIN_LEFT;
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString("Leyenda:", legendX, legendY);
        legendY += 5;
        
        String[] leyendaOrdenada = {"EJECUCION", "BLOQUEADO", "LISTO", "NUEVO", "TIP", "TCP", "TFP"};
        for (String estado : leyendaOrdenada) {
            Color color = stateColors.get(estado);
            if (color == null) continue;
            legendY += 20;
            g.setColor(color);
            g.fillRect(legendX, legendY, 15, 15);
            g.setColor(Color.BLACK);
            g.drawRect(legendX, legendY, 15, 15);
            g.drawString(estado, legendX + 25, legendY + 12);
        }
    }

    
    //Se le dice al layout manager el tamaño preferido del panel para que el JScrollPane funcione correctamente.
    @Override
    public Dimension getPreferredSize() {
        if (eventos == null || eventos.isEmpty()) return new Dimension(800, 600);
        int width = MARGIN_LEFT + (tiempoTotal + 2) * CELL_WIDTH;
        int height = MARGIN_TOP + procesos.size() * ROW_HEIGHT + 200;
        return new Dimension(width, height);
    }
}