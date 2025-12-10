# Editor Distribuido Cooperativo (RMI y Consistencia Causal)
Este proyecto implementa un editor de texto cooperativo con múltiples servidores, utilizando RMI (Invocación a Métodos Remotos) para la comunicación, el protocolo Bully para la elección de líder y Relojes Vectoriales para garantizar la Consistencia Causal entre clientes.

```
Pareja A (Cliente)
  "Interfaz de Usuario, Resiliencia del Cliente, Causalidad Local."
  "RMI Callback, Tolerancia a Fallos (Reconexión)."

Pareja B (Server Core)
  "Consistencia Causal, Exclusión Mutua Local, Arranque del Servicio."
  "Relojes Vectoriales, ReentrantReadWriteLock, Máquina de Estado Replicada."

Pareja C (Infraestructura)
  "Tolerancia a Fallos, Elección de Líder, Difusión Confiable."
  "Algoritmo Bully, Heartbeat, Gestión de Conexiones (Notifier)."
```

