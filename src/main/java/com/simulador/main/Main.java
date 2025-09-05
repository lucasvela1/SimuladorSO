package com.simulador.main;

import javax.swing.SwingUtilities;

import com.simulador.ui.VentanaPrincipal;

public class Main {
    public static void main(String[] args) {
        // Inicia la UI en el hilo de despacho de eventos de Swing para seguridad
        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal ventana = new VentanaPrincipal();
            ventana.setVisible(true);
        });
    }
}