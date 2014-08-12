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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import net.opengis.wps.x100.ProcessDescriptionType;

import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlOptions;
import org.n52.wps.PropertyDocument.Property;
import org.n52.wps.RepositoryDocument.Repository;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.IAlgorithm;
import org.n52.wps.server.ITransactionalAlgorithmRepository;
import org.n52.wps.server.r.data.CustomDataTypeManager;
import org.n52.wps.server.r.info.RProcessInfo;
import org.n52.wps.server.r.metadata.RAnnotationParser;
import org.n52.wps.server.spring.SpringWrapperAlgorithmRepository;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A repository to retrieve the available algorithms.
 * 
 * @author Matthias Hinz, Daniel Nüst
 * 
 */
@Component(LocalRAlgorithmRepository.COMPONENT_NAME)
public class LocalRAlgorithmRepository implements ITransactionalAlgorithmRepository {

    private static Logger LOGGER = LoggerFactory.getLogger(LocalRAlgorithmRepository.class);

    public static final String COMPONENT_NAME = "RAlgorithmRepository";

    // registered processes
    private Map<String, IAlgorithm> algorithms = new HashMap<String, IAlgorithm>();

    @Autowired
    private R_Config config;

    @Autowired
    RPropertyChangeManager changeManager;

    @Autowired
    private ScriptFileRepository repo;

    @Autowired
    private RAnnotationParser parser;

    private Map<String, RProcessInfo> processInfos;

    private boolean skipInvalidScripts = true;

    public LocalRAlgorithmRepository() {
        LOGGER.info("NEW {}", this);
    }

    @PostConstruct
    public void init() {
        LOGGER.info("Initializing Local*R*AlgorithmRepository");

        boolean startUpConditions = checkStartUpConditions();
        if (startUpConditions) {
            CustomDataTypeManager.getInstance().update();
            // unregistered scripts from repository folder will be added as algorithm to WPSconfig
            changeManager.updateRepositoryConfiguration();

            addAllAlgorithmsToRepository();
        }
        else
            LOGGER.warn("Start up conditions are not fulfilled, not adding any algorithms!");

        LOGGER.info("Initialized  Local*R*AlgorithmRepository");
    }

    /**
     * Check if repository is active and Rserve can be found
     * 
     * @return
     */
    private boolean checkStartUpConditions() {
        // check if the repository is active: ignored when wrapper is present (DNU)
        String className = this.getClass().getCanonicalName();
        WPSConfig wpsConfig = WPSConfig.getInstance();
        if ( !wpsConfig.isRepositoryActive(className)) {
            LOGGER.debug("Local R Algorithm Repository is inactive.");
            Repository[] registeredAlgorithmRepositories = WPSConfig.getInstance().getRegisterdAlgorithmRepositories();
            if (SpringWrapperAlgorithmRepository.wrapperRepositoryActiveAndConfiguredForRepo(this,
                                                                                             registeredAlgorithmRepositories))
                LOGGER.debug("Ignoring 'inactive' configuration value because a wrapper repo is active!");
            else
                return false;
        }

        // Try to build up a connection to Rserve. If it is refused, a new instance of Rserve will be opened
        LOGGER.debug("Trying to connect to Rserve.");
        try {
            RConnection testcon = config.openRConnection();
            LOGGER.info("WPS successfully connected to Rserve.");
            testcon.close();
        }
        catch (RserveException e) {
            // try to start Rserve via batchfile if enabled
            LOGGER.error("[Rserve] Could not connect to Rserve. Rserve may not be available or may not be ready at the current time.",
                         e);
            return false;
        }
        return true;
    }

    /**
     * add all available algorithms from the R config
     */
    private void addAllAlgorithmsToRepository() {
        processInfos = new HashMap<String, RProcessInfo>();

        Property[] propertyArray = WPSConfig.getInstance().getPropertiesForRepositoryClass(this.getClass().getCanonicalName());
        LOGGER.debug("Adding algorithms for properties: {}", Arrays.toString(propertyArray));

        for (Property property : propertyArray) {
            RProcessInfo processInfo = null;
            String algorithm_wkn = property.getStringValue();

            if (property.getName().equalsIgnoreCase(RWPSConfigVariables.ALGORITHM_PROPERTY_NAME.toString())) {
                File f = null;
                try {
                    f = repo.getScriptFileForWKN(algorithm_wkn);
                }
                catch (ExceptionReport e) {
                    LOGGER.error("Could not load file for algorithm {}", algorithm_wkn, e);
                    continue;
                }
                processInfo = new RProcessInfo(algorithm_wkn, f, parser);
                processInfos.put(algorithm_wkn, processInfo);
            }
            else
                continue;

            if (property.getActive() && processInfo != null) {
                if ( !repo.isScriptAvailable(processInfo)) {
                    // property.setActive(false);
                    // propertyChanged=true;
                    LOGGER.error("Missing R script for process '{}'. Process ignored - check WPS configuration.",
                                 algorithm_wkn);
                    continue;
                }

                if ( !processInfo.isValid()) {
                    // property.setActive(false);
                    // propertyChanged=true;
                    LOGGER.error("Invalid R script for process '{}'. "
                            + (skipInvalidScripts ? "Process ignored - check WPS configuration."
                                                 : "Process still added, check admin interface."), algorithm_wkn);

                    if (skipInvalidScripts)
                        continue;
                }

                addAlgorithm(algorithm_wkn);

                // //unavailable algorithms get an unavailable suffix in the
                // properties and will be deactivated
                // String unavailable_suffix = " (unavailable)";
                //
                // if(!rConfig.isScriptAvailable(algorithm_wkn)){
                // if(!algorithm_wkn.endsWith(unavailable_suffix)){
                // property.setName(algorithm_wkn+ unavailable_suffix);
                // }
                // property.setActive(false);
                // LOGGER.error("[WPS4R] Missing R script for process "+algorithm_wkn+". Property has been set inactive. Check WPS config.");
                //
                // }else{
                // if(algorithm_wkn.endsWith(unavailable_suffix)){
                // algorithm_wkn = algorithm_wkn.replace(unavailable_suffix,
                // "");
                // property.setName(algorithm_wkn);
                // }
                // addAlgorithm(algorithm_wkn);
                // }
            }
            else
                LOGGER.warn("Algorithm not added: active: {} | processInfo: {}", property.getActive(), processInfo);
        }
    }

    @Override
    public IAlgorithm getAlgorithm(String algorithmName) {
        if ( !this.config.getCacheProcesses()) {
            LOGGER.debug("Process cache disabled, creating new process for id '{}'", algorithmName);
            boolean b = addAlgorithm(algorithmName);
            if ( !b)
                LOGGER.warn("Problem adding algorithm for deactivated cache.");
        }

        if ( !this.algorithms.containsKey(algorithmName))
            throw new RuntimeException("This repository does not contain an algorithm '" + algorithmName + "'");

        return this.algorithms.get(algorithmName);
    }

    @Override
    public Collection<String> getAlgorithmNames() {
        return new ArrayList<String>(this.algorithms.keySet());
    }

    @Override
    public boolean containsAlgorithm(String className) {
        return this.algorithms.containsKey(className);
    }

    private IAlgorithm loadAlgorithmAndValidate(String wellKnownName) {
        LOGGER.debug("Loading algorithm '{}'", wellKnownName);

        IAlgorithm algorithm = new GenericRProcess(wellKnownName, config, parser, repo);

        if ( !algorithm.processDescriptionIsValid()) {
            // collect the errors
            ProcessDescriptionType description = algorithm.getDescription();
            XmlOptions validateOptions = new XmlOptions();
            ArrayList<XmlError> errorList = new ArrayList<XmlError>();
            validateOptions.setErrorListener(errorList);
            // run validation again
            description.validate(validateOptions);
            StringBuilder validationMessages = new StringBuilder();
            validationMessages.append("\n");

            for (XmlError e : errorList) {
                validationMessages.append("[");
                validationMessages.append(e.getLine());
                validationMessages.append(" | ");
                validationMessages.append(e.getErrorCode());
                validationMessages.append("] ");
                validationMessages.append(e.getMessage());
                validationMessages.append("\n");
            }
            LOGGER.warn("Algorithm description is not valid {}. Errors: {}",
                        wellKnownName,
                        validationMessages.toString());

            throw new RuntimeException("Could not load algorithm " + wellKnownName + ". ProcessDescription not valid: "
                    + validationMessages.toString());
        }

        return algorithm;
    }

    @Override
    public boolean addAlgorithm(Object processID) {
        if (processID instanceof String) {
            String algorithmName = (String) processID;

            try {
                IAlgorithm a = loadAlgorithmAndValidate(algorithmName);
                this.algorithms.put(algorithmName, a);
                LOGGER.info("Algorithm under name '{}' added: {}", algorithmName, a);

                return true;
            }
            catch (RuntimeException e) {
                String message = "Could not load algorithm for class name '" + algorithmName + "'";
                LOGGER.error(message, e);
                throw new RuntimeException(message + ": " + e.getMessage(), e);
            }
        }
        return false;
    }

    @Override
    public boolean removeAlgorithm(Object processID) {
        if ( ! (processID instanceof String)) {
            LOGGER.debug("Could not remove algorithm with processID {}", processID);
            return false;
        }

        String id = (String) processID;
        if (this.algorithms.containsKey(id))
            this.algorithms.remove(id);

        LOGGER.info("Removed algorithm: {}", id);
        return true;
    }

    @Override
    public ProcessDescriptionType getProcessDescription(String processID) {
        return getAlgorithm(processID).getDescription();
    }

    public RProcessInfo getProcessInfo(String processID) {
        return this.processInfos.get(processID);
    }

    @Override
    public void shutdown() {
        LOGGER.info("Shutting down ...");
        this.algorithms.clear();
        this.processInfos.clear();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("LocalRAlgorithmRepository [");
        if (algorithms != null)
            builder.append("algorithm count=").append(algorithms.size()).append(", ");
        if (config != null)
            builder.append("config=").append(config).append(", ");
        // if (changeManager != null)
        // builder.append("changeManager=").append(changeManager).append(", ");
        // if (repo != null)
        // builder.append("repo=").append(repo).append(", ");
        // if (parser != null)
        // builder.append("parser=").append(parser).append(", ");
        // if (processInfos != null)
        // builder.append("processInfos=").append(processInfos).append(", ");
        builder.append("skipInvalidScripts=").append(skipInvalidScripts).append("]");
        return builder.toString();
    }

}
