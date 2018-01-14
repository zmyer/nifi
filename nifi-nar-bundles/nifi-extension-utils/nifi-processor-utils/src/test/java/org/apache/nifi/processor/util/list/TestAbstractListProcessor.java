/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.processor.util.list;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.state.Scope;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.distributed.cache.client.Deserializer;
import org.apache.nifi.distributed.cache.client.DistributedMapCacheClient;
import org.apache.nifi.distributed.cache.client.Serializer;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.state.MockStateManager;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TestAbstractListProcessor {

    /**
     * @return current timestamp in milliseconds, but truncated at specified target precision (e.g. SECONDS or MINUTES).
     */
    private static long getCurrentTimestampMillis(final TimeUnit targetPrecision) {
        final long timestampInTargetPrecision = targetPrecision.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        return TimeUnit.MILLISECONDS.convert(timestampInTargetPrecision, targetPrecision);
    }

    private static long getSleepMillis(final TimeUnit targetPrecision) {
        return AbstractListProcessor.LISTING_LAG_MILLIS.get(targetPrecision) * 2;
    }

    private static final long DEFAULT_SLEEP_MILLIS = getSleepMillis(TimeUnit.MILLISECONDS);

    private ConcreteListProcessor proc;
    private TestRunner runner;

    @Rule
    public TestWatcher dumpState = new ListProcessorTestWatcher(
            () -> {
                try {
                    return runner.getStateManager().getState(Scope.LOCAL).toMap();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to retrieve state", e);
                }
            },
            () -> proc.entities,
            () -> runner.getFlowFilesForRelationship(AbstractListProcessor.REL_SUCCESS).stream().map(m -> (FlowFile) m).collect(Collectors.toList())
    );

    @Before
    public void setup() {
        proc = new ConcreteListProcessor();
        runner = TestRunners.newTestRunner(proc);
    }

    @Rule
    public final TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void testStateMigratedFromCacheService() throws InitializationException {

        final DistributedCache cache = new DistributedCache();
        runner.addControllerService("cache", cache);
        runner.enableControllerService(cache);
        runner.setProperty(AbstractListProcessor.DISTRIBUTED_CACHE_SERVICE, "cache");

        final String serviceState = "{\"latestTimestamp\":1492,\"matchingIdentifiers\":[\"id\"]}";
        final String cacheKey = runner.getProcessor().getIdentifier() + ".lastListingTime./path";
        cache.stored.put(cacheKey, serviceState);

        runner.run();

        final MockStateManager stateManager = runner.getStateManager();
        final Map<String, String> expectedState = new HashMap<>();
        // Ensure only timestamp is migrated
        expectedState.put(AbstractListProcessor.LATEST_LISTED_ENTRY_TIMESTAMP_KEY, "1492");
        expectedState.put(AbstractListProcessor.LAST_PROCESSED_LATEST_ENTRY_TIMESTAMP_KEY, "1492");
        stateManager.assertStateEquals(expectedState, Scope.CLUSTER);
    }

    @Test
    public void testNoStateToMigrate() throws Exception {

        runner.run();

        final MockStateManager stateManager = runner.getStateManager();
        final Map<String, String> expectedState = new HashMap<>();
        stateManager.assertStateEquals(expectedState, Scope.CLUSTER);
    }

    @Test
    public void testStateMigratedFromLocalFile() throws Exception {

        // Create a file that we will populate with the desired state
        File persistenceFile = testFolder.newFile(proc.persistenceFilename);
        // Override the processor's internal persistence file
        proc.persistenceFile = persistenceFile;

        // Local File persistence was a properties file format of <key>=<JSON entity listing representation>
        // Our ConcreteListProcessor is centered around files which are provided for a given path
        final String serviceState = proc.getPath(runner.getProcessContext()) + "={\"latestTimestamp\":1492,\"matchingIdentifiers\":[\"id\"]}";

        // Create a persistence file of the format anticipated
        try (FileOutputStream fos = new FileOutputStream(persistenceFile);) {
            fos.write(serviceState.getBytes(StandardCharsets.UTF_8));
        }

        runner.run();

        // Verify the local persistence file is removed
        Assert.assertTrue("Failed to remove persistence file", !persistenceFile.exists());

        // Verify the state manager now maintains the associated state
        final Map<String, String> expectedState = new HashMap<>();
        // Ensure timestamp and identifies are migrated
        expectedState.put(AbstractListProcessor.LATEST_LISTED_ENTRY_TIMESTAMP_KEY, "1492");
        expectedState.put(AbstractListProcessor.LAST_PROCESSED_LATEST_ENTRY_TIMESTAMP_KEY, "1492");
        expectedState.put(AbstractListProcessor.IDENTIFIER_PREFIX + ".0", "id");
        runner.getStateManager().assertStateEquals(expectedState, Scope.CLUSTER);
    }

    @Test
    public void testFetchOnStart() throws InitializationException {

        final DistributedCache cache = new DistributedCache();
        runner.addControllerService("cache", cache);
        runner.enableControllerService(cache);
        runner.setProperty(AbstractListProcessor.DISTRIBUTED_CACHE_SERVICE, "cache");

        runner.run();

        assertEquals(1, cache.fetchCount);
    }

    static class DistributedCache extends AbstractControllerService implements DistributedMapCacheClient {
        private final Map<Object, Object> stored = new HashMap<>();
        private int fetchCount = 0;

        @Override
        public <K, V> boolean putIfAbsent(K key, V value, Serializer<K> keySerializer, Serializer<V> valueSerializer) throws IOException {
            return false;
        }

        @Override
        public <K, V> V getAndPutIfAbsent(K key, V value, Serializer<K> keySerializer, Serializer<V> valueSerializer, Deserializer<V> valueDeserializer) throws IOException {
            return null;
        }

        @Override
        public <K> boolean containsKey(K key, Serializer<K> keySerializer) throws IOException {
            return false;
        }

        @Override
        public <K, V> void put(K key, V value, Serializer<K> keySerializer, Serializer<V> valueSerializer) throws IOException {
            stored.put(key, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> V get(K key, Serializer<K> keySerializer, Deserializer<V> valueDeserializer) throws IOException {
            fetchCount++;
            return (V) stored.get(key);
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public <K> boolean remove(K key, Serializer<K> serializer) throws IOException {
            final Object value = stored.remove(key);
            return value != null;
        }

        @Override
        public long removeByPattern(String regex) throws IOException {
            final List<Object> removedRecords = new ArrayList<>();
            Pattern p = Pattern.compile(regex);
            for (Object key : stored.keySet()) {
                // Key must be backed by something that can be converted into a String
                Matcher m = p.matcher(key.toString());
                if (m.matches()) {
                    removedRecords.add(stored.get(key));
                }
            }
            final long numRemoved = removedRecords.size();
            removedRecords.forEach(stored::remove);
            return numRemoved;
        }
    }

    static class ConcreteListProcessor extends AbstractListProcessor<ListableEntity> {
        final List<ListableEntity> entities = new ArrayList<>();

        final String persistenceFilename = "ListProcessor-local-state-" + UUID.randomUUID().toString() + ".json";
        String persistenceFolder = "target/";
        File persistenceFile = new File(persistenceFolder + persistenceFilename);

        @Override
        public File getPersistenceFile() {
            return persistenceFile;
        }

        public void addEntity(final String name, final String identifier, final long timestamp) {
            final ListableEntity entity = new ListableEntity() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public String getIdentifier() {
                    return identifier;
                }

                @Override
                public long getTimestamp() {
                    return timestamp;
                }
            };

            entities.add(entity);
        }

        @Override
        protected Map<String, String> createAttributes(final ListableEntity entity, final ProcessContext context) {
            final Map<String, String> attributes = new HashMap<>();
            attributes.put(CoreAttributes.FILENAME.key(), entity.getIdentifier());
            return attributes;
        }

        @Override
        protected String getPath(final ProcessContext context) {
            return "/path";
        }

        @Override
        protected List<ListableEntity> performListing(final ProcessContext context, final Long minTimestamp) throws IOException {
            return Collections.unmodifiableList(entities);
        }

        @Override
        protected boolean isListingResetNecessary(PropertyDescriptor property) {
            return false;
        }

        @Override
        protected Scope getStateScope(final ProcessContext context) {
            return Scope.CLUSTER;
        }
    }
}
