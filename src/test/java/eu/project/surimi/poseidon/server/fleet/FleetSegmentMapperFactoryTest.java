package eu.project.surimi.poseidon.server.fleet;

import org.junit.jupiter.api.Test;
import uk.ac.ox.poseidon.agents.vessels.Vessel;
import uk.ac.ox.poseidon.agents.vessels.gears.Gear;
import uk.ac.ox.poseidon.core.scopes.Scope;
import uk.ac.ox.poseidon.core.utils.NumericIntervalToStringMapperFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.ac.ox.poseidon.core.scopes.Scope.GLOBAL_SCOPE;
import static uk.ac.ox.poseidon.core.utils.Factories.numericIntervalToStringMapper;

class FleetSegmentMapperFactoryTest {

    @Test
    void buildsMapperFromConfiguredValues() {
        final FleetSegmentMapperFactory<Scope> factory = new FleetSegmentMapperFactory<>();
        factory.setCountryCodeTag("country_of_registration");
        factory.setVesselLengthTag("loa");
        factory.setScale("Industrial");
        factory.setModel("POSEIDON");
        factory.setVesselLengthClassMapper(
            numericIntervalToStringMapper(
                NumericIntervalToStringMapperFactory.interval(0.0, 12.0, "VL0612")
            )
        );

        final FleetSegmentMapper mapper = factory.get(GLOBAL_SCOPE);

        assertThat(mapper.apply(vessel("OTB", "country_of_registration", "ESP", "loa", 6.5)))
            .isEqualTo(new FleetSegment("OTB", "VL0612", "Industrial", "ESP", "POSEIDON"));
    }

    @Test
    void supportsAlternativeConfiguredValues() {
        final FleetSegmentMapperFactory<Scope> factory = new FleetSegmentMapperFactory<>();
        factory.setCountryCodeTag("flag_state");
        factory.setVesselLengthTag("lbp");
        factory.setScale("Artisanal");
        factory.setModel("ALT_MODEL");
        factory.setVesselLengthClassMapper(
            numericIntervalToStringMapper(
                NumericIntervalToStringMapperFactory.interval(12.0, 18.0, "VL1218")
            )
        );

        final FleetSegmentMapper mapper = factory.get(GLOBAL_SCOPE);

        assertThat(mapper.apply(vessel("PS", "flag_state", "FRA", "lbp", 13.0)))
            .isEqualTo(new FleetSegment("PS", "VL1218", "Artisanal", "FRA", "ALT_MODEL"));
    }

    private static Vessel vessel(
        final String gearCode,
        final String countryTagName,
        final String countryCode,
        final String vesselLengthTagName,
        final double vesselLength
    ) {
        final Vessel vessel = mock(Vessel.class);
        final Gear gear = mock(Gear.class);
        when(vessel.getGear()).thenReturn(gear);
        when(gear.getCode()).thenReturn(gearCode);
        when(vessel.getTag(countryTagName)).thenReturn(java.util.Optional.of(countryCode));
        when(vessel.getTag(vesselLengthTagName)).thenReturn(java.util.Optional.of(vesselLength));
        return vessel;
    }
}
