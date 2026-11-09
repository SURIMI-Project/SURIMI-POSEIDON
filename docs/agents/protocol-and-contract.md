# Protocol and contract

- Generated protocol classes come from the buf registry: the `build.buf.gen:surimi_surimi-protocol_grpc_java`
  dependency in `build.gradle.kts`, whose version ends with the protocol commit hash. Bumping the
  protocol means changing that version. The `.proto` sources live in the separate SURIMI-protocol
  repo (`../SURIMI-protocol` in the usual workspace layout).
- Each gRPC method has exactly one `RequestHandler` subclass, registered in `FisheryService`.
  Handlers that need a live simulation extend `WithSimulationRequestHandler` and receive its
  `SimulationManager.SimulationProperties`.
- `InitialiseSimulation` delivers the **contract**: the species, markets, price categories, fleet
  segments, and units the models in the ensemble exchange information about. `SimulationProperties`
  holds what POSEIDON keeps of it. Invariants every handler upholds:
  - Outputs only mention contract items. POSEIDON may model more; anything else is skipped.
  - Fleet segments: only those with `model` equal to `Server.MODEL_NAME` count; there must be at
    least one and no two may overlap. Blank segment fields are wildcards (`FleetSegment.covers` /
    `overlaps`). Each vessel is reported under the contract segment covering it
    (`SimulationProperties.findContractFleetSegment`, with the vessel's segment from the
    scenario's `FleetSegmentMapper` component); uncovered vessels are skipped.
  - Masses cross the wire in the contract's mass unit. Convert with
    `SimulationProperties.convertKgToStandardMassUnit` / `convertMassInStandardUnitToKg`; parse
    unit strings with POSEIDON's `Measurements`, which registers the si-units labels (`t`, `kn`, …)
    first.
- `Simulation.getComponent(Class)` only finds top-level scenario components (and members of
  collections among them), not objects nested inside other components. To reach something from a
  handler, register it as its own component. A factory builds its product once per owner of its
  level (the simulation, a vessel, a vessel component, or itself for a global product), so one
  factory object referenced both as a component and inside another component yields a single
  instance; two equal factory objects yield two.
- A vessel's attributes are the columns of its register row (`fleet_register.csv` for NW Med), and
  every action, catch and sale keeps the attributes the vessel had when it happened. Code that
  reports or judges something after the fact (the handlers, the TAC quotas, the closures, the
  landings accumulator) reads those recorded attributes, through the attribute readers
  (`stringAttribute("main_fishing_gear")`), not the vessel as it is now.
