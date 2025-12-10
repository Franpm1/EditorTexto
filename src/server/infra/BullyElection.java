package server.infra;

import common.IEditorService;
import common.VectorClock;

import java.rmi.RemoteException;
import java.util.List;

/**
 * Implementación simplificada del algoritmo Bully
 * basada en heartbeats y elección local.
 *
 * No usa startElection ni sendCoordinator.
 *
 * Flujo:
 * - HeartbeatMonitor detecta que el líder ya no responde
 * - BullyElection.onLeaderDown() se ejecuta
 * - Se busca el ID más alto vivo mediante heartbeat()
 * - Ese servidor se proclama líder (si soy yo) con becomeLeader()
 *   o se marca líder remoto si es otro
 */
public class BullyElection {

    private final ServerState serverState;
    private final List<RemoteServerInfo> allServers;
    private final EditorDocumentProvider documentProvider;

    public interface EditorDocumentProvider {
        String getDocumentSnapshot() throws RemoteException;
        VectorClock getClockSnapshot() throws RemoteException;
        void onBecameLeader();               // activar modo líder
    }

    public BullyElection(ServerState serverState,
                         List<RemoteServerInfo> allServers,
                         EditorDocumentProvider documentProvider) {
        this.serverState = serverState;
        this.allServers = allServers;
        this.documentProvider = documentProvider;
    }

    /**
     * Devuelve la referencia del líder actual conocido.
     */
    public RemoteServerInfo getCurrentLeaderInfo() {
        int id = serverState.getCurrentLeaderId();
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == id) {
                return info;
            }
        }
        return null;
    }

    /**
     * Este método se invoca cuando HeartbeatMonitor detecta
     * que el líder ya no responde.
     *
     * No se hace por RMI: elección local mediante heartbeat().
     */
    public void onLeaderDown() {
        int myId = serverState.getMyServerId();

        System.out.println("[Bully] El líder ha caído. Iniciando elección local.");

        // Empieza asumiendo que yo soy el ID más alto vivo
        int highestAlive = myId;

        // Buscar cualquier servidor con ID mayor que yo que siga vivo
        for (RemoteServerInfo info : allServers) {
            int id = info.getServerId();
            if (id <= myId) continue; // solo me interesan mayores

            try {
                IEditorService stub = info.getStub();
                stub.heartbeat(); // si responde, está vivo
                highestAlive = id; // este tiene ID más alto vivo
            } catch (Exception ignored) {
                // si falla el heartbeat, no lo consideramos vivo
            }
        }

        if (highestAlive == myId) {
            // Nadie con ID mayor está vivo -> yo soy el nuevo líder
            becomeLeaderNow();
        } else {
            // Otro servidor tiene ID mayor vivo -> él es el líder
            System.out.println("[Bully] Nuevo líder detectado: " + highestAlive);
            serverState.setLeader(false);
            serverState.setCurrentLeaderId(highestAlive);
        }
    }

    /**
     * Este servidor se proclama líder porque:
     * - es el ID vivo más alto
     * o
     * - no hay otro líder funcional.
     *
     * Acciones:
     * 1. cambiar estado local
     * 2. notificar al núcleo para aceptar writes
     * 3. mandar snapshot con becomeLeader()
     */
    private void becomeLeaderNow() {
        int myId = serverState.getMyServerId();
        System.out.println("[Bully] Soy el nuevo líder (" + myId + ").");

        serverState.setLeader(true);
        serverState.setCurrentLeaderId(myId);

        // El núcleo (EditorServiceImpl) empieza a aceptar operaciones
        try {
            documentProvider.onBecameLeader();
        } catch (Exception e) {
            System.err.println("[Bully] Error en onBecameLeader(): " + e);
        }

        // Snapshot del documento + reloj vectorial
        String doc;
        VectorClock clock;
        try {
            doc = documentProvider.getDocumentSnapshot();
            clock = documentProvider.getClockSnapshot();
        } catch (RemoteException e) {
            System.err.println("[Bully] Error obteniendo snapshot al ser líder: " + e);
            // fallback mínimo
            doc = "";
            clock = new VectorClock();
        }

        // Enviar snapshot a todos los demás servidores (fire & forget)
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == myId) continue;

            try {
                IEditorService stub = info.getStub();
                stub.becomeLeader(doc, clock);
            } catch (Exception e) {
                System.out.println("[Bully] No puedo notificar a " + info +
                        " que soy el líder. Lo ignoramos.");
            }
        }
    }
}
