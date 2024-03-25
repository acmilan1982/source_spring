 

package org.springframework.core.io.support;

import java.io.IOException;

import org.springframework.core.env.PropertySource;
import org.springframework.lang.Nullable;

/**
    The default implementation for PropertySourceFactory, wrapping every resource in a ResourcePropertySource.

    Since:
      4.3
    See Also:
      PropertySourceFactory, ResourcePropertySource
    Author:
      Juergen Hoeller
 */
public class DefaultPropertySourceFactory implements PropertySourceFactory {

	@Override
	public PropertySource<?> createPropertySource(@Nullable String name, 
                                                  EncodedResource resource) throws IOException {
		return (name != null ? new ResourcePropertySource(name, resource) : new ResourcePropertySource(resource));
	}

}
