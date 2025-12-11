package client;

import common.IEditorService;
import common.Operation;
import common.VectorClock;
import java.rmi.RemoteException;
import java.util.Scanner;

public class ConsoleUI {
    private IEditorService server;
    private final String username;
    private boolean running = true;
    private String lastContent = "";
    private String lastClock = "[0,0,0]";
    private final Scanner scanner = new Scanner(System.in);

    public ConsoleUI(String username) { 
        this.username = username; 
    }
    
    public void setServer(IEditorService server) { 
        this.server = server; 
        System.out.println("✅ Servidor configurado");
    }

    public void updateView(String content, VectorClock clock) {
        System.out.println("\n[ACTUALIZACIÓN RECIBIDA]");
        this.lastContent = content;
        if (clock != null) this.lastClock = clock.toString();
        displayCurrentState();
    }

    private void displayCurrentState() {
        clearScreen();
        System.out.println("=".repeat(50));
        System.out.println(" EDITOR COLABORATIVO - " + username);
        System.out.println("=".repeat(50));
        System.out.println(" VECTOR CLOCK: " + lastClock);
        System.out.println("-".repeat(50));
        
        if (lastContent.isEmpty()) {
            System.out.println(" DOCUMENTO: (vacío)");
        } else {
            System.out.println(" DOCUMENTO:\n" + lastContent);
        }
        
        System.out.println("-".repeat(50));
        System.out.println(" Comandos: insert <pos> <texto> | delete <pos> <len> | help | exit");
        System.out.print("> ");
    }

    private void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // Si falla, solo imprimimos líneas nuevas
            System.out.println("\n".repeat(50));
        }
    }

    public void start() {
        displayCurrentState();
        
        while (running) {
            try {
                String line = scanner.nextLine().trim();
                
                if (line.isEmpty()) {
                    System.out.print("> ");
                    continue;
                }
                
                if (line.equalsIgnoreCase("exit")) {
                    System.out.println(" Saliendo...");
                    running = false;
                    break;
                }
                
                processCommand(line);
                
            } catch (Exception e) {
                System.err.println(" Error: " + e.getMessage());
                System.out.print("> ");
            }
        }
        scanner.close();
    }

    private void processCommand(String line) {
        String[] parts = line.split("\\s+", 3);
        String cmd = parts[0].toLowerCase();
        
        switch (cmd) {
            case "insert":
                if (parts.length < 3) {
                    System.err.println(" Uso: insert <posición> <texto>");
                    break;
                }
                try {
                    int pos = Integer.parseInt(parts[1]);
                    String text = parts[2];
                    Operation op = new Operation("INSERT", pos, text, username, null);
                    server.executeOperation(op);
                    System.out.println(" Operación enviada. Esperando actualización...");
                } catch (NumberFormatException e) {
                    System.err.println(" La posición debe ser un número");
                } catch (RemoteException e) {
                    System.err.println(" Error de conexión: " + e.getMessage());
                }
                break;
                
            case "delete":
                if (parts.length < 3) {
                    System.err.println(" Uso: delete <posición> <longitud>");
                    break;
                }
                try {
                    int pos = Integer.parseInt(parts[1]);
                    int len = Integer.parseInt(parts[2]);
                    String dummy = "x".repeat(len);
                    Operation op = new Operation("DELETE", pos, dummy, username, null);
                    server.executeOperation(op);
                    System.out.println(" Operación enviada. Esperando actualización...");
                } catch (NumberFormatException e) {
                    System.err.println(" Posición y longitud deben ser números");
                } catch (RemoteException e) {
                    System.err.println(" Error de conexión: " + e.getMessage());
                }
                break;
                
            case "help":
                showHelp();
                break;
                
            case "show":
                displayCurrentState();
                break;
                
            case "clear":
                clearScreen();
                System.out.print("> ");
                break;
                
            default:
                System.err.println(" Comando desconocido. Escribe 'help' para ayuda.");
                break;
        }
        
        if (!cmd.equals("clear")) {
            System.out.print("> ");
        }
    }

    private void showHelp() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println(" AYUDA - EDITOR DISTRIBUIDO");
        System.out.println("=".repeat(50));
        System.out.println("insert <posición> <texto>");
        System.out.println("  Inserta texto en la posición especificada");
        System.out.println("  Ejemplo: insert 0 Hola mundo");
        System.out.println();
        System.out.println("delete <posición> <longitud>");
        System.out.println("  Borra caracteres desde la posición");
        System.out.println("  Ejemplo: delete 0 5 (borra 5 caracteres)");
        System.out.println();
        System.out.println("show   - Muestra el documento actual");
        System.out.println("clear  - Limpia la pantalla");
        System.out.println("help   - Muestra esta ayuda");
        System.out.println("exit   - Sale del programa");
        System.out.println("=".repeat(50) + "\n");
    }
}