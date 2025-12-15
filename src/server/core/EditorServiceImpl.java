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
        System.out.println("📢 DECLARACIÓN DE LÍDER RECIBIDA: Servidor " + leaderId);
        
        // 1. VERIFICACIÓN CRÍTICA: Si ya soy líder, ignorar cualquier otra declaración
        if (serverState.isLeader()) {
            System.out.println("⚠️  ¡YO soy el líder! Ignorando declaración de servidor " + leaderId);
            return; // No aceptar otro líder si ya soy líder
        }
        
        // 2. VERIFICACIÓN: Ignorar si el nuevo líder tiene ID MENOR que el actual
        // (en Bully, el líder debe ser el ID más alto disponible)
        int currentLeader = serverState.getCurrentLeaderId();
        if (currentLeader != -1 && leaderId < currentLeader) {
            System.out.println("⚠️  Ignorando líder con ID menor (" + leaderId + " < " + currentLeader + ")");
            return;
        }
        
        // 3. VERIFICACIÓN: Si es el mismo líder, solo registrar (evitar loops)
        if (currentLeader == leaderId) {
            System.out.println("ℹ️  Líder " + leaderId + " ya establecido.");
            return;
        }
        
        System.out.println("🔄 Actualizando líder de " + currentLeader + " a " + leaderId);
        
        // 4. Actualizar estado local SOLAMENTE
        serverState.setCurrentLeaderId(leaderId);
        serverState.setLeader(leaderId == serverState.getMyServerId());
        
        // 5. IMPORTANTE: NO PROPAGAR A OTROS SERVIDORES
        // Solo el líder original debe propagar, no los receptores
        // Esto evita ciclos de propagación infinita
        
        if (serverState.getMyServerId() == leaderId) {
            System.out.println("👑 ¡YO soy el nuevo líder! Sincronizando estado...");
            syncWithOtherServers();
        } else {
            System.out.println("✅ Reconozco a servidor " + leaderId + " como líder");
        }
    }

    // NUEVO: Método para sincronizar estado cuando tomo liderazgo
    private void syncWithOtherServers() {
        System.out.println("Sincronizando mi estado con otros servidores...");
        
        if (backupConnector instanceof ServerConnectorImpl) {
            ServerConnectorImpl connector = (ServerConnectorImpl) backupConnector;
            
            // Buscar el servidor con el vector clock más reciente
            String latestContent = document.getContent();
            VectorClock latestClock = document.getClockCopy();
            
            for (RemoteServerInfo info : connector.getAllServers()) {
                if (info.getServerId() == serverState.getMyServerId()) continue;
                
                try {
                    DocumentSnapshot snapshot = info.getStub().getCurrentState();
                    System.out.println("Estado del servidor " + info.getServerId() + ": " + snapshot.getClock());
                    
                    // Si este servidor tiene un estado más reciente usando comparador optimizado
                    if (VectorClockComparator.isClockNewer(snapshot.getClock(), latestClock)) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                        System.out.println("Servidor " + info.getServerId() + " tiene estado más reciente");
                    }
                } catch (Exception e) {
                    System.out.println("No se pudo obtener estado del servidor " + info.getServerId());
                }
            }
            
            // Actualizar mi estado con el más reciente
            document.overwriteState(latestContent, latestClock);
            System.out.println("Estado sincronizado: " + latestContent);
        }
    }

    @Override
    public void applyReplication(String doc, VectorClock clock) throws RemoteException {
        System.out.println("Replicacion recibida del líder");
        
        // 1. Aplicar el estado replicado
        document.overwriteState(doc, clock);
        
        // 2. Broadcast a mis clientes locales (IMPORTANTE: backups también notifican)
        notifier.broadcast(document.getContent(), document.getClockCopy());
    }

    // NUEVO: Implementar método para obtener estado actual
    @Override
    public DocumentSnapshot getCurrentState() throws RemoteException {
        return new DocumentSnapshot(document.getContent(), document.getClockCopy());
    }
}