/**
 * ﻿Copyright (C) 2010 - 2014 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */

package org.n52.wps.server.r;

import org.n52.wps.server.r.workspace.RSessionManager;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Component
@RequestMapping("/r")
public class RResource {

    protected static Logger log = LoggerFactory.getLogger(RResource.class);

    private R_Config config;

    public RResource() {
        this.config = R_Config.getInstance();

        log.debug("NEW {}", this);
    }

    @RequestMapping(value = "/resource", method = RequestMethod.GET)
    public ResponseEntity<String> getResource() {
        ResponseEntity<String> entity = new ResponseEntity<String>("Hello resource", HttpStatus.OK);
        return entity;
    }

    @RequestMapping(value = "/script", method = RequestMethod.GET)
    public ResponseEntity<String> getScript() {
        ResponseEntity<String> entity = new ResponseEntity<String>("Hello script", HttpStatus.OK);
        return entity;
    }

    @RequestMapping(value = "/sessionInfo", produces = MediaType.TEXT_PLAIN_VALUE)
    public HttpEntity<String> sessionInfo() {
        FilteredRConnection rCon = null;
        try {
            rCon = config.openRConnection();

            RSessionManager session = new RSessionManager(rCon, config);
            String sessionInfo = session.getSessionInfo();

            ResponseEntity<String> entity = new ResponseEntity<String>(sessionInfo, HttpStatus.OK);
            return entity;
        }
        catch (RserveException | REXPMismatchException e) {
            log.error("Could not open connection to retrieve sesion information.", e);
            ResponseEntity<String> entity = new ResponseEntity<String>("R exception: " + e.getMessage(),
                                                                       HttpStatus.INTERNAL_SERVER_ERROR);
            return entity;
        }
        finally {
            if (rCon != null)
                rCon.close();
        }
    }

}
