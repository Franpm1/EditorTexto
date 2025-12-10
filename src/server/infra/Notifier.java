package server.infra;

import common.IClientCallback;
import common.VectorClock;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


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
                               String initialDocument,
                               VectorClock initialClock) {
        clients.add(client);
        System.out.println("[Server " + myServerId + "] Cliente registrado. Total: " + clients.size());

        try {
            client.updateDocument(initialDocument, initialClock);
        } catch (RemoteException e) {
            System.err.println("[Server " + myServerId + "] Error enviando estado inicial al cliente: " + e);
            clients.remove(client);
        }
    }

    public void unregisterClient(IClientCallback client) {
        clients.remove(client);
        System.out.println("[Server " + myServerId + "] Cliente desregistrado. Total: " + clients.size());
    }

    /**
     * Envía el documento y vector clock actuales a todos los clientes.
     * Sólo se ejecuta si este servidor es LÍDER.
     */
    public void broadcast(String documentSnapshot, VectorClock clockSnapshot) {
        if (!serverState.isLeader()) return;

        synchronized (clients) {
            Iterator<IClientCallback> it = clients.iterator();
            while (it.hasNext()) {
                IClientCallback client = it.next();
                try {
                    client.updateDocument(documentSnapshot, clockSnapshot);
                } catch (RemoteException e) {
                    System.out.println("[Server " + myServerId + "] Cliente no responde, lo elimino.");
                    it.remove();
                }
            }
        }
    }
}
