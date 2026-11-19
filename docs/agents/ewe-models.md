# EwE models

The `ewe/` package (under `src/main/java/eu/project/surimi/poseidon/`) reads
Ecopath with Ecosim (EwE) models: `EweDatabase` and its implementations, for
`.eiixml` in `eiixml/` and `.sqlite` in `sqlite/`, and the factories that turn
an Ecospace scenario into a model grid, bathymetry and ports, and Ecopath groups
into species (by default, the groups some fleet catches in Ecopath) with their
starting biomass, spread over the water cells as Ecospace spreads it.

- The loader fails loudly. It never fills in gaps, looks things up elsewhere
  (such as ASFIS), normalises inconsistent values or guesses how data should be
  read: the EwE modeller is responsible for complete and correct data, and
  anything missing or inconsistent is an error with a clear message. The aim is
  to load any EwE model, and a patch encodes one model's quirks. The NW Med R
  pipeline works around EwE gaps in this way; use it as a hint of what data
  exists, never as a specification. Where EwE has no field for something
  POSEIDON needs, such as life-stage labels, raise it as a design question
  rather than inventing a rule.
- The `ewe` package serves standalone use only: loading any EwE model on its
  own, outside SURIMI, with no contract, taking everything it can from the model
  and leaving the biology to EwE. It never reads or knows about the
  pre-processing pipeline's files (such as `species.csv` or `ports.csv`) or the
  contract.
- SURIMI scenarios never read an EwE model, public or not: the pre-processing
  pipeline extracts what they need into `inputs/`, and EwE's biomass arrives at
  runtime through the controller. `AnchovyBayScenario` still reads the model
  through the `ewe` package for now. It is to become a pipeline-fed scenario
  like NW Med's, and its model loading a generic scenario of its own.
- The `ewe` package will move to core POSEIDON, where `inputs/` is not
  available. Its tests use synthetic tables (CSV strings) or small inline
  `.eiixml` files only. Checks that need the Anchovy Bay model go in
  `AnchovyBayScenarioTest`.
- The Anchovy Bay files in `inputs/anchovy_bay/` are preliminary and keep
  changing (its ports went from 3 to 60 per fleet in one revision). Never pin a
  count or a specific value of the model in a test, such as the number of ports
  or their cells. Test relations instead: what the scenario built equals what
  the EwE tables say, or two readers agree.
