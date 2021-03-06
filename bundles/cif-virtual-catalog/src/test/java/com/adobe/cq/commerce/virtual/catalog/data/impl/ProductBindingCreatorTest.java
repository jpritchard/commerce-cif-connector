/*******************************************************************************
 *
 *
 *      Copyright 2020 Adobe. All rights reserved.
 *      This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License. You may obtain a copy
 *      of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software distributed under
 *      the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *      OF ANY KIND, either express or implied. See the License for the specific language
 *      governing permissions and limitations under the License.
 *
 ******************************************************************************/

package com.adobe.cq.commerce.virtual.catalog.data.impl;

import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.serviceusermapping.ServiceUserMapped;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.day.cq.wcm.api.WCMException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.wcm.testing.mock.aem.junit.AemContext;
import io.wcm.testing.mock.aem.junit.AemContextBuilder;

public class ProductBindingCreatorTest {

    @Rule
    public AemContext context = new AemContextBuilder(ResourceResolverType.JCR_OAK).build();
    private ProductBindingCreator creator;

    @Before
    public void setup() throws ParseException, RepositoryException, IOException {
        context.load().json("/context/jcr-conf-page.json", "/conf/testing/settings");
        context.load().json("/context/jcr-catalog-bindings.json", "/var/commerce");

        ServiceUserMapped serviceUserMapped = Mockito.mock(ServiceUserMapped.class);
        context.registerService(ServiceUserMapped.class, serviceUserMapped, ImmutableMap.of(ServiceUserMapped.SUBSERVICENAME,
            "product-binding-service"));

        creator = new ProductBindingCreator();
        context.registerInjectActivateService(creator);
    }

    @Test
    public void testCreateBindingWhenConfigurationIsCreated() throws WCMException, PersistenceException {
        Assert.assertNotNull(context.resourceResolver().getResource("/conf/testing/settings/cloudconfigs"));

        ResourceChange change = new ResourceChange(ResourceChange.ChangeType.ADDED, "/conf/testing/settings/cloudconfigs/commerce", false);

        creator.onChange(ImmutableList.of(change));

        Assert.assertNotNull(context.resourceResolver().getResource("/var/commerce/products/testing-english"));

        // Test that the cq:catalogPath property was added to the configuration
        Resource config = context.resourceResolver().getResource("/conf/testing/settings/cloudconfigs/commerce/jcr:content");
        Assert.assertEquals("/var/commerce/products/testing-english", config.getValueMap().get("cq:catalogPath"));
    }

    @Test
    public void testDeleteBindingWhenConfigurationIsDeleted() {
        ResourceChange change = new ResourceChange(ResourceChange.ChangeType.REMOVED,
            "/conf/testing-to-be-deleted/settings/cloudconfigs/commerce", false);
        creator.onChange(ImmutableList.of(change));

        Assert.assertNull(context.resourceResolver().getResource("/var/commerce/products/testing-to-be-deleted"));
    }
}
