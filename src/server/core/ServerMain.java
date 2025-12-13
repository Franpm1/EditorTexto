package server.core;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import common.DocumentSnapshot; 
import common.IEditorService;
import server.infra.*;

public class ServerMain {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java server.core.ServerMain <ID> <PORT> [NODES]");
            System.out.println("Ejemplo: java server.core.ServerMain 0 1099 6");
            return;
        }

        try {
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");

            int myId = Integer.parseInt(args[0]);
            int port = Integer.parseInt(args[1]);
            int totalNodes = (args.length > 2) ? Integer.parseInt(args[2]) : 6; // CAMBIADO a 6

            System.out.println("\n" + "=".repeat(60));
            System.out.println("INICIANDO SERVIDOR " + myId);
            System.out.println("=".repeat(60));
            System.out.println("Puerto: " + port);
            System.out.println("ID: " + myId);
            System.out.println("Nodos totales: " + totalNodes);

            // 1. Lista de todos los servidores (0-5)
            List<RemoteServerInfo> allServers = new ArrayList<>();
            for (int i = 0; i < totalNodes; i++) {
                int serverPort = 1099 + i; // Puertos: 1099-1104
                allServers.add(new RemoteServerInfo(i, "127.0.0.1", serverPort, "EditorService"));
                System.out.println("   - Servidor " + i + " en puerto " + serverPort);
            }

            // 2. Estado inicial (solo el servidor 0 es líder inicial)
            boolean isInitialLeader = (myId == 0);
            ServerState state = new ServerState(myId, isInitialLeader);
            System.out.println("Lider inicial: " + (isInitialLeader ? "ESTE SERVIDOR" : "Servidor 0"));

            // 3. Componentes del sistema
            Notifier notifier = new Notifier(state);
            Document document = new Document(myId, totalNodes); // IMPORTANTE: totalNodes para VectorClock
            EditorServiceImpl service = new EditorServiceImpl(document, notifier, state);
            
            // 4. Conector para replicación
            ServerConnectorImpl connector = new ServerConnectorImpl(myId, allServers);
            service.setBackupConnector(connector);

            // 5. Crear/obtener registro RMI
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
                System.out.println("Registro RMI creado en puerto " + port);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(port);
                System.out.println("Usando registro existente en puerto " + port);
            }

            // 6. Publicar servicio
            registry.rebind("EditorService", service);
            System.out.println("Servicio publicado como 'EditorService'");


            // 7. Sincronizar estado inicial por si existe una desconexión previa
            if (allServers.size() > 1) {
                System.out.println("\n--> Verificando relojes con el clúster...");
                
                for (RemoteServerInfo info : allServers) {
                    if (info.getServerId() == myId) continue;
                    try {
                        IEditorService remote = info.getStub();
                        DocumentSnapshot snapshot = remote.getCurrentState();
                        
                        common.VectorClock myClock = document.getClockCopy();
                        common.VectorClock remoteClock = snapshot.getClock();
                        
                        // Si el remoto es "futuro" respecto a mí, o yo estoy a cero
                        if (remoteClock.isNewer(myClock) || (myClock.isZero() && !remoteClock.isZero())) {
                            System.out.println(" Detectado estado más avanzado en Servidor " + info.getServerId());
                            System.out.println(" Sincronizando datos...");
                            document.overwriteState(snapshot.getContent(), snapshot.getClock());
                            System.out.println(" Sincronización completada.");
                            break; // Ya tenemos datos válidos
                        }
                    } catch (Exception e) { /* Ignorar caídos */ }
                }
                System.out.println("--> Verificación terminada.\n");
            }

            // 8. Iniciar elección de líder y monitor de latidos
            if (allServers.size() > 1) {
                try {
                    BullyElection bully = new BullyElection(state, allServers, service);
                    Thread.sleep(1000); 
                    new Thread(new HeartbeatMonitor(state, bully, 2000)).start();
                    System.out.println("Monitor de latidos iniciado (2s).");
                } catch (Exception e) {
                    System.err.println("Error monitor: " + e.getMessage());
                }
            } else {
                state.setLeader(true);
                state.setCurrentLeaderId(myId);
                System.out.println("Líder automático (único nodo).");
            }


            // 9. Mostrar resumen
            System.out.println("\n" + "=".repeat(60));
            System.out.println("SERVIDOR " + myId + " LISTO");
            System.out.println("=".repeat(60));
            System.out.println("URL: rmi://127.0.0.1:" + port + "/EditorService");
            System.out.println("Estado: " + (state.isLeader() ? "LIDER" : "BACKUP"));
            System.out.println("Lider actual: " + state.getCurrentLeaderId());
            System.out.println("=".repeat(60) + "\n");

        } catch (Exception e) {
            System.err.println("\nERROR INICIANDO SERVIDOR " + args[0] + ":");
            e.printStackTrace();
        }
    }
}