package server.core;

import common.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import server.infra.*;

public class EditorServiceImpl extends UnicastRemoteObject implements IEditorService {

    private final Document document;
    private final Notifier notifier;
    private IServerConnector backupConnector;
    private final ServerState serverState;

    public EditorServiceImpl(Document doc, Notifier notifier, ServerState state) throws RemoteException {
        super();
        this.document = doc;
        this.notifier = notifier;
        this.serverState = state;
    }
    
    public void setBackupConnector(IServerConnector sc) { 
        this.backupConnector = sc; 
    }

    @Override
    public void executeOperation(Operation op) throws RemoteException {
        System.out.println("Operacion recibida de cliente: " + op.getType() + " de " + op.getOwner());
        
        // SI SOY LÍDER: procesar y replicar
        if (serverState.isLeader()) {
            System.out.println("Soy lider, procesando operacion...");
            
            // 1. Aplicar localmente
            document.applyOperation(op);
            
            // 2. Broadcast solo a mis clientes locales (el líder)
            notifier.broadcast(document.getContent(), document.getClockCopy());
            
            // 3. Replicar a TODOS los backups
            if (backupConnector != null) {
                backupConnector.propagateToBackups(document.getContent(), document.getClockCopy());
            }
        } 
        // SI NO SOY LÍDER: redirigir al líder SIN aplicar localmente
        else {
            System.out.println("No soy lider, redirigiendo operacion al líder...");
            
            // Buscar el líder
            RemoteServerInfo leaderInfo = findLeaderInfo();
            
            if (leaderInfo != null) {
                try {
                    // SOLO redirigir, NO aplicar localmente
                    leaderInfo.getStub().executeOperation(op);
                    System.out.println("Operacion redirigida al líder " + serverState.getCurrentLeaderId());
                } catch (Exception e) {
                    System.out.println("Error redirigiendo al líder: " + e.getMessage());
                    // Fallback: aplicar localmente si el líder no responde
                    document.applyOperation(op);
                    notifier.broadcast(document.getContent(), document.getClockCopy());
                }
            } else {
                System.out.println("No se encontro al líder, aplicando localmente");
                document.applyOperation(op);
                notifier.broadcast(document.getContent(), document.getClockCopy());
            }
        }
    }

    private RemoteServerInfo findLeaderInfo() {
        if (backupConnector instanceof ServerConnectorImpl) {
            ServerConnectorImpl connector = (ServerConnectorImpl) backupConnector;
            for (RemoteServerInfo info : connector.getAllServers()) {
                if (info.getServerId() == serverState.getCurrentLeaderId()) {
                    return info;
                }
            }
        }
        return null;
    }

    @Override
    public void registerClient(IClientCallback client, String username) throws RemoteException {
        System.out.println("Registrando cliente: " + username);
        notifier.registerClient(client);
        
        // Enviar estado actual inmediatamente
        client.syncState(document.getContent(), document.getClockCopy());
    }

    @Override
    public void heartbeat() throws RemoteException {
        // Simple respuesta de que estoy vivo
    }

    @Override
    public void becomeLeader(String doc, VectorClock clock) throws RemoteException {
        System.out.println("Recibiendo traspaso de liderazgo...");
        document.overwriteState(doc, clock);
        serverState.setLeader(true);
        serverState.setCurrentLeaderId(serverState.getMyServerId());
        System.out.println("Ahora soy el líder.");
    }

    @Override
    public void declareLeader(int leaderId) throws RemoteException {
        System.out.println("Servidor " + leaderId + " se ha declarado LIDER.");
        serverState.setCurrentLeaderId(leaderId);
        serverState.setLeader(leaderId == serverState.getMyServerId());
    }

    @Override
    public void applyReplication(String doc, VectorClock clock) throws RemoteException {
        System.out.println("Replicacion recibida del líder");
        
        // 1. Aplicar el estado replicado
        document.overwriteState(doc, clock);
        
        // 2. Broadcast a mis clientes locales (IMPORTANTE: backups también notifican)
        notifier.broadcast(document.getContent(), document.getClockCopy());
    }

    @Override
    public DocumentSnapshot getCurrentState() throws RemoteException {
        return new DocumentSnapshot(document.getContent(), document.getClockCopy());
    }
}