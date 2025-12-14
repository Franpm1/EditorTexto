package server.infra;

import common.IClientCallback;
import common.VectorClock;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Notifier {
    private final List<IClientCallback> clients = Collections.synchronizedList(new ArrayList<>());
    private final ServerState serverState;
    private final ExecutorService notifyPool;

    public Notifier(ServerState serverState) {
        this.serverState = serverState;
        this.notifyPool = Executors.newCachedThreadPool();
    }

    public void registerClient(IClientCallback client) {
        clients.add(client);
    }

    public void broadcast(String documentSnapshot, VectorClock clockSnapshot) {
        synchronized (clients) {
            var iterator = clients.iterator();
            while (iterator.hasNext()) {
                IClientCallback client = iterator.next();
                notifyPool.execute(() -> {
                    try {
                        client.syncState(documentSnapshot, clockSnapshot);
                    } catch (RemoteException e) {
                        synchronized (clients) {
                            iterator.remove();
                        }
                    }
                });
            }
        }
    }
}