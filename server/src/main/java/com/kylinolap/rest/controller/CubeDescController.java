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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.kylinolap.cube.CubeInstance;
import com.kylinolap.cube.model.CubeDesc;
import com.kylinolap.rest.service.CubeService;

/**
 * @author xduo
 * 
 */
@Controller
@RequestMapping(value = "/cube_desc")
public class CubeDescController {

    @Autowired
    private CubeService cubeService;

    /**
     * Get detail information of the "Cube ID"
     * 
     * @param cubeDescName
     *            Cube ID
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/{cubeName}", method = { RequestMethod.GET })
    @ResponseBody
    public CubeDesc[] getCube(@PathVariable String cubeName) {
        CubeInstance cubeInstance = cubeService.getCubeManager().getCube(cubeName);
        CubeDesc cSchema = cubeInstance.getDescriptor();
        if (cSchema != null) {
            return new CubeDesc[] { cSchema };
        } else {
            return null;
        }
    }

    public void setCubeService(CubeService cubeService) {
        this.cubeService = cubeService;
    }

}
