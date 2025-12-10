package server.core;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import server.infra.BullyElection;
import server.infra.HeartbeatMonitor;
import server.infra.Notifier;
import server.infra.RemoteServerInfo;
import server.infra.ServerState;
import server.infra.ServerConnectorImpl; 

public class ServerMain {

    public static void main(String[] args) {

        if (args.length < 2) {
            System.err.println("Uso correcto: java server.core.ServerMain <ID> <PUERTO>");
            System.exit(1);
        }

        try {
            int myId = Integer.parseInt(args[0]);
            int port = Integer.parseInt(args[1]);

            System.setProperty("java.rmi.server.hostname", "localhost");
            System.out.println("=== INICIANDO SERVIDOR [ID: " + myId + "] ===");

            // ---------------------------------------------------------
            // 0. CONFIGURACIÓN DE TOPOLOGÍA 
            // ---------------------------------------------------------
            List<RemoteServerInfo> allServers = new ArrayList<>();
            allServers.add(new RemoteServerInfo(0, "localhost", 1099, "EditorService"));
            allServers.add(new RemoteServerInfo(1, "localhost", 1100, "EditorService"));
            allServers.add(new RemoteServerInfo(2, "localhost", 1101, "EditorService"));
            
            int totalNodes = allServers.size();

            // ---------------------------------------------------------
            // 1. INICIALIZAR ESTADO E INFRAESTRUCTURA
            // ---------------------------------------------------------
            
            // CAMBIO 1: Creamos el ServerState PRIMERO porque Notifier lo necesita.
            ServerState serverState = new ServerState(myId, false);

            // Creamos el documento
            Document doc = new Document(myId, totalNodes);
            
            // CAMBIO 2: Pasamos myId y serverState al constructor de Notifier
            Notifier notifier = new Notifier(myId, serverState);

            // ---------------------------------------------------------
            // 2. CREAR Y PUBLICAR SERVICIO RMI
            // ---------------------------------------------------------
            EditorServiceImpl service = new EditorServiceImpl(myId, doc, notifier);
            
            // Filtramos la lista para obtener los "otros" servidores (para replicar)
            List<RemoteServerInfo> otherServers = allServers.stream()
                .filter(s -> s.getServerId() != myId)
                .collect(Collectors.toList());

            // Configurar conector de backups
            ServerConnectorImpl connector = new ServerConnectorImpl(otherServers);
            service.setBackupConnector(connector);

            // Registro RMI
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
                System.out.println(" > RMI Registry creado en puerto " + port);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(port);
                System.out.println(" > Conectado a Registry existente.");
            }
            registry.rebind("EditorService", service); 
            System.out.println(" > Servicio RMI publicado correctamente.");

            // ---------------------------------------------------------
            // 3. INICIAR INFRAESTRUCTURA (BULLY + HEARTBEAT)
            // ---------------------------------------------------------
            
            BullyElection election = new BullyElection(serverState, allServers, service);

            // Heartbeat vigila al líder. Intervalo de 2 segundos.
            HeartbeatMonitor monitor = new HeartbeatMonitor(serverState, election, 2000); 
            new Thread(monitor).start();

            System.out.println(" > Monitor de Heartbeat y Sistema de Elección iniciados.");
            System.out.println("=== SERVIDOR LISTO ===");

        } catch (Exception e) {
            System.err.println(" CRITICAL ERROR: El servidor no pudo arrancar.");
            e.printStackTrace();
        }
    }
}