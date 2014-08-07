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

// FvK: added Property Change Listener support
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URLDecoder;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.opengis.ows.x11.ExceptionReportDocument;
import net.opengis.wps.x100.CapabilitiesDocument;

import org.apache.xmlbeans.XmlException;
import org.n52.wps.GeneratorDocument.Generator;
import org.n52.wps.ParserDocument.Parser;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.io.GeneratorFactory;
import org.n52.wps.io.ParserFactory;
import org.n52.wps.server.database.DatabaseFactory;
import org.n52.wps.server.database.IDatabase;
import org.n52.wps.server.handler.RequestHandler;
import org.n52.wps.util.XMLBeansHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * This WPS supports HTTP GET for describeProcess and getCapabilities and XML-POST for execute.
 *
 * @author foerster, Benjamin Pross, Daniel Nüst
 *
 */
@Controller
@RequestMapping("/" + WPSConfig.WPS_SERVLET_PATH)
public class WebProcessingService {

    public static String DEFAULT_LANGUAGE = "en-US";

    public final static String SPECIAL_XML_POST_VARIABLE = "request";

    private static final String XML_CONTENT_TYPE = "text/xml";

    protected static Logger LOGGER = LoggerFactory.getLogger(WebProcessingService.class);

    private static String applicationBaseDir = null;

    @Autowired
    public WebProcessingService(ServletContext context) {
        init(context);
        LOGGER.info("NEW {}", this);
    }

    public void init(ServletContext context) {
        LOGGER.info("*** WebProcessingService initializing... ***");
        WPSConfig conf = WPSConfig.getInstance();

        // this is important to set the lon lat support for correct CRS transformation.
        // TODO: change to an additional configuration parameter.
        System.setProperty("org.geotools.referencing.forceXY", "true");

        try {
            if (conf == null) {
                LOGGER.error("Initialization failed! Please look at the properties file!");
                return;
            }
        }
        catch (Exception e) {
            LOGGER.error("Initialization failed! Please look at the properties file!", e);
            return;
        }
        LOGGER.info("Initialization of WPS properties successful!\n\t\tWPSConfig: {}", conf);

        // BASE_DIR = this.getServletContext().getRealPath("");
        applicationBaseDir = context.getRealPath("");
        LOGGER.debug("Application base dir is {}", applicationBaseDir);

        Parser[] parsers = conf.getActiveRegisteredParser();
        ParserFactory.initialize(parsers);
        LOGGER.info("Initialized {}", ParserFactory.getInstance());

        Generator[] generators = conf.getActiveRegisteredGenerator();
        GeneratorFactory.initialize(generators);
        LOGGER.info("Initialized {}", GeneratorFactory.getInstance());

        RepositoryManager repoManager = RepositoryManager.getInstance();
        LOGGER.info("Initialized {}", repoManager);

        LOGGER.info("Service base url is {} | Service endpoint is {} | Used config file is {}",
                    conf.getServiceBaseUrl(),
                    conf.getServiceEndpoint(),
                    WPSConfig.getConfigPath());

        IDatabase database = DatabaseFactory.getDatabase();
        LOGGER.info("Initialized {}", database);

        try {
            String capsConfigPath = getApplicationBaseDir() + File.separator + WPSConfig.CONFIG_FILE_DIR
                    + File.separator + WPSConfig.CAPABILITES_SKELETON_NAME;
            CapabilitiesDocument capsDoc = CapabilitiesConfiguration.getInstance(capsConfigPath);
            LOGGER.info("Initialized capabilities document:\n{}", capsDoc);
        }
        catch (IOException e) {
            LOGGER.error("error while initializing capabilitiesConfiguration", e);
        }
        catch (XmlException e) {
            LOGGER.error("error while initializing capabilitiesConfiguration", e);
        }

        // FvK: added Property Change Listener support
        // creates listener and register it to the wpsConfig instance.
        // it will listen to changes of the wpsCapabilities
        conf.addPropertyChangeListener(WPSConfig.WPSCAPABILITIES_SKELETON_PROPERTY_EVENT_NAME,
                                       new PropertyChangeListener() {
                                           @Override
                                           public void propertyChange(final PropertyChangeEvent propertyChangeEvent) {
                                               LOGGER.info("{}: Received Property Change Event: {}",
                                                           this.getClass().getName(),
                                                           propertyChangeEvent.getPropertyName());
                                               try {
                                                   CapabilitiesConfiguration.reloadSkeleton();
                                               }
                                               catch (IOException | XmlException e) {
                                                   LOGGER.error("error while initializing capabilitiesConfiguration", e);
                                               }
                                           }
                                       });

        // FvK: added Property Change Listener support
        // creates listener and register it to the wpsConfig instance.
        // it will listen to changes of the wpsConfiguration
        conf.addPropertyChangeListener(WPSConfig.WPSCONFIG_PROPERTY_EVENT_NAME, new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent propertyChangeEvent) {
                LOGGER.info("{}: Received Property Change Event: {}",
                            this.getClass().getName(),
                            propertyChangeEvent.getPropertyName());
                try {
                    CapabilitiesConfiguration.reloadSkeleton();
                }
                catch (IOException | XmlException e) {
                    LOGGER.error("error while initializing capabilitiesConfiguration", e);
                }
            }
        });

        LOGGER.info("*** WPS up and running! ***");
    }

    public static String getApplicationBaseDir() {
        return applicationBaseDir;
    }

    @RequestMapping(method = RequestMethod.GET)
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try (OutputStream out = res.getOutputStream();) {
            RequestHandler handler = new RequestHandler(req.getParameterMap(), out);
            String mimeType = handler.getResponseMimeType();
            res.setContentType(mimeType);
            handler.handle();

            res.setStatus(HttpServletResponse.SC_OK);
        }
        catch (ExceptionReport e) {
            handleException(e, res);
        }
        catch (RuntimeException e) {
            ExceptionReport er = new ExceptionReport("Error handing request: " + e.getMessage(),
                                                     ExceptionReport.NO_APPLICABLE_CODE,
                                                     e);
            handleException(er, res);
        }
        finally {
            if (res != null) {
                res.flushBuffer();
            }
        }
    }

    @RequestMapping(method = RequestMethod.POST)
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        BufferedReader reader = null;

        try {
            String contentType = req.getContentType();
            String characterEncoding = req.getCharacterEncoding();
            if (characterEncoding == null || characterEncoding.length() == 0) {
                characterEncoding = "UTF-8"; // default character encoding if unspecified
            }

            int contentLength = req.getContentLength();
            if (contentLength > WPSConfig.MAXIMUM_REQUEST_SIZE) {
                LOGGER.warn("POST request rejected, request size of " + contentLength + " too large.");
                ExceptionReport er = new ExceptionReport("Request body too large, limited to "
                        + WPSConfig.MAXIMUM_REQUEST_SIZE + " bytes", ExceptionReport.NO_APPLICABLE_CODE);
                handleException(er, res);
            }

            LOGGER.debug("Received POST: Content-Type = " + contentType + ", Character-Encoding = " + characterEncoding
                    + ", Content-Length = " + contentLength);

            int requestSize = 0;

            StringWriter writer = contentLength > 0 ? new StringWriter(contentLength) : new StringWriter();
            reader = req.getReader();
            char[] buffer = new char[8192];
            int read;
            while ( (read = reader.read(buffer)) != -1 && requestSize < WPSConfig.MAXIMUM_REQUEST_SIZE) {
                writer.write(buffer, 0, read);
                requestSize += read;
            }

            LOGGER.debug("POST request contained {} characters.", requestSize);

            // Protect against denial of service attacks.
            if (requestSize >= WPSConfig.MAXIMUM_REQUEST_SIZE && reader.read() > -1) {
                LOGGER.warn("POST request rejected, request size of {} too large.", requestSize);
                ExceptionReport er = new ExceptionReport("Request body too large, limited to "
                        + WPSConfig.MAXIMUM_REQUEST_SIZE + " bytes", ExceptionReport.NO_APPLICABLE_CODE);
                handleException(er, res);
            }

            String documentString = writer.toString();

            // Perform URL decoding, if necessary
            if ( (contentType).startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
                if (documentString.startsWith(SPECIAL_XML_POST_VARIABLE + "=")) {
                    // This is a hack to permit xml to be easily submitted via a form POST.
                    // By convention, we are allowing users to post xml if they name it
                    // with a POST parameter "request" although this is not
                    // valid per the specification.
                    documentString = documentString.substring(SPECIAL_XML_POST_VARIABLE.length() + 1);
                    LOGGER.debug("POST request form variable removed");
                }
                documentString = URLDecoder.decode(documentString, characterEncoding);
                LOGGER.debug("Decoded POST request:\n{}\n", documentString);
            }

            RequestHandler handler = new RequestHandler(new ByteArrayInputStream(documentString.getBytes("UTF-8")),
                                                        res.getOutputStream());
            String mimeType = handler.getResponseMimeType();
            res.setContentType(mimeType);

            handler.handle();

            res.setStatus(HttpServletResponse.SC_OK);
        }
        catch (ExceptionReport e) {
            handleException(e, res);
        }
        catch (Exception e) {
            ExceptionReport er = new ExceptionReport("Error handing request: " + e.getMessage(),
                                                     ExceptionReport.NO_APPLICABLE_CODE,
                                                     e);
            handleException(er, res);
        }
        finally {
            if (res != null) {
                res.flushBuffer();
            }

            if (reader != null) {
                reader.close();
            }
        }
    }

    private void handleException(ExceptionReport exception, HttpServletResponse res) {
        try {
            LOGGER.debug(exception.toString());
            // DO NOT MIX getWriter and getOuputStream!
            ExceptionReportDocument document = exception.getExceptionDocument();
            document.save(res.getOutputStream(), XMLBeansHelper.getXmlOptions());

            res.setContentType(XML_CONTENT_TYPE);
            res.setStatus(HttpServletResponse.SC_OK);
        }
        catch (IOException e) {
            LOGGER.warn("exception occured while writing ExceptionReport to stream");
            try {
                res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                              "error occured, while writing OWS Exception output");
            }
            catch (IOException ex) {
                LOGGER.error("error while writing error code to client!");
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
        catch (Exception e) {
            LOGGER.error("here..", e);
        }

        LOGGER.debug("Wrote exception response.");
    }

    @Override
    protected void finalize() throws Throwable {
        LOGGER.debug("Finalizing {}", this);
        super.finalize();
        DatabaseFactory.getDatabase().shutdown();
    }

}