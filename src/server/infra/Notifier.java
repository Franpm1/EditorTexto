package server.infra;

import common.IClientCallback;
import common.VectorClock;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Notificador (Pareja C)
 * Envía el estado del documento y el VectorClock a los clientes.
 */
public class Notifier {

    private final List<IClientCallback> clients =
            Collections.synchronizedList(new ArrayList<>());

    private final int myServerId;
    private final ServerState serverState;

    public Notifier(int myServerId, ServerState serverState) {
        this.myServerId = myServerId;
        this.serverState = serverState;
    }

    public void registerClient(IClientCallback client,
                               String docSnapshot,
                               VectorClock clockSnapshot) {
        clients.add(client);
        System.out.println("[Server " + myServerId + "] Cliente registrado (" + clients.size() + ")");

        try {
            client.syncState(docSnapshot, clockSnapshot);
        } catch (RemoteException e) {
            System.err.println("[Server " + myServerId + "] Error inicial syncState: " + e);
            clients.remove(client);
        }
    }

    public void unregisterClient(IClientCallback client) {
        clients.remove(client);
    }

    /**
     * Envía el documento y el reloj a TODOS los clientes conectados.
     */
    public void broadcast(String documentSnapshot, VectorClock clockSnapshot) {
        if (!serverState.isLeader()) return;

        synchronized (clients) {
            Iterator<IClientCallback> it = clients.iterator();
            while (it.hasNext()) {
                IClientCallback client = it.next();
                try {
                    client.syncState(documentSnapshot, clockSnapshot);
                } catch (RemoteException e) {
                    System.out.println("[Server " + myServerId + "] Cliente no responde, eliminado");
                    it.remove();
                }
            }
        }
    }
}
