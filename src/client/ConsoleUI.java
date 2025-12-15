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
        if (clock != null) {
            this.lastClock = clock.toString();
        }
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
        System.out.println(" Comandos: insert <pos> <texto> | jumpline <pos> | delete <pos> <len>");
        System.out.println("           replace <pos> <len> <texto_nuevo> | refresh | help | exit");
        System.out.print("> ");
    }

    private void clearScreen() {
        // SOLUCIÓN SEGURA: Imprimir saltos de línea.
        // Evita usar ProcessBuilder/cls porque interfiere con el buffer 
        // de entrada (System.in) cuando hay escritura concurrente.
        System.out.println("\n".repeat(50));    }

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

            case "jumpline":
                if (parts.length < 2) {
                    System.err.println(" Uso: jumpline <posición> ");
                    break;
                }
                try {
                    int pos = Integer.parseInt(parts[1]);
                    Operation op = new Operation("INSERT", pos, "\n", username, null);
                    server.executeOperation(op);
                    System.out.println(" Operación enviada. Esperando actualización...");
                } catch (NumberFormatException e) {
                    System.err.println(" La posición debe ser un número entero");
                } catch (RemoteException e) {
                    System.err.println(" Error de conexión: " + e.getMessage());
                }
                break;

            case "replace":
                try {
                    // Eliminar "replace " del inicio
                    String argsLine = line.substring(8).trim();
                    String[] replaceParts = argsLine.split("\\s+", 3);

                    if (replaceParts.length < 3) {
                        System.err.println(" Uso: replace <posición> <longitud_a_borrar> <texto_nuevo>");
                        System.err.println(" Ejemplo: replace 5 5 amigo");
                        break;
                    }

                    int pos = Integer.parseInt(replaceParts[0]);
                    int deleteLen = Integer.parseInt(replaceParts[1]);
                    String newText = replaceParts[2];

                    // Formato: "longitud|texto_nuevo"
                    String operationText = deleteLen + "|" + newText;
                    Operation op = new Operation("REPLACE", pos, operationText, username, null);
                    server.executeOperation(op);
                    System.out.println(" Sustitución enviada. Esperando actualización...");
                } catch (NumberFormatException e) {
                    System.err.println(" Posición y longitud deben ser números");
                } catch (RemoteException e) {
                    System.err.println(" Error de conexión: " + e.getMessage());
                } catch (StringIndexOutOfBoundsException e) {
                    System.err.println(" Uso: replace <posición> <longitud_a_borrar> <texto_nuevo>");
                }
                break;

            case "refresh":
                try {
                    // Enviar una operación nula o pedir sincronización
                    // Opción simple: solicitar estado actual al servidor
                    System.out.println(" Solicitando estado actual del servidor...");
                    // Como no hay método directo, enviamos una operación dummy que no cambie nada
                    Operation dummyOp = new Operation("INSERT", 0, "", username, null);
                    server.executeOperation(dummyOp);
                    System.out.println(" Refresh solicitado. Esperando actualización...");
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
        System.out.println("jumpline <posición>");
        System.out.println("  Inserta un salto de línea en la posición dada");
        System.out.println("  Ejemplo: jumpline 5");
        System.out.println();
        System.out.println("delete <posición> <longitud>");
        System.out.println("  Borra caracteres desde la posición");
        System.out.println("  Ejemplo: delete 0 5 (borra 5 caracteres)");
        System.out.println();
        System.out.println("replace <posición> <longitud_a_borrar> <texto_nuevo>");
        System.out.println("  Sustituye un fragmento por otro texto");
        System.out.println("  Ejemplo: replace 10 5 nuevo_texto");
        System.out.println();
        System.out.println("refresh  - Sincroniza manualmente con el servidor");
        System.out.println("show     - Muestra el documento actual");
        System.out.println("clear    - Limpia la pantalla");
        System.out.println("help     - Muestra esta ayuda");
        System.out.println("exit     - Sale del programa");
        System.out.println("=".repeat(50) + "\n");
    }
}
