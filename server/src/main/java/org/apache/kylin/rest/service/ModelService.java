/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.apache.kylin.rest.service;


import org.apache.kylin.job.exception.JobException;
import org.apache.kylin.metadata.model.DataModelDesc;
import org.apache.kylin.metadata.project.ProjectInstance;
import org.apache.kylin.rest.constant.Constant;
import org.apache.kylin.rest.exception.InternalErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jiazhong
 */
@Component("modelMgmtService")
public class ModelService extends BasicService {

    private static final Logger logger = LoggerFactory.getLogger(ModelService.class);

    @Autowired
    private AccessService accessService;

    @PostFilter(Constant.ACCESS_POST_FILTER_READ)
    public List<DataModelDesc> listAllModels(final String modelName, final String projectName) {
        List<DataModelDesc> models = null;
        ProjectInstance project = (null != projectName) ? getProjectManager().getProject(projectName) : null;

        if (null == project) {
            models = getMetadataManager().getModels();
        } else {
            //TO-DO
//            models = listAllModels(projectName);
            models=Collections.emptyList();
        }

        List<DataModelDesc> filterModels = new ArrayList();
        for (DataModelDesc modelDesc : models) {
            boolean isModelMatch = (null == modelName) || modelDesc.getName().toLowerCase().contains(modelName.toLowerCase());

            if (isModelMatch) {
                filterModels.add(modelDesc);
            }
        }

        return filterModels;
    }

    public List<DataModelDesc> getModels(final String modelName, final String projectName, final Integer limit, final Integer offset) {
        int climit = (null == limit) ? 30 : limit;
        int coffset = (null == offset) ? 0 : offset;

        List<DataModelDesc> modelDescs;
        modelDescs = listAllModels(modelName, projectName);

        if (modelDescs.size() <= coffset) {
            return Collections.emptyList();
        }

        if ((modelDescs.size() - coffset) < climit) {
            return modelDescs.subList(coffset, modelDescs.size());
        }

        return modelDescs.subList(coffset, coffset + climit);
    }


    public DataModelDesc createModelDesc(String projectName, DataModelDesc desc) throws IOException {
        if (getMetadataManager().getDataModelDesc(desc.getName()) != null) {
            throw new InternalErrorException("The model named " + desc.getName() + " already exists");
        }
        DataModelDesc createdDesc = null;
        createdDesc = getMetadataManager().createDataModelDesc(desc);
//        ProjectInstance project = getProjectManager().getProject(projectName);
//        accessService.inherit(createdDesc, project);
        return createdDesc;
    }


    @PreAuthorize(Constant.ACCESS_HAS_ROLE_ADMIN + " or hasPermission(#model, 'ADMINISTRATION') or hasPermission(#model, 'MANAGEMENT')")
    public DataModelDesc updateModelAndDesc(DataModelDesc desc, String newProjectName) throws IOException {
        DataModelDesc existingModel = getMetadataManager().getDataModelDesc(desc.getName());
        if (existingModel == null) {
            getMetadataManager().createDataModelDesc(desc);
        } else {
            getMetadataManager().updateDataModelDesc(desc);
        }
        return desc;
    }


}