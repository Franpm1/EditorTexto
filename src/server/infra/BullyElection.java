package server;

import common.IEditorService;

import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementación simplificada del algoritmo Bully. Gestiona procesos de elección,
 * detecta servidores superiores vivos y determina quién debe ser el nuevo líder.
 */
public class BullyElection {

    private final EditorServiceImpl localServer;
    private final List<RemoteServerInfo> allServers; // La lista incluye al propio servidor
    private final AtomicInteger currentLeaderId = new AtomicInteger(-1);

    // Indica si ya se está llevando a cabo un proceso de elección
    private volatile boolean inElection = false;

    public BullyElection(EditorServiceImpl localServer, List<RemoteServerInfo> allServers, int initialLeaderId) {
        this.localServer = localServer;
        this.allServers = allServers;
        this.currentLeaderId.set(initialLeaderId);
    }

    /** Devuelve el ID del líder actual. */
    public int getCurrentLeaderId() {
        return currentLeaderId.get();
    }

    /** Devuelve el descriptor del servidor líder actual. */
    public RemoteServerInfo getCurrentLeaderInfo() {
        int id = currentLeaderId.get();
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == id) return info;
        }
        return null;
    }

    /**
     * Inicio oficial del algoritmo Bully. Se ejecuta cuando el heartbeat detecta
     * que el líder ha caído o cuando no existe un líder conocido.
     */
    public synchronized void startElection() {
        if (inElection) return;
        inElection = true;

        int myId;
        try {
            myId = localServer.getServerId();
        } catch (RemoteException e) {
            myId = -1;
        }

        System.out.println("[Server " + myId + "] Iniciando ELECTION.");

        boolean higherResponded = false;

        // Consultar servidores con ID superior al mío
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() <= myId) continue;

            try {
                IEditorService stub = info.getStub();
                stub.heartbeat(); // Si responde, el servidor superior está vivo
                higherResponded = true;
                System.out.println("[Server " + myId + "] Servidor " + info.getServerId() + " vive y es mayor.");
            } catch (Exception e) {
                System.out.println("[Server " + myId + "] Servidor " + info + " no responde.");
            }
        }

        // Si ninguno superior responde → este servidor se convierte en líder
        if (!higherResponded) {
            becomeLeaderAndBroadcast();
        } else {
            // Esperar un tiempo por si un servidor superior se proclama líder
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

                if (inElection) {
                    System.out.println("[Server " + myId + "] Ningún mayor se proclamó. Me autoproclamo líder.");
                    becomeLeaderAndBroadcast();
                }
            }).start();
        }
    }

    /**
     * Proclama a este servidor como líder y notifica a todos los demás enviando
     * el estado actual del documento.
     */
    private synchronized void becomeLeaderAndBroadcast() {
        int myId;
        try {
            myId = localServer.getServerId();
        } catch (RemoteException e) {
            myId = -1;
        }

        System.out.println("[Server " + myId + "] Soy el nuevo LÍDER (Bully).");

        localServer.setLeader(true);
        currentLeaderId.set(myId);
        inElection = false;

        // Enviar snapshot de documento y reloj vectorial
        String doc;
        common.VectorClock vc;

        try {
            doc = localServer.getDocument();
            vc = localServer.getClock();
        } catch (RemoteException e) {
            doc = "";
            vc = new common.VectorClock();
        }

        // Notificar a todos los servidores que hay un nuevo líder
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == myId) continue;

            try {
                IEditorService stub = info.getStub();
                stub.becomeLeader(doc, vc);
            } catch (Exception e) {
                System.out.println("[Server " + myId + "] Error notificando a " + info);
            }
        }
    }
}
