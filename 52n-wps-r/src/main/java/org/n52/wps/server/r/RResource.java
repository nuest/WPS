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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.n52.wps.commons.WPSConfig;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.r.workspace.RSessionManager;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 
 * TODO: support multiple output formats (XML, JSON), see e.g.
 * http://springinpractice.com/2012/02/22/supporting
 * -xml-and-json-web-service-endpoints-in-spring-3-1-using-responsebody
 * 
 * @author Daniel Nüst
 *
 */
@Component
@RequestMapping(RResource.R_ENDPOINT)
public class RResource {

    public static final String R_ENDPOINT = "/r";

    public static final String SESSION_INFO_PATH = "/sessionInfo";

    public static final String RESOURCE_PATH = "/resource";

    private static final String RESOURCE_PATH_PARAMS = RESOURCE_PATH;

    public static final String SCRIPT_PATH = "/script";

    private static final String SCRIPT_PATH_PARAMS = SCRIPT_PATH;

    private static final String REQUEST_PARAM_ID = "id";

    private static final Object EQUALS = "=";

    private static URL ERROR_SESSION_INFO_URL;

    protected static Logger log = LoggerFactory.getLogger(RResource.class);

    static {
        try {
            ERROR_SESSION_INFO_URL = new URL("http://internal.error/sessionInfo.not.available");
        }
        catch (MalformedURLException e) {
            log.error("cannot create fallback URL", e);
        }
    }

    @Autowired
    private R_Config config;

    @Autowired
    private ScriptFileRepository repo;

    public RResource() {
        log.debug("NEW {}", this);
    }

    @RequestMapping(value = RESOURCE_PATH_PARAMS, method = RequestMethod.GET)
    public ResponseEntity<String> getResource(@RequestParam(REQUEST_PARAM_ID) String id) {
        ResponseEntity<String> entity = new ResponseEntity<String>("Hello resource " + id, HttpStatus.OK);
        return entity;
    }

    @RequestMapping(value = SCRIPT_PATH_PARAMS, method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE,
                                                                                        RConstants.R_SCRIPT_TYPE_VALUE})
    public ResponseEntity<Resource> getScript(@RequestParam(REQUEST_PARAM_ID) String id) throws ExceptionReport,
            IOException {
        HttpHeaders headers = new HttpHeaders();

        File f = null;
        try {
            f = repo.getScriptFileForWKN(id);
            log.trace("Serving script file for id {}: {}", id, f);
        }
        catch (ExceptionReport e) {
            log.debug("Could not get script file for id '{}'", id);
            throw e;
        }

        try (FileInputStream fis = new FileInputStream(f);) {
            FileSystemResource fsr = new FileSystemResource(f);

            // headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentType(RConstants.R_SCRIPT_TYPE);
            // headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            ResponseEntity<Resource> entity = new ResponseEntity<Resource>(fsr, headers, HttpStatus.OK);
            return entity;
        }
        catch (IOException e) {
            log.debug("Could not copy script file for '{}' to output stream", id);
            throw e;
        }
    }

    @RequestMapping(value = SESSION_INFO_PATH, produces = MediaType.TEXT_PLAIN_VALUE)
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

    /**
     * 
     * @param wkn
     *        well-known name for a process
     * @return a publicly available URL to retrieve the process
     */
    public static URL getScriptURL(String wkn) throws MalformedURLException, ExceptionReport {
        StringBuilder sb = new StringBuilder();
        sb.append(WPSConfig.getInstance().getServiceBaseUrl()).append(R_ENDPOINT);
        sb.append(RResource.SCRIPT_PATH).append("?").append(REQUEST_PARAM_ID).append(EQUALS).append(wkn);
        return new URL(sb.toString());
    }

    /**
     * @return the service endpoint to retrieve a textual representation of the sessionInfo() function in R.
     */
    public static URL getSessionInfoURL() {
        StringBuilder sb = new StringBuilder();
        sb.append(WPSConfig.getInstance().getServiceBaseUrl()).append(R_ENDPOINT).append(RResource.SESSION_INFO_PATH);
        try {
            return new URL(sb.toString());
        }
        catch (MalformedURLException e) {
            log.error("Could not create URL for session info, returning fallback URL", e);
            return ERROR_SESSION_INFO_URL;
        }
    }

}
