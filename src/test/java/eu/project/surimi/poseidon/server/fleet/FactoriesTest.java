package eu.project.surimi.poseidon.server.fleet;

import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.core.scopes.Scope;
import uk.ac.ox.poseidon.core.utils.NumericIntervalToStringMapperFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.ac.ox.poseidon.core.scopes.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.core.utils.Factories.numericIntervalToStringMapper;
import static eu.project.surimi.poseidon.server.fleet.Factories.fleetSegmentMapper;

class FactoriesTest {

    @Test
    void fleetSegmentMapperBuildsFactoryWithProvidedConfiguration() {
        final var vesselLengthClassMapper =
            numericIntervalToStringMapper(
                NumericIntervalToStringMapperFactory.interval(12.0, 18.0, "VL1218")
            );
        final FleetSegmentMapperFactory<Scope> factory =
            fleetSegmentMapper(
                "country_of_registration",
                "loa",
                vesselLengthClassMapper,
                "Industrial",
                "POSEIDON"
            );

        assertThat(factory.getCountryCodeTag()).isEqualTo("country_of_registration");
        assertThat(factory.getVesselLengthTag()).isEqualTo("loa");
        assertThat(factory.getVesselLengthClassMapper()).isSameAs(vesselLengthClassMapper);
        assertThat(factory.getScale()).isEqualTo("Industrial");
        assertThat(factory.getModel()).isEqualTo("POSEIDON");
        assertThat(factory.get(GLOBAL_SCOPE)).isNotNull();
    }
}
