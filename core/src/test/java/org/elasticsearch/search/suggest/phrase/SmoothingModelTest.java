/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.suggest.phrase;

import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.NamedWriteableAwareStreamInput;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.indices.query.IndicesQueriesRegistry;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestionBuilder.Laplace;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestionBuilder.LinearInterpolation;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestionBuilder.SmoothingModel;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestionBuilder.StupidBackoff;
import org.elasticsearch.test.ESTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public abstract class SmoothingModelTest<SM extends SmoothingModel<?>> extends ESTestCase {

    private static NamedWriteableRegistry namedWriteableRegistry;
    private static IndicesQueriesRegistry indicesQueriesRegistry;

    /**
     * setup for the whole base test class
     */
    @BeforeClass
    public static void init() {
        if (namedWriteableRegistry == null) {
            namedWriteableRegistry = new NamedWriteableRegistry();
            namedWriteableRegistry.registerPrototype(SmoothingModel.class, Laplace.PROTOTYPE);
            namedWriteableRegistry.registerPrototype(SmoothingModel.class, LinearInterpolation.PROTOTYPE);
            namedWriteableRegistry.registerPrototype(SmoothingModel.class, StupidBackoff.PROTOTYPE);
        }
        indicesQueriesRegistry = new IndicesQueriesRegistry(Settings.settingsBuilder().build(), Collections.emptySet(), namedWriteableRegistry);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        namedWriteableRegistry = null;
        indicesQueriesRegistry = null;
    }

    /**
     * create random model that is put under test
     */
    protected abstract SM createTestModel();

    /**
     * mutate the given model so the returned smoothing model is different
     */
    protected abstract SM createMutation(SM original) throws IOException;

    /**
     * Test that creates new smoothing model from a random test smoothing model and checks both for equality
     */
    public void testFromXContent() throws IOException {
        QueryParseContext context = new QueryParseContext(indicesQueriesRegistry);
        context.parseFieldMatcher(new ParseFieldMatcher(Settings.EMPTY));

        SM testModel = createTestModel();
        XContentBuilder contentBuilder = XContentFactory.contentBuilder(randomFrom(XContentType.values()));
        if (randomBoolean()) {
            contentBuilder.prettyPrint();
        }
        contentBuilder.startObject();
        testModel.innerToXContent(contentBuilder, ToXContent.EMPTY_PARAMS);
        contentBuilder.endObject();
        XContentParser parser = XContentHelper.createParser(contentBuilder.bytes());
        context.reset(parser);
        SmoothingModel<?> prototype = (SmoothingModel<?>) namedWriteableRegistry.getPrototype(SmoothingModel.class,
                testModel.getWriteableName());
        SmoothingModel<?> parsedModel = prototype.fromXContent(context);
        assertNotSame(testModel, parsedModel);
        assertEquals(testModel, parsedModel);
        assertEquals(testModel.hashCode(), parsedModel.hashCode());
    }

    /**
     * Test serialization and deserialization of the tested model.
     */
    @SuppressWarnings("unchecked")
    public void testSerialization() throws IOException {
        SM testModel = createTestModel();
        SM deserializedModel = (SM) copyModel(testModel);
        assertEquals(testModel, deserializedModel);
        assertEquals(testModel.hashCode(), deserializedModel.hashCode());
        assertNotSame(testModel, deserializedModel);
    }

    /**
     * Test equality and hashCode properties
     */
    @SuppressWarnings("unchecked")
    public void testEqualsAndHashcode() throws IOException {
            SM firstModel = createTestModel();
            assertFalse("smoothing model is equal to null", firstModel.equals(null));
            assertFalse("smoothing model is equal to incompatible type", firstModel.equals(""));
            assertTrue("smoothing model is not equal to self", firstModel.equals(firstModel));
            assertThat("same smoothing model's hashcode returns different values if called multiple times", firstModel.hashCode(),
                    equalTo(firstModel.hashCode()));
            assertThat("different smoothing models should not be equal", createMutation(firstModel), not(equalTo(firstModel)));

            SM secondModel = (SM) copyModel(firstModel);
            assertTrue("smoothing model is not equal to self", secondModel.equals(secondModel));
            assertTrue("smoothing model is not equal to its copy", firstModel.equals(secondModel));
            assertTrue("equals is not symmetric", secondModel.equals(firstModel));
            assertThat("smoothing model copy's hashcode is different from original hashcode", secondModel.hashCode(), equalTo(firstModel.hashCode()));

            SM thirdModel = (SM) copyModel(secondModel);
            assertTrue("smoothing model is not equal to self", thirdModel.equals(thirdModel));
            assertTrue("smoothing model is not equal to its copy", secondModel.equals(thirdModel));
            assertThat("smoothing model copy's hashcode is different from original hashcode", secondModel.hashCode(), equalTo(thirdModel.hashCode()));
            assertTrue("equals is not transitive", firstModel.equals(thirdModel));
            assertThat("smoothing model copy's hashcode is different from original hashcode", firstModel.hashCode(), equalTo(thirdModel.hashCode()));
            assertTrue("equals is not symmetric", thirdModel.equals(secondModel));
            assertTrue("equals is not symmetric", thirdModel.equals(firstModel));
    }

    static SmoothingModel<?> copyModel(SmoothingModel<?> original) throws IOException {
        try (BytesStreamOutput output = new BytesStreamOutput()) {
            original.writeTo(output);
            try (StreamInput in = new NamedWriteableAwareStreamInput(StreamInput.wrap(output.bytes()), namedWriteableRegistry)) {
                SmoothingModel<?> prototype = (SmoothingModel<?>) namedWriteableRegistry.getPrototype(SmoothingModel.class, original.getWriteableName());
                return prototype.readFrom(in);
            }
        }
    }

}
