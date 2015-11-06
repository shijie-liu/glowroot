/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.ui;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.io.CharStreams;
import org.immutables.value.Value;

import org.glowroot.common.config.Versions;
import org.glowroot.common.live.LiveAggregateRepository;
import org.glowroot.common.util.ObjectMappers;
import org.glowroot.storage.repo.ConfigRepository;
import org.glowroot.storage.repo.ConfigRepository.RollupConfig;
import org.glowroot.storage.repo.TransactionTypeRepository;
import org.glowroot.storage.repo.config.UserInterfaceConfig;
import org.glowroot.storage.repo.config.UserInterfaceConfig.AnonymousAccess;

import static java.util.concurrent.TimeUnit.HOURS;

class LayoutService {

    private static final String SERVER_ID = "";

    private static final JsonFactory jsonFactory = new JsonFactory();
    private static final ObjectMapper mapper = ObjectMappers.create();

    private final boolean central;
    private final String version;
    private final ConfigRepository configRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final LiveAggregateRepository liveAggregateRepository;

    LayoutService(boolean central, String version, ConfigRepository configRepository,
            TransactionTypeRepository transactionTypeRepository,
            LiveAggregateRepository liveAggregateRepository) {
        this.central = central;
        this.version = version;
        this.configRepository = configRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.liveAggregateRepository = liveAggregateRepository;
    }

    String getLayout() throws Exception {
        Layout layout = buildLayout();
        return mapper.writeValueAsString(layout);
    }

    String getLayoutVersion() throws Exception {
        Layout layout = buildLayout();
        return layout.version();
    }

    String getNeedsAuthenticationLayout() throws Exception {
        UserInterfaceConfig userInterfaceConfig = configRepository.getUserInterfaceConfig();
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = jsonFactory.createGenerator(CharStreams.asWriter(sb));
        jg.writeStartObject();
        jg.writeBooleanField("needsAuthentication", true);
        jg.writeBooleanField("readOnlyPasswordEnabled",
                userInterfaceConfig.readOnlyPasswordEnabled());
        jg.writeStringField("footerMessage", "Glowroot version " + version);
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    private Layout buildLayout() throws Exception {
        List<Long> rollupExpirationMillis = Lists.newArrayList();
        for (long hours : configRepository.getStorageConfig().rollupExpirationHours()) {
            rollupExpirationMillis.add(HOURS.toMillis(hours));
        }
        UserInterfaceConfig userInterfaceConfig = configRepository.getUserInterfaceConfig();

        String defaultDisplayedTransactionType =
                userInterfaceConfig.defaultDisplayedTransactionType();

        // linked hash map to preserve ordering
        Map<String, ServerRollupLayout> serverRollups = Maps.newLinkedHashMap();
        SortedSetMultimap<String, String> transactionTypes =
                transactionTypeRepository.readTransactionTypes();
        if (!central) {
            transactionTypes.putAll(SERVER_ID,
                    liveAggregateRepository.getLiveTransactionTypes(SERVER_ID));
        }
        for (Entry<String, Collection<String>> entry : transactionTypes.asMap().entrySet()) {
            serverRollups.put(entry.getKey(), ImmutableServerRollupLayout.builder()
                    .addAllTransactionTypes(entry.getValue())
                    .build());
        }

        return ImmutableLayout.builder()
                .central(central)
                .footerMessage("Glowroot version " + version)
                .adminPasswordEnabled(userInterfaceConfig.adminPasswordEnabled())
                .readOnlyPasswordEnabled(userInterfaceConfig.readOnlyPasswordEnabled())
                .anonymousAccess(userInterfaceConfig.anonymousAccess())
                .addAllRollupConfigs(configRepository.getRollupConfigs())
                .addAllRollupExpirationMillis(rollupExpirationMillis)
                .gaugeCollectionIntervalMillis(configRepository.getGaugeCollectionIntervalMillis())
                .defaultTransactionType(defaultDisplayedTransactionType)
                .addAllDefaultPercentiles(userInterfaceConfig.defaultDisplayedPercentiles())
                .serverRollups(serverRollups)
                .build();
    }

    @Value.Immutable
    abstract static class Layout {

        abstract boolean central();
        abstract String footerMessage();
        abstract boolean adminPasswordEnabled();
        abstract boolean readOnlyPasswordEnabled();
        abstract AnonymousAccess anonymousAccess();
        abstract ImmutableList<RollupConfig> rollupConfigs();
        abstract ImmutableList<Long> rollupExpirationMillis();
        abstract long gaugeCollectionIntervalMillis();

        abstract String defaultTransactionType();
        abstract ImmutableList<Double> defaultPercentiles();
        abstract ImmutableMap<String, ServerRollupLayout> serverRollups();

        @Value.Derived
        public String version() {
            return Versions.getVersion(this);
        }
    }

    @Value.Immutable
    interface ServerRollupLayout {
        List<String> transactionTypes();
    }
}
