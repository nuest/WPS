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

package org.n52.wps.webapp;

import org.n52.security.service.config.support.SecurityConfigDelegatingServletFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

public class WpsSecurityModule extends ServletModule {

    protected static Logger log = LoggerFactory.getLogger(WpsSecurityModule.class);

    @Override
    protected void configureServlets() {
        log.info("Configure {}", this);
        
        // 52n Security 
        // disables validation of the security-config.xml this is necessary because the Maven Project: org.n52.wps:52n-wps-webapp:3.3.0-SNAPSHOT @ D:\dev\GitHub4w\WPS\52n-wps-webapp\pom.xml mechanism works only if thevalidation is disabled.
        getServletContext().setAttribute("security.config.validation", "false");

        // bind(SecurityConfigContextListener.class).in(Singleton.class);

        // Delegates calls to AuthenticationChainFilter that is defined in the security-config.
        bind(SecurityConfigDelegatingServletFilter.class).in(Singleton.class);
        filter("/webAdmin/*").through(SecurityConfigDelegatingServletFilter.class);
    }

}
