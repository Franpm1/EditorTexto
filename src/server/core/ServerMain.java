package server.core;

import server.infra.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

public class ServerMain {

    public static void main(String[] args) {
        try {
            if (args.length < 3) {
                System.out.println("Uso: java server.core.ServerMain <myId> <port> <totalNodes>");
                return;
            }

            int myId = Integer.parseInt(args[0]);
            int port = Integer.parseInt(args[1]);
            int totalNodes = Integer.parseInt(args[2]);

            // Estado
            ServerState state = new ServerState(myId, false);

            // Inicialmente podéis poner 0 si sabéis que 0 arranca como líder;
            // si queréis elección al arrancar, dejad -1.
            state.setCurrentLeaderId(0);

            // Document con persistencia pro
            Document document = new Document(myId, totalNodes);
            try {
                document.recoverFromDisk();
                System.out.println("[PERSIST] Recovery OK. Doc='" + document.getContent() + "'");
                System.out.println("[PERSIST] Clock=" + document.getClockCopy());
            } catch (Exception e) {
                System.out.println("[PERSIST] Recovery falló (continúo): " + e.getMessage());
            }

            // Notifier
            Notifier notifier = new Notifier(state);

            // Lista de servidores
            List<RemoteServerInfo> allServers = new ArrayList<>();
            for (int i = 0; i < totalNodes; i++) {
                int p = 1099 + i; // ejemplo: 1099,1100,1101...
                allServers.add(new RemoteServerInfo(i, "127.0.0.1", p, "EditorService"));
            }

            // Backups = todos menos yo
            List<RemoteServerInfo> backups = new ArrayList<>();
            for (RemoteServerInfo info : allServers) {
                if (info.getServerId() != myId) backups.add(info);
            }

            // Connector (replicación del líder a backups)
            ServerConnectorImpl connector = new ServerConnectorImpl(myId, backups);

            // Servicio RMI
            EditorServiceImpl service = new EditorServiceImpl(document, notifier, connector, state);

            // Registry
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(port);
            }
            registry.rebind("EditorService", service);
            System.out.println("Servidor " + myId + " publicado en puerto " + port);

            // Bully + Heartbeat
            BullyElection bully = new BullyElection(state, allServers, service);
            HeartbeatMonitor hb = new HeartbeatMonitor(state, bully, 1000);
            new Thread(hb, "Heartbeat-" + myId).start();

            System.out.println("Servidor " + myId + " listo.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
