package server.infra;

import common.IEditorService;
import common.VectorClock;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BullyElection {

    /** Provider LOCAL (no RMI) para snapshot y hook al core */
    public interface StateProvider {
        String getDocumentSnapshot();
        VectorClock getClockSnapshot();
        void onBecameLeader();
    }

    private final ServerState state;
    private final List<RemoteServerInfo> allServers;
    private final StateProvider provider;

    private final AtomicBoolean electionInProgress = new AtomicBoolean(false);

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, StateProvider provider) {
        this.state = state;
        this.allServers = allServers;
        this.provider = provider;
    }

    public void onLeaderDown() {
        if (state.isLeader()) return;

        if (!electionInProgress.compareAndSet(false, true)) {
            return; // ya hay elección en curso
        }

        try {
            int myId = state.getMyServerId();
            System.out.println("[Bully] Detectado fallo de líder. Elección... (yo=" + myId + ")");

            // Elegimos ID más alto vivo (determinista)
            int highestAlive = myId;

            for (RemoteServerInfo info : allServers) {
                int id = info.getServerId();
                if (id <= myId) continue;

                try {
                    info.getStub().heartbeat();
                    highestAlive = Math.max(highestAlive, id);
                } catch (Exception ignored) {
                    // no responde: no lo contamos
                }
            }

            if (highestAlive == myId) {
                becomeLeaderNow();
            } else {
                System.out.println("[Bully] Hay un ID mayor vivo. Nuevo líder esperado: " + highestAlive);
                state.setLeader(false);
                state.setCurrentLeaderId(highestAlive);
            }
        } finally {
            electionInProgress.set(false);
        }
    }

    private void becomeLeaderNow() {
        int myId = state.getMyServerId();
        System.out.println("[Bully] Me proclamo LÍDER (ID " + myId + ")");

        // Estado local
        state.setLeader(true);
        state.setCurrentLeaderId(myId);

        // Aviso al core (aceptar escrituras + persistencia)
        try {
            provider.onBecameLeader();
        } catch (Exception e) {
            System.out.println("[Bully] Error onBecameLeader(): " + e.getMessage());
        }

        // Snapshot del estado para sincronizar al resto
        String doc = provider.getDocumentSnapshot();
        VectorClock clock = provider.getClockSnapshot();

        // Notificar y sincronizar a los demás
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == myId) continue;

            try {
                IEditorService stub = info.getStub();
                stub.declareLeader(myId);
                stub.applyReplication(doc, clock); // estado completo -> backups consistentes + persistencia
                System.out.println("[Bully] Notificado+sync a servidor " + info.getServerId());
            } catch (Exception e) {
                System.out.println("[Bully] No se pudo notificar/sync a servidor " + info.getServerId());
            }
        }

        System.out.println("[Bully] Ahora acepto escrituras como líder.");
    }

    public RemoteServerInfo getCurrentLeaderInfo() {
        int leaderId = state.getCurrentLeaderId();
        if (leaderId == -1) return null;

        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == leaderId) return info;
        }
        return null;
    }
}
