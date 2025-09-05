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
import javax.swing.SwingWorker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.simulador.Simulador;
import com.simulador.models.Evento;
import com.simulador.models.Proceso;
import com.simulador.models.SystemParams;
import com.simulador.scheduler.FCFS;
import com.simulador.scheduler.Planificador;
import com.simulador.scheduler.RoundRobin;

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
             try {
                 // 1. Deshabilitar botones para no iniciar otra simulación mientras una corre
                 iniciarButton.setEnabled(false);
                // Dejamos habilitado el botón de cargar por si se quiere cambiar la tanda
        
                logArea.setText(""); // Limpiar el log anterior

                 // 2. Recolectar los parámetros de la UI
                 int tip = Integer.parseInt(tipField.getText());
                 int tfp = Integer.parseInt(tfpField.getText());
                 int tcp = Integer.parseInt(tcpField.getText());
                 int quantum = Integer.parseInt(quantumField.getText());
                 SystemParams params = new SystemParams(tip, tfp, tcp, quantum);

                // 3. Crear el planificador seleccionado
                String algoSeleccionado = (String) selectorAlgoritmo.getSelectedItem();
                Planificador planificador = crearPlanificador(algoSeleccionado);
                if (planificador == null) { // Por si acaso
                   JOptionPane.showMessageDialog(this, "Algoritmo no implementado.", "Error", JOptionPane.ERROR_MESSAGE);
                   iniciarButton.setEnabled(true);
                   return;
                }
        
                // 4. Crear el Simulador con una COPIA de los procesos cargados
                // Esto permite volver a ejecutar la misma tanda sin tener que recargar el archivo
                List<Proceso> copiaProcesos = procesosCargados.stream().map(Proceso::new).toList();
                for (int i = 0; i < copiaProcesos.size(); i++) {
                   copiaProcesos.get(i).inicializarParaSimulacion(i + 1);
                }
                Simulador simulador = new Simulador(copiaProcesos, planificador, params);
        
                // 5. Crear y ejecutar el SwingWorker
                logArea.append("--- INICIANDO SIMULACIÓN [" + algoSeleccionado + "] ---\n");
                SimulacionWorker worker = new SimulacionWorker(simulador);
                worker.execute(); 

            } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Por favor, ingrese valores numéricos válidos en los parámetros.", "Error de Entrada", JOptionPane.ERROR_MESSAGE);
            iniciarButton.setEnabled(true);
            } catch (Exception ex) {
            ex.printStackTrace(); // Imprime el error en la consola para depuración
            JOptionPane.showMessageDialog(this, "Ocurrió un error inesperado: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            iniciarButton.setEnabled(true);
            }
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
               logArea.append("----------------------------------------\n");
               logArea.append("  PID: " + p.getPid() + " | Nombre: " + p.getNombre() + "\n");
               logArea.append("    - Tiempo de Arribo: " + p.getTiempoArribo() + "\n");
               logArea.append("    - Ráfagas de CPU: " + p.getCantidadRafagasCPU() + "\n");
               logArea.append("    - Duración Ráfaga CPU: " + p.getDuracionRafagaCPU() + "\n");
               logArea.append("    - Duración Ráfaga E/S: " + p.getDuracionRafagaES() + "\n");
               logArea.append("    - Prioridad: " + p.getPrioridadExterna() + "\n");
            }
            logArea.append("----------------------------------------\n");
            
            iniciarButton.setEnabled(true);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error al leer el archivo: " + ex.getMessage(), "Error de Archivo", JOptionPane.ERROR_MESSAGE);
            iniciarButton.setEnabled(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "El archivo no es un JSON válido: " + ex.getMessage(), "Error de Formato", JOptionPane.ERROR_MESSAGE);
            iniciarButton.setEnabled(false);
        }
    }

    private Planificador crearPlanificador(String nombreAlgoritmo){
        switch (nombreAlgoritmo) {
            case "FCFS":
                return new FCFS();
            case "Round-Robin":
                return new RoundRobin();
            default:
                return new FCFS(); // Por defecto    
        }
    }

    private class SimulacionWorker extends SwingWorker<List<Evento>, Void> {

        private Simulador simulador;
        // No necesitas una referencia a logArea aquí, puedes usar la de la clase externa

        public SimulacionWorker(Simulador simulador) {
            this.simulador = simulador;
        }

        @Override
        protected List<Evento> doInBackground() throws Exception {
            simulador.iniciar();
            return simulador.getLog();
        }

        @Override
        protected void done() {
            // ... lógica para actualizar la UI con los resultados ...
            // Puedes acceder a logArea e iniciarButton directamente.
            // Ejemplo:
            // logArea.append("Simulación terminada.\n");
            // iniciarButton.setEnabled(true);
        }
    }
}

