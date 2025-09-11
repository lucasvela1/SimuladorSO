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

    private final List<Proceso> procesos;
    private final List<Evento> eventos;
    //Para los rectangulos de colores, nombres, etc
    private static final int ROW_HEIGHT = 30;
    private static final int CELL_WIDTH = 15;
    private static final int MARGIN_LEFT = 80;
    private static final int MARGIN_TOP = 40;

    private final Map<String, Color> stateColors; //A un estado le corresponde un color

    public PanelGantt(List<Proceso> procesos, List<Evento> eventos) {
        this.procesos = procesos;
        this.eventos = eventos;

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
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        setBackground(Color.WHITE);

        if (procesos == null || procesos.isEmpty() || eventos == null || eventos.isEmpty()) {
            g.drawString("No hay datos para mostrar.", 20, 20);
            return;
        }

        int tiempoTotal = eventos.get(eventos.size() - 1).getTiempo(); 

        //1 Dibujar etiquetas y escala de tiempo
        g.setFont(new Font("Arial", Font.BOLD, 12));
        for (int i = 0; i < procesos.size(); i++) {
            Proceso p = procesos.get(i);
            g.drawString("P" + p.getPid() + " (" + p.getNombre() + ")", 10, MARGIN_TOP + i * ROW_HEIGHT + ROW_HEIGHT / 2 + 5);
        }
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        for (int t = 0; t <= tiempoTotal; t++) {
            if (t % 5 == 0) {
                g.drawString(String.valueOf(t), MARGIN_LEFT + t * CELL_WIDTH, MARGIN_TOP - 10);
                g.drawLine(MARGIN_LEFT + t * CELL_WIDTH, MARGIN_TOP - 5, MARGIN_LEFT + t * CELL_WIDTH, MARGIN_TOP);
            }
        }

        //2 Construir la matriz de estados
        String[][] estadoEnTiempo = new String[procesos.size() + 1][tiempoTotal + 2];

        for (int t = 0; t <= tiempoTotal + 1; t++) {
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
            for (Evento e : eventos) {
                if (e.getTiempo() == t && e.getPid() != null) {
                    int pid = e.getPid();
                    String tipo = e.getTipoEvento();

                    switch (tipo) { //Algunos estados "transitorios" deberían mostrar cierto estado, como de ejecutando a bloquedo, debe mostrar ejecutando esa casilla
                        case "ARRIBO_PROCESO":
                            estadoEnTiempo[pid][t] = "NUEVO";
                            break;
                        case "INICIO_TIP":
                            estadoEnTiempo[pid][t] = "TIP";
                            break;
                        case "INICIO_TCP":
                            estadoEnTiempo[pid][t] = "TCP";
                            break;
                        case "EJECUCION":
                        case "DESPACHO_PROCESO":
                            estadoEnTiempo[pid][t] = "EJECUCION";
                            break;
                        case "FIN_TIP":
                        case "BLOQUEADO_A_LISTO":
                        case "INTERRUPCION":
                        case "INCUMBENTE_EXPROPIADO":
                            estadoEnTiempo[pid][t] = "LISTO";
                            break;
                        case "FIN TFP":
                            estadoEnTiempo[pid][t] = "TFP";
                            if (t + 1 <= tiempoTotal + 1) estadoEnTiempo[pid][t + 1] = "TERMINADO";
                            break;
                        
                        case "EJECUCION_A_BLOQUEADO":
                            estadoEnTiempo[pid][t] = "EJECUCION";
                            if (t + 1 <= tiempoTotal + 1) estadoEnTiempo[pid][t + 1] = "BLOQUEADO";
                            break;
                        case "PROCESO_TERMINADO":
                            estadoEnTiempo[pid][t] = "EJECUCION";
                            if (t + 1 <= tiempoTotal + 1) estadoEnTiempo[pid][t + 1] = "TFP";
                            break;
                        case "FIN_QUANTUM": 
                            estadoEnTiempo[pid][t] = "EJECUCION";
                            if (t + 1 <= tiempoTotal + 1) estadoEnTiempo[pid][t + 1] = "LISTO";
                            break;
                    }
                }
            }
        }

        //3 Dibujar los rectángulos
        for (int i = 0; i < procesos.size(); i++) {
            int pid = procesos.get(i).getPid();
            for (int t = 0; t <= tiempoTotal; t++) {
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
        
        //4 Dibujar leyenda
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

    @Override
    public Dimension getPreferredSize() {
        if (eventos == null || eventos.isEmpty()) {
            return new Dimension(800, 600);
        }
        int tiempoTotal = eventos.get(eventos.size() - 1).getTiempo();
        int width = MARGIN_LEFT + (tiempoTotal + 2) * CELL_WIDTH;
        int height = MARGIN_TOP + procesos.size() * ROW_HEIGHT + 200;
        return new Dimension(width, height);
    }
}