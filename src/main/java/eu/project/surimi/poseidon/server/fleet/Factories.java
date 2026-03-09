package eu.project.surimi.poseidon.server.fleet;

import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.scopes.Scope;
import uk.ac.ox.poseidon.core.utils.NumericIntervalMapper;

public class Factories {

    private Factories() {
    }

    public static <S extends Scope> FleetSegmentMapperFactory<S> fleetSegmentMapper(
        final String countryCodeTag,
        final String vesselLengthTag,
        final Factory<? super S, ? extends NumericIntervalMapper<String>> vesselLengthClassMapper,
        final String scale,
        final String model
    ) {
        return new FleetSegmentMapperFactory<>(
            countryCodeTag,
            vesselLengthTag,
            vesselLengthClassMapper,
            scale,
            model
        );
    }
}
