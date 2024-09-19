package org.taulin.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

@UtilityClass
public class ResourceLoaderUtil {
    private static final String FILE_SUFFIX_SEPARATOR = ".";

    public static File loadResource(String resourceName) {
        return inputStreamToFile(resourceName, loadResourceAsInputStream(resourceName));
    }

    public static InputStream loadResourceAsInputStream(String resourceName) {
        final InputStream resourceInputStream = ClassLoader.getSystemResourceAsStream(resourceName);
        if (Objects.isNull(resourceInputStream)) {
            throw new IllegalArgumentException("Invalid resource: " + resourceName);
        }

        return resourceInputStream;
    }

    public static File inputStreamToFile(String resourceName, InputStream inputStream) {
        final Map.Entry<String, String> prefixSuffixTuple = getPrefixAndSuffixFromResource(resourceName);
        final File file = createTempFile(prefixSuffixTuple.getKey(), prefixSuffixTuple.getValue());
        try (FileOutputStream out = new FileOutputStream(file)) {
            IOUtils.copy(inputStream, out);
        } catch (IOException e) {
            throw new RuntimeException("Error converting input stream to file", e);
        }
        return file;
    }

    public static File createTempFile(String prefix, String suffix) {
        try {
            final File tempFile = File.createTempFile(prefix, suffix);
            tempFile.deleteOnExit();
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Error creating temp file %s%s".formatted(prefix, suffix), e);
        }
    }

    public static Map.Entry<String, String> getPrefixAndSuffixFromResource(String resourceName) {
        final String prefix = resourceName.substring(0, resourceName.indexOf(FILE_SUFFIX_SEPARATOR));
        final String suffix = resourceName.substring(resourceName.indexOf(FILE_SUFFIX_SEPARATOR));
        return Map.entry(prefix, suffix);
    }
}
