

package org.springframework.core.io.support;

import java.io.IOException;

import org.springframework.core.env.PropertySource;
import org.springframework.lang.Nullable;

/**
    Strategy interface for creating resource-based PropertySource wrappers.

    Since:
      4.3
    See Also:
      DefaultPropertySourceFactory
    Author:
      Juergen Hoeller
 */
public interface PropertySourceFactory {

	/**
        Create a PropertySource that wraps the given resource.
        Params:
        name – the name of the property source (can be null in which case the factory implementation will have to generate a name based on the given resource) resource – the resource (potentially encoded) to wrap
        Returns:
        the new PropertySource (never null)
        Throws:
        IOException – if resource resolution failed
	 */
	PropertySource<?> createPropertySource(@Nullable String name, EncodedResource resource) throws IOException;

}
