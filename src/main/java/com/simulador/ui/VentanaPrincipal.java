package com.simulador.ui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.simulador.models.Proceso;

public class VentanaPrincipal extends JFrame {

    // Componentes de la UI
    private JTextArea logArea;
    private JButton iniciarButton;
    private JComboBox<String> selectorAlgoritmo;
    private JTextField tipField, tfpField, tcpField, quantumField;

    // Datos de la simulación
    private List<Proceso> procesosCargados;

    public VentanaPrincipal() {
        setTitle("Simulador de Planificación de CPU");
        setSize(1024, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // ---- Panel de Controles (Norte) ----
        JPanel panelControles = new JPanel();
        String[] algoritmos = {"FCFS", "Prioridad Externa", "Round-Robin", "SPN", "SRTN"};
        selectorAlgoritmo = new JComboBox<>(algoritmos);
        tipField = new JTextField("5", 4);
        tfpField = new JTextField("3", 4);
        tcpField = new JTextField("1", 4);
        quantumField = new JTextField("10", 4);
        
        panelControles.add(new JLabel("Algoritmo:"));
        panelControles.add(selectorAlgoritmo);
        panelControles.add(new JLabel("TIP:"));
        panelControles.add(tipField);
        panelControles.add(new JLabel("TFP:"));
        panelControles.add(tfpField);
        panelControles.add(new JLabel("TCP:"));
        panelControles.add(tcpField);
        panelControles.add(new JLabel("Quantum:"));
        panelControles.add(quantumField);

        // ---- Área de Log (Centro) ----
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);

        // ---- Panel de Botones (Sur) ----
        JPanel panelBotones = new JPanel();
        JButton cargarJsonButton = new JButton("Cargar JSON de Procesos");
        iniciarButton = new JButton("Iniciar Simulación");
        iniciarButton.setEnabled(false); // Deshabilitado hasta cargar procesos
        panelBotones.add(cargarJsonButton);
        panelBotones.add(iniciarButton);

        // ---- Añadir paneles al Frame ----
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(panelControles, BorderLayout.NORTH);
        contentPane.add(scrollPane, BorderLayout.CENTER);
        contentPane.add(panelBotones, BorderLayout.SOUTH);

        // --- LÓGICA DE LOS BOTONES ---

        cargarJsonButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Selecciona el archivo JSON");
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                cargarProcesosDesdeJSON(selectedFile);
            }
        });

        iniciarButton.addActionListener(e -> {
            // Aquí irá la lógica para crear y ejecutar el Simulador.java
            logArea.append("\n--- INICIANDO SIMULACIÓN ---\n");
            // Por ahora, solo es un marcador de posición.
        });
    }

    private void cargarProcesosDesdeJSON(File archivo) {
        Gson gson = new Gson();
        java.lang.reflect.Type tipoListaProcesos = new TypeToken<List<Proceso>>() {}.getType();


        try (FileReader reader = new FileReader(archivo)) {
            this.procesosCargados = gson.fromJson(reader, tipoListaProcesos);
            
            if (this.procesosCargados == null || this.procesosCargados.isEmpty()) {
                logArea.setText("El archivo JSON está vacío o no tiene el formato esperado.");
                iniciarButton.setEnabled(false);
                return;
            }

            // Asignar un PID único a cada proceso y prepararlo para la simulación
            for (int i = 0; i < this.procesosCargados.size(); i++) {
                this.procesosCargados.get(i).inicializarParaSimulacion(i + 1); // Asigna PID 1, 2, 3...
            }

            logArea.setText("Archivo cargado: " + archivo.getName() + "\n");
            logArea.append(this.procesosCargados.size() + " procesos cargados exitosamente.\n");
            for (Proceso p : this.procesosCargados) {
                logArea.append("  - PID: " + p.getPid() + ", Nombre: " + p.getNombre() + "\n");
            }
            
            iniciarButton.setEnabled(true);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error al leer el archivo: " + ex.getMessage(), "Error de Archivo", JOptionPane.ERROR_MESSAGE);
            iniciarButton.setEnabled(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "El archivo no es un JSON válido: " + ex.getMessage(), "Error de Formato", JOptionPane.ERROR_MESSAGE);
            iniciarButton.setEnabled(false);
        }
    }
}