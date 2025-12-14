package server.infra;

import common.IClientCallback;
import common.VectorClock;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import server.core.ServerMain;

public class Notifier {
    private final List<IClientCallback> clients = Collections.synchronizedList(new ArrayList<>());
    private final ServerState serverState;

    public Notifier(ServerState serverState) {
        this.serverState = serverState;
    }

    public void registerClient(IClientCallback client) {
        clients.add(client);
        System.out.println("Cliente registrado. Total: " + clients.size());
    }

    public void broadcast(String documentSnapshot, VectorClock clockSnapshot) {
        // Broadcast PARALELO a todos los clientes
        if (clients.isEmpty()) return;
        
        System.out.println("Broadcast PARALELO a " + clients.size() + " clientes");
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        synchronized (clients) {
            var iterator = clients.iterator();
            while (iterator.hasNext()) {
                IClientCallback client = iterator.next();
                
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        client.syncState(documentSnapshot, clockSnapshot);
                    } catch (RemoteException e) {
                        // Cliente desconectado - remover en segundo plano
                        synchronized (clients) {
                            clients.remove(client);
                        }
                    }
                }, ServerMain.GLOBAL_EXECUTOR));
            }
        }
        
        // Continuar SIN ESPERAR a que terminen los broadcasts
        // Los completable futures se ejecutan en background
    }
}