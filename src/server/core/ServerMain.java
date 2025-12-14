package server.core;

import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import server.infra.*;

public class ServerMain {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java server.core.ServerMain <ID> <PORT> [CONFIG_FILE]");
            return;
        }

        try {
            // Configurar timeouts globales al inicio
            System.setProperty("sun.rmi.transport.tcp.responseTimeout", "1000");
            System.setProperty("sun.rmi.transport.tcp.readTimeout", "1000");
            System.setProperty("sun.rmi.transport.connectionTimeout", "1000");
            System.setProperty("sun.rmi.transport.proxy.connectTimeout", "1000");
            System.setProperty("sun.rmi.transport.tcp.handshakeTimeout", "1000");
            System.setProperty("sun.rmi.dgc.ackTimeout", "1000");
            
            String localIP = InetAddress.getLocalHost().getHostAddress();
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.rmi.server.hostname", localIP);

            int myId = Integer.parseInt(args[0]);
            int port = Integer.parseInt(args[1]);
            String configFile = (args.length > 2) ? args[2] : "config.txt";

            System.out.println("INICIANDO SERVIDOR " + myId + " EN " + localIP + ":" + port);

            List<RemoteServerInfo> allServers = ConfigLoader.loadConfig(configFile, myId);
            
            boolean myIdFound = false;
            for (RemoteServerInfo info : allServers) {
                if (info.getServerId() == myId) {
                    myIdFound = true;
                    break;
                }
            }
            
            if (!myIdFound) {
                System.err.println("ERROR: Mi ID " + myId + " no encontrado");
                return;
            }

            ServerState state = new ServerState(myId, false);
            Notifier notifier = new Notifier(state);
            Document document = new Document(myId, allServers.size());
            EditorServiceImpl service = new EditorServiceImpl(document, notifier, state);
            
            ServerConnectorImpl connector = new ServerConnectorImpl(myId, allServers);
            service.setBackupConnector(connector);

            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(localIP, port);
            }

            registry.rebind("EditorService", service);

            if (allServers.size() > 1) {
                try {
                    BullyElection bully = new BullyElection(state, allServers, service);
                    // Intervalo más corto: 1500ms
                    new Thread(new HeartbeatMonitor(state, bully, 200)).start();
                } catch (Exception e) {
                    // Continuar
                }
            } else {
                state.setLeader(true);
                state.setCurrentLeaderId(myId);
            }

            System.out.println("✅ SERVIDOR " + myId + " LISTO");
            System.out.println("URL: rmi://" + localIP + ":" + port + "/EditorService");
            System.out.println("Estado: " + (state.isLeader() ? "LÍDER" : "BACKUP"));

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
        }
    }
}