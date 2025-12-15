# Editor Colaborativo Distribuido
Sistema de edición colaborativa en tiempo real implementado en **Java RMI** bajo una arquitectura **Primary-Backup** para alta disponibilidad. <br>
Incorpora el **Algoritmo de Bully** para la elección dinámica de líder y utiliza **Relojes Vectoriales** (Vector Clocks) para gestionar la causalidad y consistencia eventual entre réplicas.
# Esquemas
### 1 . Arquitectura Lógica y Flujo de funcionamiento
Ilustra el flujo de una operación iniciada en un nodo Backup. <br>
Podemos observar la redirección automática de la operación hacia el servidor Líder, la propagación interna del estado a las réplicas y la sincronización final (callback) a todos los clientes conectados para garantizar que todos vean el documento actualizado.
``` mermaid
---
config:
  look: classic
  layout: dagre
  theme: forest
---
flowchart TB
 subgraph Clientes["Capa de Clientes"]
        C1("Cliente A")
        C2("Cliente B")
        C3("Cliente C")
  end
 subgraph Cluster["Cluster de Servidores RMI"]
        S1("Servidor X: BACKUP")
        S2("Servidor Y: LÍDER")
        S3("Servidor Z: BACKUP")
  end
    C1 -- "1. Op: Insertar Texto" --> S1
    S1 -. "2. Redirigir Op al Líder" .-> S2
    S2 == "3. Replicar Estado" ==> S1 & S3
    S2 L_S2_C2_0@-- "4. Sync Cliente" --> C2
    S1 -- "4. Sync Cliente" --> C1
    S3 -- "4. Sync Cliente" --> C3

     C1:::client
     C2:::client
     C3:::client
     S1:::backup
     S2:::leader
     S3:::backup
    classDef client fill:#e1f5fe,stroke:#01579b,stroke-width:2px,color:black
    classDef leader fill:#fff9c4,stroke:#fbc02d,stroke-width:4px,color:black
    classDef backup fill:#f5f5f5,stroke:#9e9e9e,stroke-width:2px,stroke-dasharray: 5 5,color:black
    style Clientes fill:#ffffff,stroke:#333,stroke-width:2px,color:black
    style Cluster fill:#ffffff,stroke:#333,stroke-width:2px,color:black
    linkStyle 0 stroke:#0277bd,stroke-width:2px,fill:none
    linkStyle 1 stroke:#ef6c00,stroke-width:2px,stroke-dasharray: 5 5,fill:none
    linkStyle 2 stroke:#2e7d32,stroke-width:3px,fill:none
    linkStyle 3 stroke:#2e7d32,stroke-width:3px,fill:none
    linkStyle 4 stroke:#7b1fa2,stroke-width:2px,fill:none
    linkStyle 5 stroke:#7b1fa2,stroke-width:2px,fill:none
    linkStyle 6 stroke:#7b1fa2,stroke-width:2px,fill:none

    L_S2_C2_0@{ curve: natural }
```
### 2 . Lógica de elección del Líder y recuperación (Algoritmo de Bully)
Se verifica continuamente la disponibilidad del líder mediante heartbeats periódicos. Ante la detección de una caída, se activa el protocolo de elección que garantiza que el servidor con el ID más alto disponible asuma el control y notifique al resto de nodos.
``` mermaid
---
config:
  theme: forest
  look: classic
---
graph TD
    classDef start fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px,color:black;
    classDef decision fill:#fff9c4,stroke:#fbc02d,stroke-width:2px,color:black;
    classDef process fill:#e1f5fe,stroke:#01579b,stroke-width:2px,color:black;
    style AlgoritmoBully fill:#ffffff,stroke:#333,stroke-width:2px,color:black

    subgraph AlgoritmoBully [ ]
        direction TB
        
        Start((Inicio)):::start --> CheckLeader{¿Soy el Líder?}:::decision
        CheckLeader -- SÍ --> Sleep[Dormir Intervalo]:::process
        Sleep --> CheckLeader
        CheckLeader -- NO --> FindLeader[Buscar Info del Líder actual]:::process
        FindLeader --> PingLeader{¿Ping al Líder?}:::decision
        PingLeader -- Responde --> Sleep
        PingLeader -- Falla/Null --> Election[Inicia BullyElection]:::process
        
        Election --> SearchHigher[Buscar Nodos con ID > mi ID]:::process
        SearchHigher --> HigherExists{¿Responde alguno?}:::decision
        HigherExists -- SÍ --> Sleep
        HigherExists -- NO --> BecomeLeader[¡Me convierto en Líder!]:::process
        BecomeLeader --> Declare[Enviar 'declareLeader' a todos los menores]:::process
        Declare --> CheckLeader
    end
    linkStyle 2,12 stroke:#333,stroke-width:2px,stroke-dasharray: 5 5;
    linkStyle 0,1,3,4,5,6,7,8,9,10,11 stroke:#333,stroke-width:2px;
```
### 3. Protocolo de Comunicación y Replicación (Vista de Secuencia)
Diagrama de secuencia que detalla el flujo de mensajes RMI durante una operación de escritura iniciada en un nodo Backup. Para una correcta visualización usar un tema claro.
```mermaid
%%{init: {'theme': 'base', 'themeVariables': { 'mainBkg': '#ffffff', 'actorBkg': '#e1f5fe', 'actorBorder': '#01579b', 'signalColor': '#000000', 'signalTextColor': '#000000', 'noteBkgColor': '#fff9c4', 'noteBorderColor': '#fbc02d', 'activationBorderColor': '#666', 'activationBkgColor': '#f5f5f5', 'sequenceNumberColor': '#000000'}}}%%
sequenceDiagram
    autonumber
    participant ClientA as Cliente A
    participant ServerBackup as Servidor Backup
    participant ServerLeader as Servidor Líder
    participant ClientB as Cliente B

    Note over ClientA, ServerBackup: 1. Cliente envía operación al Backup
    ClientA->>ServerBackup: executeOperation(op)
    
    activate ServerBackup
    %% Lógica en EditorServiceImpl.java
    ServerBackup->>ServerBackup: isLeader() -> FALSE
    ServerBackup->>ServerBackup: findLeaderInfo()
    
    Note right of ServerBackup: 2. Redirección RMI (Sin aplicar localmente)
    ServerBackup->>ServerLeader: executeOperation(op)
    deactivate ServerBackup

    activate ServerLeader
    %% Lógica en EditorServiceImpl.java (Líder)
    ServerLeader->>ServerLeader: isLeader() -> TRUE
    
    Note right of ServerLeader: 3. Escritura Local (Document.java)
    ServerLeader->>ServerLeader: document.applyOperation(op)
    
    Note right of ServerLeader: 4. Notificar Clientes Locales
    %% Lógica en Notifier.java
    ServerLeader->>ClientB: syncState(fullDoc, clock)
    
    Note right of ServerLeader: 5. Replicación (ServerConnectorImpl)
    %% Hilo separado en ServerConnectorImpl.java
    ServerLeader-)ServerBackup: applyReplication(fullDoc, clock)
    deactivate ServerLeader

    activate ServerBackup
    Note left of ServerBackup: 6. Backup recibe nuevo estado
    %% Lógica en EditorServiceImpl.java (applyReplication)
    ServerBackup->>ServerBackup: document.overwriteState(doc, clock)
    
    Note left of ServerBackup: 7. Notificar Clientes del Backup
    ServerBackup->>ClientA: syncState(fullDoc, clock)
    deactivate ServerBackup
```
### 4. Topología de Red y Monitorización Concurrente
Representa el el flujo de datos principal (escritura y replicación RMI) y se destaca el mecanismo de Heartbeat, el cual se ejecuta paralela e independientemente. Esto permite que los nodos Backup verifiquen la disponibilidad del Líder de forma continua y concurrente sin bloquear las operaciones de edición en tiempo real.
```mermaid 
---
config:
  look: classic
  theme: forest
---
flowchart TB
 subgraph Machine1["LÍDER"]
        S0(("Servidor X<br>LÍDER"))
        C1["Cliente Local"]
  end
 subgraph Machine2["BACKUP"]
        S1(("Servidor Y<br>BACKUP"))
        C2["Cliente Remoto"]
  end
 subgraph Lienzo[" "]
    direction TB
        Machine1
        Machine2
  end
    C1 -- "1. RMI (Insert)" --> S0
    S0 -- "2. RMI (Sync)" --> C1
    S0 -- "3. RMI (Replicación)" --> S1
    S1 -- "4. RMI (Sync)" --> C2
    S1 -. RMI (Heartbeat) .-> S0

     S0:::leader
     C1:::client
     S1:::backup
     C2:::client
    classDef leader fill:#e1f5fe,stroke:#01579b,stroke-width:2px,color:black
    classDef backup fill:#fff3e0,stroke:#e65100,stroke-width:2px,color:black
    classDef client fill:#f5f5f5,stroke:#333,stroke-width:1px,color:black
    style S0 fill:#2962FF
    style S1 fill:#FF6D00
    style Machine1 fill:#e1f5fe,stroke:#01579b,color:black
    style Machine2 fill:#fff3e0,stroke:#e65100,color:black
    style Lienzo fill:#ffffff,stroke:#333,stroke-width:2px,color:black
    linkStyle 0 stroke:#2e7d32,stroke-width:2px,fill:none
    linkStyle 1 stroke:#2e7d32,stroke-width:2px,fill:none
    linkStyle 2 stroke:#2e7d32,stroke-width:2px,fill:none
    linkStyle 3 stroke:#2e7d32,stroke-width:2px,fill:none
    linkStyle 4 stroke:#d32f2f,stroke-width:2px,fill:none,stroke-dasharray: 5 5
```
