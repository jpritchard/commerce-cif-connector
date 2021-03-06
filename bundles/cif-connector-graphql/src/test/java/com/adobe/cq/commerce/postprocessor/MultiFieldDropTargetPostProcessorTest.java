/*******************************************************************************
 *
 *    Copyright 2019 Adobe. All rights reserved.
 *    This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License. You may obtain a copy
 *    of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software distributed under
 *    the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *    OF ANY KIND, either express or implied. See the License for the specific language
 *    governing permissions and limitations under the License.
 *
 ******************************************************************************/

/*******************************************************************************
 * 
 *    Copyright (c) 2018 iDA MediaFoundry
 * 
 *    Permission is hereby granted, free of charge, to any person obtaining a
 *    copy of this software and associated documentation files (the "Software"),
 *    to deal in the Software without restriction, including without limitation
 *    the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *    and/or sell copies of the Software, and to permit persons to whom the Software
 *    is furnished to do so, subject to the following conditions:
 * 
 *    The above copyright notice and this permission notice shall be included in all
 *    copies or substantial portions of the Software.
 * 
 *    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *    WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *    CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *******************************************************************************/

package com.adobe.cq.commerce.postprocessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.servlethelpers.MockSlingHttpServletRequest;
import org.apache.sling.servlets.post.Modification;
import org.junit.Rule;
import org.junit.Test;

import com.adobe.cq.commerce.graphql.magento.GraphqlAemContext;
import io.wcm.testing.mock.aem.junit.AemContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class MultiFieldDropTargetPostProcessorTest {

    @Rule
    public final AemContext context = GraphqlAemContext.createContext(Collections.singletonMap("/content",
        "/context/drop-target-post-processor.json"));

    @Test
    public void testAppendToExistingMultiValue() throws Exception {
        testAppendToExistingMultiValue(Collections.singletonMap("./multiDropTarget->@items", "b"), "a", "b");
    }

    @Test
    public void testAppendToExistingMultiValueWithSkuSelection() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("./multiDropTarget->@items", "/var/commerce/products/b");
        map.put("./selectionId", "sku");
        map.put("./multiple", Boolean.TRUE);
        testAppendToExistingMultiValue(map, "a", "b");
    }

    @Test
    public void testAppendToExistingMultiValueWithSlugSelection() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("./multiDropTarget->@items", "/content/mockproduct");
        map.put("./selectionId", "slug");
        map.put("./multiple", Boolean.TRUE);
        testAppendToExistingMultiValue(map, "a", "mockslug");
    }

    @Test
    public void testAppendToExistingMultiValueWithMissingSlugSelection() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("./multiDropTarget->@items", "/content/missingslug");
        map.put("./selectionId", "slug");
        map.put("./multiple", Boolean.TRUE);
        testAppendToExistingMultiValue(map, "a", "/content/missingslug");
    }

    @Test
    public void testDropTargetWithSkuSelection() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("./multiDropTarget->@items", "/var/commerce/products/a");
        map.put("./selectionId", "sku");
        map.put("./multiple", Boolean.FALSE);
        map.put("./@CopyFrom", "/does/not/matter");

        // During an AEM drag and drop, the Sling POST will contain a COPY modification with the target component
        // In this test, we mock that something is copied to /content/postprocessor/jcr:content/noExistingMultiValue
        List<Modification> modifications = new ArrayList<>();
        modifications.add(Modification.onCopied("/does/not/matter", "/content/postprocessor/jcr:content/noExistingMultiValue"));

        // During an AEM drag and drop, the Sling POST is typically sent to the responsivegrid
        // In this test, we just set to any location, we'll just have to check that the dropped properties are not added there
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver());
        request.setResource(context.resourceResolver().resolve("/content/postprocessor/jcr:content/noExistingComposite"));
        request.setParameterMap(map);

        MultiFieldDropTargetPostProcessor dropTargetPostProcessor = new MultiFieldDropTargetPostProcessor();
        dropTargetPostProcessor.process(request, modifications);

        assertThat(modifications).hasSize(2); // there is an extra modification to /content/postprocessor/jcr:content/noExistingMultiValue

        // The target resource of the POST should NOT have the new property
        Resource resource = request.getResource();
        assertThat(resource.getValueMap()).hasSize(1); // just {"jcr:primaryType"="nt:unstructured"}

        // The "copy" resource of the POST MUST contain the new property
        Resource dropTargetResource = resource.getResourceResolver().getResource("/content/postprocessor/jcr:content/noExistingMultiValue");
        ValueMap targetValueMap = dropTargetResource.getValueMap();
        assertThat(targetValueMap.get("items", String.class)).isEqualTo("a");
    }

    private void testAppendToExistingMultiValue(Map<String, Object> parameterMap, String... expectedValues) throws Exception {
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver());
        request.setResource(context.resourceResolver().resolve("/content/postprocessor/jcr:content/existingMultiValue"));
        request.setParameterMap(parameterMap);

        MultiFieldDropTargetPostProcessor dropTargetPostProcessor = new MultiFieldDropTargetPostProcessor();
        List<Modification> modifications = new ArrayList<>();
        dropTargetPostProcessor.process(request, modifications);
        Resource resource = request.getResource();

        assertThat(modifications)
            .isNotEmpty()
            .hasSize(1);

        assertThat(resource).isNotNull();

        assertThat(resource.getValueMap())
            .isNotEmpty()
            .containsKeys("./items")
            .doesNotContainKeys("./multiDropTarget->@items");

        assertThat(resource.getValueMap().get("./items", String[].class))
            .isNotEmpty()
            .containsExactly(expectedValues);
    }

    @Test
    public void testCreateSubnodeAndAppendValue() throws Exception {
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver());
        request.setResource(context.resourceResolver().resolve("/content/postprocessor/jcr:content/noExistingMultiValue"));
        request.setParameterMap(Collections.singletonMap("./multiDropTarget->/subNode/@items", "b"));

        MultiFieldDropTargetPostProcessor dropTargetPostProcessor = new MultiFieldDropTargetPostProcessor();
        List<Modification> modifications = new ArrayList<>();
        dropTargetPostProcessor.process(request, modifications);
        Resource resource = request.getResource();

        assertThat(modifications)
            .isNotEmpty()
            .hasSize(1);

        assertThat(resource).isNotNull();
        assertThat(resource.getChild("multiDropTarget->")).isNull();
        assertThat(resource.getChild("subNode")).isNotNull();

        assertThat(resource.getValueMap())
            .isNotEmpty()
            .doesNotContainKeys("./multiDropTarget->/subNode/@items");

        assertThat(resource.getChild("subNode").getValueMap().get("./items", String[].class))
            .isNotEmpty()
            .containsExactly("b");
    }

    @Test
    public void testCreateNewMultiValue() throws Exception {
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver());
        request.setResource(context.resourceResolver().resolve("/content/postprocessor/jcr:content/noExistingMultiValue"));
        request.setParameterMap(Collections.singletonMap("./multiDropTarget->@items", "b"));

        MultiFieldDropTargetPostProcessor dropTargetPostProcessor = new MultiFieldDropTargetPostProcessor();
        List<Modification> modifications = new ArrayList<>();
        dropTargetPostProcessor.process(request, modifications);
        Resource resource = request.getResource();

        assertThat(modifications).isNotEmpty().hasSize(1);
        assertThat(resource).isNotNull();

        assertThat(resource.getValueMap())
            .isNotEmpty()
            .containsKeys("./items")
            .doesNotContainKeys("./multiDropTarget->@items");

        assertThat(resource.getValueMap().get("./items", String[].class))
            .isNotEmpty()
            .containsExactly("b");
    }

    @Test
    public void testConvertExistingSingleValueToMultifield() throws Exception {
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver());
        request.setResource(context.resourceResolver().resolve("/content/postprocessor/jcr:content/existingSingleValue"));
        request.setParameterMap(Collections.singletonMap("./multiDropTarget->@items", "b"));

        MultiFieldDropTargetPostProcessor dropTargetPostProcessor = new MultiFieldDropTargetPostProcessor();
        List<Modification> modifications = new ArrayList<>();
        dropTargetPostProcessor.process(request, modifications);
        Resource resource = request.getResource();

        assertThat(modifications)
            .isNotEmpty()
            .hasSize(1);

        assertThat(resource).isNotNull();

        assertThat(resource.getValueMap())
            .isNotEmpty()
            .containsKeys("./items")
            .doesNotContainKeys("./multiDropTarget->@items");

        assertThat(resource.getValueMap().get("./items", String[].class))
            .isNotEmpty()
            .containsExactly("a", "b");
    }

    @Test
    public void testCreateSubnodeWithUniqueNameAndAppendValue() throws Exception {
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver());
        request.setResource(context.resourceResolver().resolve("/content/postprocessor/jcr:content/existingComposite"));
        request.setParameterMap(Collections.singletonMap("./multiDropTarget->/subNode/{{COMPOSITE}}/@link", "b"));

        MultiFieldDropTargetPostProcessor dropTargetPostProcessor = new MultiFieldDropTargetPostProcessor();
        List<Modification> modifications = new ArrayList<>();
        dropTargetPostProcessor.process(request, modifications);
        Resource resource = request.getResource();

        assertThat(modifications)
            .isNotEmpty()
            .hasSize(1);

        assertThat(resource).isNotNull();

        assertThat(resource.getValueMap())
            .isNotEmpty()
            .doesNotContainKeys("./items")
            .doesNotContainKeys("./multiDropTarget->/subNode/{{COMPOSITE}}/@link");

        assertThat(resource.getChild("multiDropTarget->")).isNull();
        assertThat(resource.getChild("subNode")).isNotNull();
        assertThat(resource.getChild("subNode").getChildren()).hasSize(3);
        assertThat(resource.getChild("subNode/item0")).isNotNull();
        assertThat(resource.getChild("subNode/item1")).isNotNull();
        assertThat(resource.getChild("subNode/item2")).isNotNull();

        assertThat(resource.getChild("subNode/item0").getValueMap())
            .contains(
                entry("jcr:primaryType", "nt:unstructured"),
                entry("link", "a"));

        assertThat(resource.getChild("subNode/item1").getValueMap())
            .contains(
                entry("jcr:primaryType", "nt:unstructured"),
                entry("link", "c"));

        assertThat(resource.getChild("subNode/item2").getValueMap())
            .contains(
                entry("link", "b"));
    }

}
