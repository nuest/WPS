/**
 * Copyright (C) 2010 - 2014 52°North Initiative for Geospatial Open Source
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.opengis.wps.x100.ProcessDescriptionType;
import net.opengis.wps.x100.ProcessDescriptionsDocument;

import org.n52.wps.io.data.IData;
import org.n52.wps.server.AbstractObservableAlgorithm;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.ProcessDescription;
import org.n52.wps.server.r.data.RDataTypeRegistry;
import org.n52.wps.server.r.data.R_Resource;
import org.n52.wps.server.r.metadata.RAnnotationParser;
import org.n52.wps.server.r.metadata.RProcessDescriptionCreator;
import org.n52.wps.server.r.syntax.RAnnotation;
import org.n52.wps.server.r.syntax.RAnnotationException;
import org.n52.wps.server.r.syntax.RAnnotationType;
import org.n52.wps.server.r.syntax.RAttribute;
import org.n52.wps.server.r.util.RExecutor;
import org.n52.wps.server.r.util.RLogger;
import org.n52.wps.server.r.workspace.RIOHandler;
import org.n52.wps.server.r.workspace.RSessionManager;
import org.n52.wps.server.r.workspace.RWorkspaceManager;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import org.n52.wps.server.r.util.InvalidRScriptException;
import org.springframework.beans.factory.annotation.Autowired;

public class GenericRProcess extends AbstractObservableAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(GenericRProcess.class);

    private List<RAnnotation> annotations;

    private final R_Config config;

    private final List<String> errors = new ArrayList<>();

    private final RExecutor executor = new RExecutor();

    private final RIOHandler iohandler;

    @Autowired
    private RAnnotationParser parser;

    @Autowired
    private ScriptFileRepository scriptRepo;

    @Autowired
    private ResourceFileRepository resourceRepo;

    private boolean shutdownRServerAfterRun = false;

    public GenericRProcess(String wellKnownName,
                           R_Config config,
                           RDataTypeRegistry dataTypeRegistry) {
        super(wellKnownName, false);
        this.config = config;
        this.iohandler = new RIOHandler(dataTypeRegistry);

        this.description = initializeDescription();

        log.debug("NEW {}", this);
    }

    public List<RAnnotation> getAnnotations() {
        return annotations;
    }

    @Override
    public List<String> getErrors() {
        return this.errors;
    }

    @Override
    public Class< ? extends IData> getInputDataType(String id) {
        return this.iohandler.getInputDataType(id, this.annotations);
    }

    @Override
    public Class< ? > getOutputDataType(String id) {
        return this.iohandler.getOutputDataType(id, this.annotations);
    }

    @Override
    protected ProcessDescription initializeDescription() {
        String wkn = getWellKnownName();
        log.debug("Loading file for {}", wkn);

        // Reading process information from script annotations:
        File scriptFile = null;
        try {
            scriptFile = scriptRepo.getValidatedScriptFile(wkn);
        }
        catch (InvalidRScriptException e) {
            log.warn("Could not load sript file for {}", wkn, e);
            throw new RuntimeException("Error creating process description: " + e.getMessage(), e);
        }
        log.debug("Script file loaded: {}", scriptFile.getAbsolutePath());

        try (InputStream rScriptStream = new FileInputStream(scriptFile);) {
            log.info("Initializing description for {}", this.toString());
            this.annotations = this.parser.parseAnnotationsfromScript(rScriptStream);

            // submits annotation with process informations to ProcessdescriptionCreator:
            RProcessDescriptionCreator creator = new RProcessDescriptionCreator(wkn,
                                                                                config.isResourceDownloadEnabled(),
                                                                                config.isImportDownloadEnabled(),
                                                                                config.isScriptDownloadEnabled(),
                                                                                config.isSessionInfoLinkEnabled());
            ProcessDescriptionType doc = creator.createDescribeProcessType(this.annotations,
                                                                           wkn,
                                                                           RResource.getScriptURL(wkn),
                                                                           RResource.getSessionInfoURL());

            if (log.isDebugEnabled()) {
                ProcessDescriptionsDocument outerDoc = ProcessDescriptionsDocument.Factory.newInstance();
                ProcessDescriptionType type = outerDoc.addNewProcessDescriptions().addNewProcessDescription();
                type.set(doc);
                log.debug("Created process description for {}:\n{}", wkn, outerDoc.xmlText());
            }

            ProcessDescription processDescription = new ProcessDescription();
            processDescription.addProcessDescriptionForVersion(doc, "1.0.0");
            return processDescription;
        }
        catch (RAnnotationException | IOException | ExceptionReport e) {
            log.error("Error initializing description for script '{}'", wkn, e);
            throw new RuntimeException("Error while parsing script file or creating process description of script '"
                    + wkn + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, IData> run(Map<String, List<IData>> inputData) throws ExceptionReport {
        log.info("Running {} \n\tInput data: {}", this.toString(), Arrays.toString(inputData.entrySet().toArray()));

        FilteredRConnection rCon = null;
        try {
            rCon = config.openRConnection();
            RLogger.logGenericRProcess(rCon,
                                       "Running algorithm with input "
                                               + Arrays.deepToString(inputData.entrySet().toArray()));

            RSessionManager session = new RSessionManager(rCon, config);
            session.configureSession(getWellKnownName(), executor);

            RWorkspaceManager workspace = new RWorkspaceManager(rCon, iohandler, config);
            String originalWorkDir = workspace.prepareWorkspace(inputData, getWellKnownName());

            List<RAnnotation> resAnnotList = RAnnotation.filterAnnotations(this.annotations, RAnnotationType.RESOURCE);
            workspace.loadResources(resAnnotList);

            List<RAnnotation> inAnnotations = RAnnotation.filterAnnotations(this.annotations, RAnnotationType.INPUT);
            workspace.loadInputValues(inputData, inAnnotations);

            List<RAnnotation> importAnnotations = RAnnotation.filterAnnotations(this.annotations,
                                                                                RAnnotationType.IMPORT);
            List<File> imports = Lists.newArrayList();
            for (RAnnotation rAnnotation : importAnnotations) {
                @SuppressWarnings("unchecked")
                List<R_Resource> importList = (List<R_Resource>) rAnnotation.getObjectValue(RAttribute.NAMED_LIST);
                for (R_Resource importedScript : importList) {
                    String value = importedScript.getResourceValue();
                    try {
                        File f = scriptRepo.getImportedFileForWKN(getWellKnownName(), value);
                        imports.add(f);
                        log.debug("Got imported file {} based on import resource {}", f, importList);
                    }
                    catch (InvalidRScriptException e) {
                        log.error("Failed resolving imported script for '{}'", getWellKnownName(), e);
                        throw new ExceptionReport(e.getMessage(), ExceptionReport.NO_APPLICABLE_CODE);
                    }
                }
            }
            session.loadImportedScripts(executor, imports);

            if (log.isDebugEnabled())
                workspace.saveImage("preExecution");

            File scriptFile = scriptRepo.getScriptFile(getWellKnownName());
            boolean success = executor.executeScript(scriptFile, rCon);
            if (log.isDebugEnabled())
                workspace.saveImage("afterExecution");

            HashMap<String, IData> result = null;
            if (success) {
                List<RAnnotation> outAnnotations = RAnnotation.filterAnnotations(this.annotations,
                                                                                 RAnnotationType.OUTPUT);
                result = workspace.saveOutputValues(outAnnotations);
                result = session.saveInfos(result);
            }
            else {
                String msg = "Failure while executing R script. See logs for details";
                log.error(msg);
                throw new ExceptionReport(msg, getClass().getName());
            }

            log.debug("RESULT: " + Arrays.toString(result.entrySet().toArray()));

            session.cleanUp();
            workspace.cleanUpInR(originalWorkDir);
            workspace.cleanUpWithWPS();

            return result;
        }
        catch (IOException | RuntimeException e) {
            String message = "Attempt to run R script file failed:\n" + e.getClass() + " - " + e.getLocalizedMessage()
                    + "\n" + e.getCause();
            log.error(message, e);
            throw new ExceptionReport(message, e.getClass().getName(), e);
        }
        catch (RAnnotationException e) {
            String message = "R script cannot be executed due to invalid annotations.";
            log.error(message, e);
            throw new ExceptionReport(message, e.getClass().getName(), e);
        }
        catch (RserveException e) {
            log.error("Rserve problem executing script: " + e.getMessage(), e);
            throw new ExceptionReport("Rserve problem executing script: " + e.getMessage(),
                                      "R",
                                      ExceptionReport.REMOTE_COMPUTATION_ERROR,
                                      e);
        }
        catch (REXPMismatchException e) {
            String message = "An R Parsing Error occoured:\n" + e.getMessage() + " - " + e.getClass() + " - "
                    + e.getLocalizedMessage() + "\n" + e.getCause();
            log.error(message, e);
            throw new ExceptionReport(message, "R", "R_Connection", e);
        }
        finally {
            if (rCon != null) {
                if (shutdownRServerAfterRun) {
                    log.debug("Shutting down R completely...");
                    try {
                        rCon.serverShutdown();
                    }
                    catch (RserveException e) {
                        String message = "Error during R server shutdown:\n" + e.getMessage() + " - " + e.getClass()
                                + " - " + e.getLocalizedMessage() + "\n" + e.getCause();
                        log.error(message, e);
                        throw new ExceptionReport(message, "R", "R_Connection", e);
                    }
                }
                else
                    rCon.close();
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("GenericRProcess [wkn = ");
        sb.append(this.wkName);
        if (scriptRepo != null) {
            sb.append(", script file = ");
            sb.append(scriptRepo.getScriptFileName(this.wkName));
        }
        if (this.annotations != null) {
            sb.append(", annotations = ");
            sb.append(Arrays.toString(this.annotations.toArray()));
        }
        sb.append("]");
        return sb.toString();
    }

}