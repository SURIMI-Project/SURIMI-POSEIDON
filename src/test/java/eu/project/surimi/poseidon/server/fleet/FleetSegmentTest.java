package eu.project.surimi.poseidon.server.fleet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FleetSegmentTest {

    @Test
    void allowsNullFieldsAndCoversMoreSpecificSegments() {
        final FleetSegment generalSegment = new FleetSegment(
            "OTB",
            null,
            "Industrial",
            "ESP",
            "POSEIDON"
        );
        final FleetSegment specificSegment = new FleetSegment(
            "OTB",
            "VL1824",
            "Industrial",
            "ESP",
            "POSEIDON"
        );

        assertThat(generalSegment.getVesselLengthClass()).isNull();
        assertThat(generalSegment.covers(specificSegment)).isTrue();
        assertThat(specificSegment.covers(generalSegment)).isFalse();
    }

    @Test
    void doesNotCoverSegmentsThatDisagreeOnSpecifiedFields() {
        final FleetSegment quotaSegment = new FleetSegment(
            "OTB",
            null,
            "Industrial",
            "ESP",
            "POSEIDON"
        );
        final FleetSegment otherCountrySegment = new FleetSegment(
            "OTB",
            "VL1824",
            "Industrial",
            "FRA",
            "POSEIDON"
        );

        assertThat(quotaSegment.covers(otherCountrySegment)).isFalse();
    }
}
