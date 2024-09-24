package org.taulin.factory;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.functions.FilterFunction;
import org.taulin.component.EventFilterRunner;
import org.taulin.component.impl.EventFilterRunnerImpl;
import org.taulin.exception.ConfigurationException;
import org.taulin.flink.filter.TitlesFilterFunction;
import org.taulin.model.RecentChangeEvent;
import org.taulin.util.ResourceLoaderUtil;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

@Slf4j
public class FilterModule extends AbstractModule {
    @Override
    protected void configure() {
        Names.bindProperties(binder(), loadApplicationProperties());
        bind(EventFilterRunner.class).to(EventFilterRunnerImpl.class);
        bind(new TypeLiteral<FilterFunction<RecentChangeEvent>>() {}).to(TitlesFilterFunction.class);
    }

    private Properties loadApplicationProperties() {
        try {
            Properties properties = new Properties();
            properties.load(new FileReader(ResourceLoaderUtil.loadResource("application.properties")));
            return properties;
        } catch (IOException ex) {
            log.error("Unable to load application.properties.");
            throw new ConfigurationException("Unable to load application properties configuration", ex);
        }
    }
}
