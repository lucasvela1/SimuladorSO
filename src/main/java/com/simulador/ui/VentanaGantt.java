package com.simulador.ui;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;

import com.simulador.models.Evento;
import com.simulador.models.Proceso;

public class VentanaGantt extends JDialog {

    public VentanaGantt(JFrame parent, List<Proceso> procesos, List<Evento> eventos) {
        super(parent, "Diagrama de Gantt", true); // true para que sea modal
        
        setSize(1200, 650);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // El panel de dibujo se coloca dentro de un JScrollPane
        // para poder navegar si el diagrama es muy ancho.
        PanelGantt panelGantt = new PanelGantt(procesos, eventos);
        JScrollPane scrollPane = new JScrollPane(panelGantt);
        
        add(scrollPane, BorderLayout.CENTER);
    }
}