package client;

import common.IEditorService;
import common.Operation;
import common.VectorClock;
import java.rmi.RemoteException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsoleUI {
    private IEditorService server;
    private final String username;
    private boolean running = true;
    private String lastContent = "";
    private String lastClock = "[0,0,0]";
    private final Scanner scanner = new Scanner(System.in);
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);
    private int updateCount = 0;

    public ConsoleUI(String username) { 
        this.username = username; 
        System.out.println("👤 Cliente inicializado como: " + username);
    }
    
    public void setServer(IEditorService server) { 
        this.server = server; 
        System.out.println("✅ Servidor configurado en cliente");
    }

    public void updateView(String content, VectorClock clock) {
        updateCount++;
        
        if (isUpdating.get()) {
            System.out.println("\n[ACTUALIZACIÓN #" + updateCount + " - PENDIENTE]");
            return;
        }
        
        isUpdating.set(true);
        
        try {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("📥 ACTUALIZACIÓN #" + updateCount + " RECIBIDA");
            System.out.println("=".repeat(60));
            
            // Verificar si el contenido cambió
            boolean contentChanged = !this.lastContent.equals(content);
            boolean clockChanged = clock != null && !this.lastClock.equals(clock.toString());
            
            if (contentChanged) {
                System.out.println("✓ CONTENIDO ACTUALIZADO");
                if (!lastContent.isEmpty()) {
                    System.out.println("  Anterior: " + truncate(lastContent, 40));
                }
                System.out.println("  Nuevo: " + truncate(content, 40));
            } else {
                System.out.println("⚠️ Mismo contenido, solo refresh");
            }
            
            if (clockChanged) {
                System.out.println("🔄 Vector Clock actualizado: " + clock);
            }
            
            System.out.println("=".repeat(60));
            
            this.lastContent = content;
            if (clock != null) this.lastClock = clock.toString();
            
            // Forzar actualización visual inmediata
            forceDisplayCurrentState();
            
        } finally {
            isUpdating.set(false);
        }
    }
    
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...[" + (text.length() - maxLength) + " más]";
    }

    private void forceDisplayCurrentState() {
        // Limpiar y mostrar estado inmediatamente
        clearScreen();
        displayHeader();
        displayDocument();
        displayCommands();
        prompt();
    }

    private void displayHeader() {
        System.out.println("=".repeat(60));
        System.out.println("✏️  EDITOR COLABORATIVO - " + username.toUpperCase());
        System.out.println("=".repeat(60));
        System.out.println("📊 VECTOR CLOCK: " + lastClock);
        System.out.println("🔄 ACTUALIZACIONES RECIBIDAS: " + updateCount);
        System.out.println("-".repeat(60));
    }

    private void displayDocument() {
        if (lastContent.isEmpty()) {
            System.out.println("📄 DOCUMENTO: (vacío)");
        } else {
            System.out.println("📄 DOCUMENTO (" + lastContent.length() + " caracteres):");
            System.out.println("\"" + lastContent + "\"");
        }
        System.out.println("-".repeat(60));
    }

    private void displayCommands() {
        System.out.println("🎮 COMANDOS DISPONIBLES:");
        System.out.println("  insert <posición> <texto>  - Insertar texto");
        System.out.println("  delete <posición> <longitud> - Borrar texto");
        System.out.println("  refresh                    - Forzar actualización");
        System.out.println("  status                     - Ver estado actual");
        System.out.println("  help                       - Mostrar ayuda completa");
        System.out.println("  clear                      - Limpiar pantalla");
        System.out.println("  exit                       - Salir del programa");
        System.out.println("-".repeat(60));
    }

    private void prompt() {
        System.out.print("> ");
        System.out.flush();
    }

    public void refreshView() {
        System.out.println("\n[REFRESH SOLICITADO]");
        displayCurrentState();
    }

    public void showStatus() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📈 ESTADO DEL CLIENTE");
        System.out.println("=".repeat(60));
        System.out.println("Usuario: " + username);
        System.out.println("Actualizaciones recibidas: " + updateCount);
        System.out.println("Último Vector Clock: " + lastClock);
        System.out.println("Longitud documento: " + lastContent.length() + " caracteres");
        System.out.println("Servidor configurado: " + (server != null ? "Sí" : "No"));
        System.out.println("=".repeat(60));
        prompt();
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
            // Fallback: imprimir líneas nuevas
            System.out.println("\n".repeat(50));
        }
    }

    public void start() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🚀 INICIANDO EDITOR COLABORATIVO");
        System.out.println("=".repeat(60));
        displayCurrentState();
        
        while (running) {
            try {
                String line = scanner.nextLine().trim();
                
                if (line.isEmpty()) {
                    System.out.print("> ");
                    continue;
                }
                
                if (line.equalsIgnoreCase("exit")) {
                    System.out.println("👋 Saliendo...");
                    running = false;
                    break;
                }
                
                processCommand(line);
                
            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
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
                    System.err.println("❌ Uso: insert <posición> <texto>");
                    break;
                }
                try {
                    int pos = Integer.parseInt(parts[1]);
                    String text = parts[2];
                    Operation op = new Operation("INSERT", pos, text, username, null);
                    server.executeOperation(op);
                    System.out.println("📤 Operación INSERT enviada. Esperando actualización...");
                } catch (NumberFormatException e) {
                    System.err.println("❌ La posición debe ser un número");
                } catch (RemoteException e) {
                    System.err.println("❌ Error de conexión: " + e.getMessage());
                    System.out.println("💡 Intenta 'refresh' para reconectar");
                } catch (Exception e) {
                    System.err.println("❌ Error inesperado: " + e.getMessage());
                }
                break;
                
            case "delete":
                if (parts.length < 3) {
                    System.err.println("❌ Uso: delete <posición> <longitud>");
                    break;
                }
                try {
                    int pos = Integer.parseInt(parts[1]);
                    int len = Integer.parseInt(parts[2]);
                    String dummy = "x".repeat(len);
                    Operation op = new Operation("DELETE", pos, dummy, username, null);
                    server.executeOperation(op);
                    System.out.println("📤 Operación DELETE enviada. Esperando actualización...");
                } catch (NumberFormatException e) {
                    System.err.println("❌ Posición y longitud deben ser números");
                } catch (RemoteException e) {
                    System.err.println("❌ Error de conexión: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("❌ Error inesperado: " + e.getMessage());
                }
                break;
                
            case "refresh":
                refreshView();
                break;
                
            case "status":
                showStatus();
                break;
                
            case "help":
                showHelp();
                break;
                
            case "show":
                displayCurrentState();
                break;
                
            case "clear":
                clearScreen();
                displayCurrentState();
                break;
                
            case "force":
                // Comando secreto para forzar actualización
                System.out.println("⚡ Forzando actualización manual...");
                if (server != null) {
                    try {
                        // Intentar una operación de ping
                        server.heartbeat();
                        System.out.println("✓ Servidor responde");
                    } catch (Exception e) {
                        System.err.println("✗ Servidor no responde");
                    }
                }
                break;
                
            default:
                System.err.println("❌ Comando desconocido: '" + cmd + "'");
                System.out.println("💡 Escribe 'help' para ver comandos disponibles");
                break;
        }
        
        if (!cmd.equals("clear")) {
            System.out.print("> ");
        }
    }

    private void displayCurrentState() {
        clearScreen();
        displayHeader();
        displayDocument();
        displayCommands();
        prompt();
    }

    private void showHelp() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📚 AYUDA COMPLETA - EDITOR DISTRIBUIDO");
        System.out.println("=".repeat(60));
        
        System.out.println("\n📝 COMANDOS PRINCIPALES:");
        System.out.println("insert <posición> <texto>");
        System.out.println("  Inserta texto en la posición especificada (0-based)");
        System.out.println("  Ejemplo: insert 0 Hola mundo");
        System.out.println("           insert 5 , amigos");
        
        System.out.println("\ndelete <posición> <longitud>");
        System.out.println("  Borra 'longitud' caracteres desde la posición");
        System.out.println("  Ejemplo: delete 0 5   (borra primeros 5 caracteres)");
        System.out.println("           delete 10 3  (borra 3 caracteres desde posición 10)");
        
        System.out.println("\n🔄 COMANDOS DEL SISTEMA:");
        System.out.println("refresh   - Actualiza la vista manualmente");
        System.out.println("status    - Muestra estado interno del cliente");
        System.out.println("show      - Muestra el documento actual");
        System.out.println("clear     - Limpia la pantalla");
        System.out.println("help      - Muestra esta ayuda");
        System.out.println("exit      - Sale del programa");
        
        System.out.println("\n💡 INFORMACIÓN:");
        System.out.println("- Las actualizaciones se reciben automáticamente");
        System.out.println("- Vector Clock muestra la consistencia del documento");
        System.out.println("- Si no ves cambios, usa 'refresh'");
        System.out.println("- El servidor replica cambios a todos los clientes");
        
        System.out.println("\n⚠️ SOLUCIÓN DE PROBLEMAS:");
        System.out.println("1. Si no ves cambios: usa 'refresh'");
        System.out.println("2. Si hay error de conexión: verifica que el servidor esté activo");
        System.out.println("3. Si el documento no se actualiza: el líder pudo haber cambiado");
        System.out.println("4. Usa 'status' para verificar el estado actual");
        
        System.out.println("=".repeat(60));
        System.out.print("\n> ");
    }
    
    // Método para forzar actualización desde fuera
    public void forceUpdate(String content, VectorClock clock) {
        System.out.println("\n[ACTUALIZACIÓN FORZADA DESDE RECONEXIÓN]");
        updateView(content, clock);
    }
}