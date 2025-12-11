package client;

import common.IEditorService;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ClientMain {
    public static void main(String[] args) {
        System.out.println("=== CLIENTE ===");
        
        String username = "User";
        int port = 1099; // Default
        
        // Si recibe argumento, usar ese puerto
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                username = "User" + port;
            } catch (NumberFormatException e) {
                System.out.println("Usando puerto default 1099");
            }
        }
        
        System.out.println("Conectando a puerto: " + port);
        
        try {
            ConsoleUI ui = new ConsoleUI(username);
            ClientImpl clientCallback = new ClientImpl(ui);
            
            Registry registry = LocateRegistry.getRegistry("127.0.0.1", port);
            IEditorService serverStub = (IEditorService) registry.lookup("EditorService");
            
            serverStub.registerClient(clientCallback, username);
            ui.setServer(serverStub);
            
            System.out.println("Conectado al servidor en puerto " + port);
            ui.start();
            
        } catch (Exception e) {
            System.err.println("ERROR conectando: " + e.getMessage());
            System.out.println("Asegurate que el servidor en puerto " + port + " esta corriendo");
        }
    }
}