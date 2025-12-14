package server.infra;

import common.IEditorService;
import common.VectorClockComparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class BullyElection {
    private final ServerState state;
    private final List<RemoteServerInfo> allServers;
    private final IEditorService myServiceStub;

    public BullyElection(ServerState state, List<RemoteServerInfo> allServers, IEditorService myServiceStub) {
        this.state = state;
        this.allServers = allServers;
        this.myServiceStub = myServiceStub;
    }

    public void startElectionOnStartup() {
        System.out.println("🔍 Iniciando elección al arrancar (ID: " + state.getMyServerId() + ")...");
        
        // Esperar para que todos los servidores estén listos
        try {
            Thread.sleep(1500); // Más tiempo para que todos arranquen
        } catch (InterruptedException e) {}
        
        int myId = state.getMyServerId();
        
        // PASO 1: Intentar descubrir quién es el líder actual
        System.out.println("Buscando líder existente en la red...");
        Integer currentLeaderId = discoverCurrentLeader();
        
        if (currentLeaderId != null) {
            // Se encontró un líder
            System.out.println("✅ Líder encontrado: Servidor " + currentLeaderId);
            state.setCurrentLeaderId(currentLeaderId);
            
            if (currentLeaderId == myId) {
                // ¡Sorpresa! Yo soy el líder según los demás
                state.setLeader(true);
                System.out.println("👑 Otros servidores me reconocen como líder");
            }
            return; // Ya tenemos líder, salir
        }
        
        // PASO 2: No se encontró líder, iniciar elección Bully
        System.out.println("❌ No se encontró líder, iniciando elección Bully...");
        boolean foundHigher = false;
        
        ExecutorService quickPool = Executors.newCachedThreadPool();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                Future<Boolean> future = quickPool.submit(() -> {
                    try {
                        info.getStub().heartbeat();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                
                try {
                    if (future.get(2000, TimeUnit.MILLISECONDS)) {
                        foundHigher = true;
                        System.out.println("✓ Servidor " + info.getServerId() + " responde (ID mayor)");
                        
                        // Preguntar a este servidor quién es el líder
                        try {
                            // Intentar obtener su estado para inferir
                            common.DocumentSnapshot snapshot = info.getStub().getCurrentState();
                            System.out.println("Servidor " + info.getServerId() + " tiene estado, podría ser líder");
                            // Si responde y tiene estado, asumimos que podría ser líder
                            state.setCurrentLeaderId(info.getServerId());
                        } catch (Exception e) {
                            // Si no puede dar estado, tal vez no sea líder
                            state.setCurrentLeaderId(info.getServerId());
                        }
                        break;
                    }
                } catch (TimeoutException e) {
                    System.out.println("✗ Servidor " + info.getServerId() + " timeout");
                    future.cancel(true);
                } catch (Exception e) {
                    System.out.println("✗ Servidor " + info.getServerId() + " error: " + e.getMessage());
                }
            }
        }
        
        quickPool.shutdownNow();

        if (!foundHigher) {
            System.out.println("✅ Soy el servidor con ID más alto activo. Sincronizando...");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        } else {
            System.out.println("⏳ Líder provisional: " + state.getCurrentLeaderId());
            // Verificar si el líder provisional realmente funciona
            verifyLeader();
        }
    }

    // NUEVO MÉTODO: Descubrir quién es el líder actual
    private Integer discoverCurrentLeader() {
        if (allServers.size() <= 1) return null;
        
        ExecutorService pool = Executors.newCachedThreadPool();
        List<Future<Integer>> futures = new java.util.ArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            futures.add(pool.submit(() -> {
                try {
                    // Intentar preguntar quién es el líder
                    // Método 1: Ver si responde como líder (heartbeat rápido)
                    long startTime = System.currentTimeMillis();
                    info.getStub().heartbeat();
                    long responseTime = System.currentTimeMillis() - startTime;
                    
                    // Si responde muy rápido, podría ser líder
                    if (responseTime < 300) {
                        // Verificar si tiene estado consistente
                        try {
                            common.DocumentSnapshot snapshot = info.getStub().getCurrentState();
                            System.out.println("Servidor " + info.getServerId() + " responde rápido y tiene estado");
                            return info.getServerId(); // Posible líder
                        } catch (Exception e) {
                            // No puede dar estado
                            return null;
                        }
                    }
                    return null;
                } catch (Exception e) {
                    return null;
                }
            }));
        }
        
        // Buscar consenso sobre quién es el líder
        Map<Integer, Integer> leaderVotes = new HashMap<>();
        int validResponses = 0;
        
        for (Future<Integer> future : futures) {
            try {
                Integer possibleLeader = future.get(1500, TimeUnit.MILLISECONDS);
                if (possibleLeader != null) {
                    leaderVotes.put(possibleLeader, leaderVotes.getOrDefault(possibleLeader, 0) + 1);
                    validResponses++;
                    System.out.println("Voto para líder " + possibleLeader);
                }
            } catch (Exception e) {
                // Ignorar timeouts
            }
        }
        
        pool.shutdownNow();
        
        // Necesitamos al menos 2 respuestas para tener consenso
        if (validResponses < 2 || leaderVotes.isEmpty()) {
            System.out.println("No hay consenso sobre el líder (respuestas: " + validResponses + ")");
            return null;
        }
        
        // Encontrar el líder con más votos
        Integer electedLeader = null;
        int maxVotes = 0;
        
        for (Map.Entry<Integer, Integer> entry : leaderVotes.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                electedLeader = entry.getKey();
            }
        }
        
        // Verificar que el líder electo tenga mayoría
        if (maxVotes >= (validResponses / 2) + 1) {
            System.out.println("✅ Consenso: Líder es " + electedLeader + " (" + maxVotes + "/" + validResponses + " votos)");
            return electedLeader;
        } else {
            System.out.println("❌ Sin consenso claro para líder");
            return null;
        }
    }

    // NUEVO MÉTODO: Verificar si el líder provisional realmente funciona
    private void verifyLeader() {
        int leaderId = state.getCurrentLeaderId();
        if (leaderId == -1 || leaderId == state.getMyServerId()) return;
        
        System.out.println("Verificando líder " + leaderId + "...");
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == leaderId) {
                try {
                    // Intentar una operación simple
                    info.getStub().heartbeat();
                    System.out.println("✓ Líder " + leaderId + " verificado y responde");
                    return;
                } catch (Exception e) {
                    System.out.println("✗ Líder " + leaderId + " no responde, reiniciando elección");
                    state.setCurrentLeaderId(-1);
                    onLeaderDown();
                    return;
                }
            }
        }
    }

    public void onLeaderDown() {
        if (state.isLeader()) {
            return; // Si ya soy líder, no hacer nada
        }
        
        System.out.println("⚡ Posible caída del líder, iniciando verificación...");
        
        // Primero verificar si el líder realmente cayó
        int currentLeader = state.getCurrentLeaderId();
        if (currentLeader != -1) {
            for (RemoteServerInfo info : allServers) {
                if (info.getServerId() == currentLeader) {
                    try {
                        // Última verificación
                        info.getStub().heartbeat();
                        System.out.println("✓ El líder " + currentLeader + " SÍ responde");
                        return; // El líder sigue vivo
                    } catch (Exception e) {
                        System.out.println("✗ Confirmado: líder " + currentLeader + " NO responde");
                        break;
                    }
                }
            }
        }
        
        // Líder confirmado caído
        state.setCurrentLeaderId(-1);
        System.out.println("🔄 Iniciando nueva elección...");
        
        boolean foundHigher = false;
        int myId = state.getMyServerId();
        
        ExecutorService quickPool = Executors.newCachedThreadPool();

        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() > myId) {
                Future<Boolean> future = quickPool.submit(() -> {
                    try {
                        info.getStub().heartbeat();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                
                try {
                    if (future.get(1500, TimeUnit.MILLISECONDS)) {
                        foundHigher = true;
                        System.out.println("✓ Servidor " + info.getServerId() + " responde (ID mayor)");
                        
                        // Preguntar si él es líder o conoce al líder
                        try {
                            common.DocumentSnapshot snapshot = info.getStub().getCurrentState();
                            System.out.println("Servidor " + info.getServerId() + " podría ser el nuevo líder");
                            state.setCurrentLeaderId(info.getServerId());
                            break;
                        } catch (Exception e) {
                            state.setCurrentLeaderId(info.getServerId());
                            break;
                        }
                    }
                } catch (Exception e) {
                    future.cancel(true);
                }
            }
        }
        
        quickPool.shutdownNow();

        if (!foundHigher) {
            System.out.println("🔄 No hay servidores con ID mayor activos. Sincronizando...");
            syncStateBeforeBecomingLeader();
            becomeLeaderNow();
        } else {
            System.out.println("⏳ Nuevo líder establecido: " + state.getCurrentLeaderId());
            // Informar a otros sobre el nuevo líder
            propagateNewLeader(state.getCurrentLeaderId());
        }
    }

    private void syncStateBeforeBecomingLeader() {
        System.out.println("🔄 Sincronizando estado antes de ser líder...");
        
        String latestContent = "";
        common.VectorClock latestClock = null;
        boolean gotState = false;
        
        ExecutorService syncPool = Executors.newCachedThreadPool();
        List<Future<common.DocumentSnapshot>> futures = new java.util.ArrayList<>();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            futures.add(syncPool.submit(() -> {
                try {
                    return info.getStub().getCurrentState();
                } catch (Exception e) {
                    return null;
                }
            }));
        }
        
        for (int i = 0; i < futures.size(); i++) {
            try {
                common.DocumentSnapshot snapshot = futures.get(i).get(2000, TimeUnit.MILLISECONDS);
                if (snapshot != null) {
                    System.out.println("Estado obtenido de un servidor: " + snapshot.getClock());
                    
                    if (!gotState) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                        gotState = true;
                    } else if (VectorClockComparator.isClockNewer(snapshot.getClock(), latestClock)) {
                        latestContent = snapshot.getContent();
                        latestClock = snapshot.getClock();
                        System.out.println("Encontrado estado más reciente");
                    }
                }
            } catch (Exception e) {
                System.out.println("Timeout obteniendo estado");
            }
        }
        
        syncPool.shutdownNow();
        
        if (gotState) {
            try {
                myServiceStub.becomeLeader(latestContent, latestClock);
                System.out.println("✅ Estado sincronizado correctamente");
            } catch (Exception e) {
                System.out.println("⚠️ Error al sincronizar: " + e.getMessage());
            }
        } else {
            System.out.println("⚠️ No se pudo obtener estado de otros servidores");
        }
    }

    private void becomeLeaderNow() {
        System.out.println("👑 DECLARÁNDOME LÍDER (ID " + state.getMyServerId() + ")");
        
        state.setLeader(true);
        state.setCurrentLeaderId(state.getMyServerId());
        
        // Informar a TODOS los servidores
        ExecutorService notifyPool = Executors.newCachedThreadPool();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId()) continue;
            
            notifyPool.execute(() -> {
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        info.getStub().declareLeader(state.getMyServerId());
                        System.out.println("✓ Servidor " + info.getServerId() + " notificado");
                        break;
                    } catch (Exception e) {
                        if (attempt == 2) {
                            System.out.println("✗ No se pudo notificar a servidor " + info.getServerId());
                        }
                        try { Thread.sleep(500); } catch (InterruptedException ie) {}
                    }
                }
            });
        }
        
        notifyPool.shutdown();
        try {
            notifyPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {}
        
        System.out.println("✅ Liderazgo establecido");
    }

    // NUEVO MÉTODO: Propagar nuevo líder a otros servidores
    private void propagateNewLeader(int leaderId) {
        if (leaderId == state.getMyServerId()) return;
        
        ExecutorService pool = Executors.newCachedThreadPool();
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == state.getMyServerId() || info.getServerId() == leaderId) continue;
            
            pool.execute(() -> {
                try {
                    info.getStub().declareLeader(leaderId);
                    System.out.println("Propagado líder " + leaderId + " a servidor " + info.getServerId());
                } catch (Exception e) {
                    // Silencioso
                }
            });
        }
        
        pool.shutdown();
    }

    public RemoteServerInfo getCurrentLeaderInfo() {
        int leaderId = state.getCurrentLeaderId();
        if (leaderId == -1) return null;
        
        for (RemoteServerInfo info : allServers) {
            if (info.getServerId() == leaderId) {
                return info;
            }
        }
        return null;
    }
}