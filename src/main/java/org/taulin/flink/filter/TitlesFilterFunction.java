package org.taulin.flink.filter;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.flink.api.common.functions.FilterFunction;
import org.taulin.model.RecentChangeEvent;

import java.util.List;

public class TitlesFilterFunction implements FilterFunction<RecentChangeEvent> {
    private final List<String> filterTitleUrls;

    @Inject
    public TitlesFilterFunction(@Named("filter.title.urls") String filterTitleUrlsStr) {
        filterTitleUrls = List.of(filterTitleUrlsStr.split(","));
    }

    @Override
    public boolean filter(RecentChangeEvent event) {
        return filterTitleUrls.contains(event.getTitleUrl().toString());
    }
}
