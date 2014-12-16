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

import com.kylinolap.metadata.project.ProjectInstance;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.codahale.metrics.annotation.Metered;
import com.kylinolap.rest.exception.InternalErrorException;
import com.kylinolap.rest.request.CreateProjectRequest;
import com.kylinolap.rest.request.UpdateProjectRequest;
import com.kylinolap.rest.service.ProjectService;

/**
 * @author xduo
 */
@Controller
@RequestMapping(value = "/projects")
public class ProjectController extends BasicController {
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    @Autowired
    private ProjectService projectService;

    /**
     * Get available project list
     * 
     * @return Table metadata array
     * @throws IOException
     */
    @RequestMapping(value = "", method = { RequestMethod.GET })
    @ResponseBody
    public List<ProjectInstance> getProjects(@RequestParam(value = "limit", required = false) Integer limit, @RequestParam(value = "offset", required = false) Integer offset) {
        return projectService.listAllProjects(limit, offset);
    }

    @RequestMapping(value = "", method = { RequestMethod.POST })
    @ResponseBody
    @Metered(name = "saveProject")
    public ProjectInstance saveProject(@RequestBody CreateProjectRequest projectRequest) {
        if (StringUtils.isEmpty(projectRequest.getName())) {
            throw new InternalErrorException("A project name must be given to create a project");
        }

        ProjectInstance createdProj = null;
        try {
            createdProj = projectService.createProject(projectRequest);
        } catch (Exception e) {
            logger.error("Failed to deal with the request.", e);
            throw new InternalErrorException(e.getLocalizedMessage());
        }

        return createdProj;
    }

    @RequestMapping(value = "", method = { RequestMethod.PUT })
    @ResponseBody
    @Metered(name = "updateProject")
    public ProjectInstance updateProject(@RequestBody UpdateProjectRequest projectRequest) {
        if (StringUtils.isEmpty(projectRequest.getFormerProjectName())) {
            throw new InternalErrorException("A project name must be given to update a project");
        }

        ProjectInstance updatedProj = null;
        try {
            updatedProj = projectService.updateProject(projectRequest);
        } catch (Exception e) {
            logger.error("Failed to deal with the request.", e);
            throw new InternalErrorException(e.getLocalizedMessage());
        }

        return updatedProj;
    }

    @RequestMapping(value = "/{projectName}", method = { RequestMethod.DELETE })
    @ResponseBody
    @Metered(name = "deleteProject")
    public void deleteProject(@PathVariable String projectName) {
        try {
            projectService.deleteProject(projectName);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            throw new InternalErrorException("Failed to delete project. " + " Caused by: " + e.getMessage(), e);
        }
    }

    public void setProjectService(ProjectService projectService) {
        this.projectService = projectService;
    }

}
