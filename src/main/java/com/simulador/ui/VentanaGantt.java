package com.simulador.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

import com.simulador.models.Evento;
import com.simulador.models.Proceso;

public class VentanaGantt extends JDialog {

    private final PanelGantt panelGantt;
    private final Timer timerAnimacion;
    private int tiempoAnimacionActual = 0;
    private final int tiempoTotal;

    private final JButton pausarButton;
    private final JButton finalizarButton;
    private final JButton repetirButton;
    private boolean isPaused = false;

    public VentanaGantt(JFrame parent, List<Proceso> procesos, List<Evento> eventos) {
        super(parent, "Diagrama de Gantt", true);
        
        setSize(1200, 650);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        //Crear el panel de dibujo y el scroll pane
        panelGantt = new PanelGantt(procesos, eventos);
        JScrollPane scrollPane = new JScrollPane(panelGantt);
        add(scrollPane, BorderLayout.CENTER);

        //Crear los botones de control
        pausarButton = new JButton("Pausar");
        finalizarButton = new JButton("Finalizar Animación");
        repetirButton = new JButton("Repetir Animación");
        repetirButton.setEnabled(false);

        JPanel panelBotones = new JPanel();
        panelBotones.add(pausarButton);
        panelBotones.add(finalizarButton);
        panelBotones.add(repetirButton);
        add(panelBotones, BorderLayout.SOUTH);

        //Determinar el tiempo total ANTES de crear el timer
        if (eventos != null && !eventos.isEmpty()) {
            this.tiempoTotal = eventos.get(eventos.size() - 1).getTiempo();
        } else {
            this.tiempoTotal = 0;
        }

        //Crear el ActionListener y el Timer
        ActionListener animador = e -> {
            if (tiempoAnimacionActual > tiempoTotal) {
                ((Timer) e.getSource()).stop();
                finalizarButton.setEnabled(false);
                pausarButton.setEnabled(false);
                repetirButton.setEnabled(true);
            } else {
                panelGantt.setTiempoDeDibujo(tiempoAnimacionActual);
                tiempoAnimacionActual++;
            }
        };

        timerAnimacion = new Timer(600, animador); //Aca se cambia para hacer que la animación sea más rápida o más lenta

        //Lógica de los botones
        pausarButton.addActionListener(e -> {
            if (isPaused) {
                timerAnimacion.start();
                pausarButton.setText("Pausar");
                isPaused = false;
            } else {
                timerAnimacion.stop();
                pausarButton.setText("Reanudar");
                isPaused = true;
            }
        });

        finalizarButton.addActionListener(e -> {
            timerAnimacion.stop();
            panelGantt.setTiempoDeDibujo(-1);
            finalizarButton.setEnabled(false);
            pausarButton.setEnabled(false);
            pausarButton.setText("Pausar");
            isPaused = false;
            repetirButton.setEnabled(true);
        });

        repetirButton.addActionListener(e -> {
            tiempoAnimacionActual = 0;
            panelGantt.setTiempoDeDibujo(0);
            timerAnimacion.start();
            
            repetirButton.setEnabled(false);
            finalizarButton.setEnabled(true);
            pausarButton.setEnabled(true);
            pausarButton.setText("Pausar");
            isPaused = false;
        });
        
        // 6. Iniciar la animación SOLO si hay algo que animar
        if (this.tiempoTotal > 0) {
            timerAnimacion.start();
        } else {
            panelGantt.setTiempoDeDibujo(-1);
            pausarButton.setEnabled(false);
            finalizarButton.setEnabled(false);
            repetirButton.setEnabled(false);
        }
    }
}