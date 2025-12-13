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
            System.out.println("Ejemplo: java server.core.ServerMain 0 1099 config.txt");
            System.out.println("Ejemplo LAN: java server.core.ServerMain 0 1099 lan_config.txt");
            return;
        }

        try {
            // Obtener IP local automáticamente
            String localIP = InetAddress.getLocalHost().getHostAddress();
            System.out.println("IP Local detectada: " + localIP);
            
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.rmi.server.hostname", localIP); // Usar IP real

            int myId = Integer.parseInt(args[0]);
            int port = Integer.parseInt(args[1]);
            String configFile = (args.length > 2) ? args[2] : "config.txt";

            System.out.println("\n" + "=".repeat(60));
            System.out.println("INICIANDO SERVIDOR " + myId + " EN " + localIP + ":" + port);
            System.out.println("=".repeat(60));
            System.out.println("Puerto: " + port);
            System.out.println("ID: " + myId);
            System.out.println("Archivo config: " + configFile);

            // 1. Cargar configuración de servidores desde archivo
            System.out.println("\nCargando configuracion LAN...");
            List<RemoteServerInfo> allServers = ConfigLoader.loadConfig(configFile, myId);
            System.out.println("Total servidores configurados: " + allServers.size());

            // 2. Verificar que mi ID está en la configuración
            boolean myIdFound = false;
            for (RemoteServerInfo info : allServers) {
                if (info.getServerId() == myId) {
                    myIdFound = true;
                    System.out.println("Mi config: " + info);
                    break;
                }
            }
            
            if (!myIdFound) {
                System.err.println("ERROR: Mi ID " + myId + " no encontrado en " + configFile);
                System.err.println("Agrega esta linea al archivo:");
                System.err.println(myId + "=" + localIP + ":" + port);
                return;
            }

            // 3. Estado inicial - nadie es líder al inicio
            ServerState state = new ServerState(myId, false);
            System.out.println("Modo: Lider se elegira automaticamente (mayor ID presente)");

            // 4. Componentes del sistema
            Notifier notifier = new Notifier(state);
            Document document = new Document(myId, allServers.size()); // Tamaño según servidores config
            EditorServiceImpl service = new EditorServiceImpl(document, notifier, state);
            
            // 5. Conector para replicación
            ServerConnectorImpl connector = new ServerConnectorImpl(myId, allServers);
            service.setBackupConnector(connector);

            // 6. Crear/obtener registro RMI
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
                System.out.println("Registro RMI creado en " + localIP + ":" + port);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(localIP, port);
                System.out.println("Usando registro existente en " + localIP + ":" + port);
            }

            // 7. Publicar servicio
            registry.rebind("EditorService", service);
            System.out.println("Servicio publicado como 'EditorService'");

            // 8. Iniciar sistema de elección y heartbeat
            if (allServers.size() > 1) {
                try {
                    BullyElection bully = new BullyElection(state, allServers, service);
                    new Thread(new HeartbeatMonitor(state, bully, 2000)).start();
                    System.out.println("Monitor de latidos iniciado (intervalo: 2s)");
                } catch (Exception e) {
                    System.err.println("Error iniciando monitor: " + e.getMessage());
                }
            } else {
                // Si solo hay 1 nodo, es automáticamente líder
                state.setLeader(true);
                state.setCurrentLeaderId(myId);
                System.out.println("Unico nodo, soy lider automaticamente");
            }

            // 9. Mostrar resumen
            System.out.println("\n" + "=".repeat(60));
            System.out.println("SERVIDOR " + myId + " LISTO PARA LAN");
            System.out.println("=".repeat(60));
            System.out.println("URL: rmi://" + localIP + ":" + port + "/EditorService");
            System.out.println("Estado: " + (state.isLeader() ? "LIDER" : "BACKUP"));
            System.out.println("Lider actual: " + state.getCurrentLeaderId());
            System.out.println("Servidores conocidos: " + allServers.size());
            System.out.println("=".repeat(60) + "\n");

        } catch (Exception e) {
            System.err.println("\nERROR INICIANDO SERVIDOR " + args[0] + ":");
            e.printStackTrace();
            System.err.println("\nSugerencia: Verifica que:");
            System.err.println("1. El archivo config.txt existe");
            System.err.println("2. Tu IP está correcta en el archivo");
            System.err.println("3. Firewall permite puertos 1099-1104");
        }
    }
}