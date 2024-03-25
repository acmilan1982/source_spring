
package org.springframework.core.type.filter;

import java.io.IOException;

import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
    Base interface for type filters using a MetadataReader.
    Since:
      2.5
    Author:
      Costin Leau, Juergen Hoeller, Mark Fisher
 */
@FunctionalInterface
public interface TypeFilter {

	/**
        Determine whether this filter matches for the class described by the given metadata.
        Params:
          metadataReader – the metadata reader for the target class 
          metadataReaderFactory – a factory for obtaining metadata readers for other classes (such as superclasses and interfaces)
        Returns:
          whether this filter matches
        Throws:
          IOException – in case of I/O failure when reading metadata
	 */
	boolean match(MetadataReader metadataReader, 
                  MetadataReaderFactory metadataReaderFactory)
			throws IOException;

}
