# EwE models

The `ewe/` package (under `src/main/java/eu/project/surimi/poseidon/`) reads Ecopath with Ecosim
(EwE) models: `EweDatabase` and its implementations, for `.eiixml` in `eiixml/` and `.sqlite` in
`sqlite/`, and the factories that turn an Ecospace scenario into a model grid, bathymetry and ports,
and Ecopath groups into species (by default, the groups some fleet catches in Ecopath) with their
starting biomass, spread over the water cells as Ecospace spreads it.

- The loader fails loudly. It never fills in gaps, looks things up elsewhere (such as
  ASFIS), normalises inconsistent values or guesses how data should be read: the EwE modeller is
  responsible for complete and correct data, and anything missing or inconsistent is an error with
  a clear message. The aim is to load any EwE model, and a patch encodes one model's quirks. The
  NW Med R pipeline works around EwE gaps in this way; use it as a hint of what data exists, never
  as a specification. Where EwE has no field for something POSEIDON needs, such as life-stage
  labels, raise it as a design question rather than inventing a rule.
- The `ewe` package will move to core POSEIDON, where `inputs/` is not available. Its tests use
  synthetic tables (CSV strings) or small inline `.eiixml` files only. Checks that need the Anchovy
  Bay model go in `AnchovyBayScenarioTest`.
- The Anchovy Bay files in `inputs/anchovy_bay/` are preliminary and keep changing (its ports went
  from 3 to 60 per fleet in one revision). Never pin a count or a specific value of the model in a
  test, such as the number of ports or their cells. Test relations instead: what the scenario
  built equals what the EwE tables say, or two readers agree.
