package server.infra;

import common.IEditorService;
import common.VectorClock;

import java.rmi.RemoteException;
import java.util.List;


public class BullyElection {

    private final ServerState serverState;
    private final List<RemoteServerInfo> allServers;

    // Referencia al núcleo lógico para poder leer doc + clock cuando yo me convierta en líder.
    private final EditorDocumentProvider documentProvider;

    // Para evitar elecciones simultáneas locas
    private volatile boolean inElection = false;

    /**
     * Interface mínima que debe implementar el núcleo lógico (EditorServiceImpl)
     * para que el algoritmo pueda leer el estado y replicarlo.
     */
    public interface EditorDocumentProvider {
        String getDocumentSnapshot() throws RemoteException;
        VectorClock getClockSnapshot() throws RemoteException;

        /**
         * Se llama cuando ESTE servidor se convierte en líder,
         * para que empiece a aceptar peticiones de clientes.
         */
        void onBecameLeader();
    }

    /**
     * @param serverState Info local (mi ID y si soy líder)
     * @param allServers  Lista de todos los servidores (incluyéndome)
     * @param documentProvider Puente hacia EditorServiceImpl
     */
    public BullyElection(ServerState serverState,
                         List<RemoteServerInfo> allServers,
                         EditorDocumentProvider documentProvider) {
        this.serverState = serverState;
        this.allServers = allServers;
        this.documentProvider = documentProvider;
    }

    public int getCurrentLeaderId() {
        return serverState.getCurrentLeaderId();
    }

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
     * Inicia la elección según el algoritmo Bully.
     * Se puede llamar desde HeartbeatMonitor o manualmente en pruebas.
     */
    public synchronized void startElection() {
        if (inElection) {
            return; // ya hay una elección en marcha
        }
        inElection = true;

        int myId = serverState.getMyServerId();
        System.out.println("[Server " + myId + "] Iniciando ELECCIÓN (Bully).");

        boolean higherResponded = false;

        // Lanzar "mensaje de elección" a todos los servidores con ID > myId
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() <= myId) continue;

            try {
                IEditorService stub = info.getStub();
                // Comprobamos si está vivo con un heartbeat
                stub.heartbeat();
                higherResponded = true;
                System.out.println("[Server " + myId + "] Servidor " + info.getServerId()
                        + " responde y tiene ID mayor. Espero que él coordine.");
            } catch (Exception e) {
                System.out.println("[Server " + myId + "] Servidor " + info.getServerId()
                        + " no responde en elección.");
            }
        }

        if (!higherResponded) {
            // Nadie por encima de mí está vivo → yo seré el nuevo líder
            becomeLeaderAndBroadcast();
        } else {
            // En una implementación completa esperaríamos un "COORDINATOR".
            // Para simplificar, esperamos un poco y si nadie ha cerrado la elección,
            // nos autoproclamamos.
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}

                synchronized (BullyElection.this) {
                    if (inElection) {
                        System.out.println("[Server " + myId + "] Nadie se ha proclamado. Me autoproclamo líder.");
                        becomeLeaderAndBroadcast();
                    }
                }
            }).start();
        }
    }

    /**
     * Acción cuando este servidor decide que es el nuevo líder:
     *  - actualiza ServerState
     *  - avisa al núcleo lógico
     *  - envía estado a los demás servidores
     */
    private void becomeLeaderAndBroadcast() {
        int myId = serverState.getMyServerId();
        System.out.println("[Server " + myId + "] Soy el nuevo LÍDER (Bully).");

        // Actualizar estado local
        serverState.setLeader(true);
        inElection = false;

        // Avisar al núcleo lógico: a partir de ahora acepta peticiones
        try {
            documentProvider.onBecameLeader();
        } catch (Exception e) {
            System.err.println("[Server " + myId + "] Error notificando al EditorServiceImpl que soy líder: " + e);
        }

        // Snapshot del documento y reloj para mandar a los demás
        String doc;
        VectorClock clock;
        try {
            doc = documentProvider.getDocumentSnapshot();
            clock = documentProvider.getClockSnapshot();
        } catch (RemoteException e) {
            System.err.println("[Server " + myId + "] Error obteniendo estado local al ser líder: " + e);
            doc = "";
            clock = new VectorClock(); // mínimo
        }

        // Notificar a todos los demás servidores
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == myId) continue; // yo no

            try {
                IEditorService stub = info.getStub();
                // becomeLeader en el remoto: se interpreta como "este es el nuevo líder"
                // y se usa para actualizar su copia del documento.
                stub.becomeLeader(doc, clock);
            } catch (Exception e) {
                System.out.println("[Server " + myId + "] No pude notificar a " + info + " que soy el nuevo líder.");
            }
        }
    }
}
