/**
 * ﻿Copyright (C) 2007 - 2014 52°North Initiative for Geospatial Open Source
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

package org.n52.wps.server;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import com.thetransactioncompany.cors.CORSFilter;

public class WpsModule extends ServletModule {

    protected static Logger log = LoggerFactory.getLogger(WpsModule.class);

    @Override
    protected void configureServlets() {
        log.info("Configure {}", this);

        Map<String, String> corsParams = new HashMap<String, String>();
        corsParams.put("cors.allowOrigin", "*");
        corsParams.put("cors.allowGenericHttpRequests", "true");
        corsParams.put("cors.supportedMethods", "GET, POST, HEAD, PUT, DELETE, OPTIONS");
        corsParams.put("cors.supportedHeaders", "*");
        corsParams.put("cors.exposedHeaders", "*");
        bind(CORSFilter.class).in(Singleton.class);
        filter("/*").through(CORSFilter.class, corsParams);

        // FIXME this configuration parameter is used during integration tests
        // from web.xml:
        // <init-param>
        // <param-name>wps.config.file</param-name>
        // <param-value>${wps.config.file}</param-value>
        // </init-param>
        Map<String, String> params = new HashMap<String, String>();
        params.put("wps.config.file", "config/wps_config.xml");

        serve("/" + WebProcessingService.SERVLET_PATH).with(WebProcessingService.class, params);

        serve("/" + RetrieveResultServlet.SERVLET_PATH).with(RetrieveResultServlet.class);
    }

}
