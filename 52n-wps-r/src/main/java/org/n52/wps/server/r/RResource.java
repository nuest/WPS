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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.n52.wps.server.r.workspace.RSessionManager;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.servlet.RequestScoped;

@Path("/r")
@RequestScoped
public class RResource {

    protected static Logger log = LoggerFactory.getLogger(RResource.class);

    private R_Config config;

    public RResource() {
        this.config = R_Config.getInstance();

        log.debug("NEW {}", this);
    }

    @GET
    @Path("/resource")
    public String getResource() {
        return "Hello resource";
    }

    @GET
    @Path("/script")
    public String getScript() {
        return "Hello script";
    }

    @GET
    @Path("/sessionInfo")
    @Produces(MediaType.TEXT_PLAIN)
    public Response sessionInfo() {
        FilteredRConnection rCon = null;
        try {
            rCon = config.openRConnection();

            RSessionManager session = new RSessionManager(rCon, config);
            String sessionInfo = session.getSessionInfo();
            return Response.ok(sessionInfo).build();
        }
        catch (RserveException | REXPMismatchException e) {
            log.error("Could not open connection to retrieve sesion information.", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
        finally {
            if (rCon != null)
                rCon.close();
        }
    }

}
