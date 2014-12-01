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

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.kylinolap.rest.exception.BadRequestException;
import com.kylinolap.rest.exception.ForbiddenException;
import com.kylinolap.rest.exception.InternalErrorException;
import com.kylinolap.rest.exception.NotFoundException;
import com.kylinolap.rest.response.ErrorResponse;
import com.kylinolap.rest.service.MetricsService;

/**
 * Created with IntelliJ IDEA. User: lukhan Date: 9/1/13 Time: 10:54 PM To
 * change this template use File | Settings | File Templates.
 */
public class BasicController {

    private static final Logger logger = LoggerFactory.getLogger(BasicController.class);

    @Autowired
    protected MetricsService metricsService;

    // ~ exception handlers ~
    @ExceptionHandler(Exception.class)
    void handleError(HttpServletRequest req, Exception ex) {
        logger.error("Internal error (Exception) throw out of controller", ex);
    }
    
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(InternalErrorException.class)
    @ResponseBody
    ErrorResponse handleInternalError(HttpServletRequest req, Exception ex) {
        logger.error("Internal error (InternalErrorException) throw out of controller", ex);
        return new ErrorResponse(req.getRequestURL().toString(), ex);
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(ForbiddenException.class)
    @ResponseBody
    ErrorResponse handleForbidden(HttpServletRequest req, Exception ex) {
        return new ErrorResponse(req.getRequestURL().toString(), ex);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundException.class)
    @ResponseBody
    ErrorResponse handleNotFound(HttpServletRequest req, Exception ex) {
        return new ErrorResponse(req.getRequestURL().toString(), ex);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BadRequestException.class)
    @ResponseBody
    ErrorResponse handleBadRequest(HttpServletRequest req, Exception ex) {
        return new ErrorResponse(req.getRequestURL().toString(), ex);
    }
}
