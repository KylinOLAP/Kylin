/*
 * Copyright 2013-2014 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kylinolap.rest.controller;

import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.kylinolap.rest.request.AccessRequest;
import com.kylinolap.rest.response.AccessEntryResponse;
import com.kylinolap.rest.service.AccessService;
import com.kylinolap.rest.service.ServiceTestBase;

/**
 * @author xduo
 * 
 */
public class AccessControllerTest extends ServiceTestBase {

    private AccessController accessController;

    @Autowired
    AccessService accessService;

    @Before
    public void setup() {
        super.setUp();

        accessController = new AccessController();
        accessController.setAccessService(accessService);
    }

    @Test
    public void testBasics() throws IOException {
        List<AccessEntryResponse> aes = accessController.getAccessEntities("CubeInstance", "a24ca905-1fc6-4f67-985c-38fa5aeafd92");
        Assert.assertTrue(aes.size() == 0);

        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setPermission("ADMINISTRATION");
        accessRequest.setSid("MODELER");
        accessRequest.setPrincipal(true);

        aes = accessController.grant("CubeInstance", "a24ca905-1fc6-4f67-985c-38fa5aeafd92", accessRequest);
        Assert.assertTrue(aes.size() == 1);

        Long aeId = null;
        for (AccessEntryResponse ae : aes) {
            aeId = (Long) ae.getId();
        }
        Assert.assertNotNull(aeId);

        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(aeId);
        accessRequest.setPermission("READ");

        aes = accessController.update("CubeInstance", "a24ca905-1fc6-4f67-985c-38fa5aeafd92", accessRequest);
        Assert.assertTrue(aes.size() == 1);
        for (AccessEntryResponse ae : aes) {
            aeId = (Long) ae.getId();
        }
        Assert.assertNotNull(aeId);

        accessRequest = new AccessRequest();
        accessRequest.setAccessEntryId(aeId);
        accessRequest.setPermission("READ");
        aes = accessController.revoke("CubeInstance", "a24ca905-1fc6-4f67-985c-38fa5aeafd92", accessRequest);
        Assert.assertTrue(aes.size() == 0);
    }
}
