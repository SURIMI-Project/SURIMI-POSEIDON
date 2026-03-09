package eu.project.surimi.poseidon.server.fleet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.ac.ox.poseidon.core.Factory;
import uk.ac.ox.poseidon.core.RelativeScopeFactory;
import uk.ac.ox.poseidon.core.scopes.Scope;
import uk.ac.ox.poseidon.core.utils.NumericIntervalMapper;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Factory for {@link FleetSegmentMapper}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FleetSegmentMapperFactory<S extends Scope> extends RelativeScopeFactory<S, FleetSegmentMapper> {

    private String countryCodeTag;
    private String vesselLengthTag;
    private Factory<? super S, ? extends NumericIntervalMapper<String>> vesselLengthClassMapper;
    private String scale;
    private String model;

    @Override
    protected FleetSegmentMapper newInstance(final S scope) {
        return new FleetSegmentMapper(
            checkNotNull(countryCodeTag),
            checkNotNull(vesselLengthTag),
            checkNotNull(vesselLengthClassMapper).get(scope),
            scale,
            model
        );
    }
}
