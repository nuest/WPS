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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.xmlbeans.XmlException;
import org.n52.wps.PropertyDocument.Property;
import org.n52.wps.RepositoryDocument.Repository;
import org.n52.wps.WPSConfigurationDocument;
import org.n52.wps.WPSConfigurationDocument.WPSConfiguration;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.r.data.CustomDataTypeManager;
import org.n52.wps.server.r.syntax.RAnnotationException;
import org.n52.wps.server.r.util.RFileExtensionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RPropertyChangeManager implements PropertyChangeListener {

    private static Logger LOGGER = LoggerFactory.getLogger(RPropertyChangeManager.class);

    @Autowired
    private R_Config config;

    @Autowired
    private ScriptFileRepository fileRepository;

    public RPropertyChangeManager() {
        WPSConfig c = WPSConfig.getInstance();
        c.addPropertyChangeListener(WPSConfig.WPSCONFIG_PROPERTY_EVENT_NAME, this);
        LOGGER.info("NEW {}", this);
    }

    // public static RPropertyChangeManager getInstance() {
    // if (instance == null) {
    // instance = new RPropertyChangeManager();
    // }
    // return instance;
    // }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // String repName = LocalRAlgorithmRepository.class.getCanonicalName();
        // RepositoryManager manager = RepositoryManager.getInstance();
        // LocalRAlgorithmRepository repository = (LocalRAlgorithmRepository)
        // manager.getRepositoryForClassName(repName);

        LOGGER.info("Received PropertyChangeEvent: " + evt.getPropertyName());
        updateRepositoryConfiguration();
        CustomDataTypeManager.getInstance().update();
    }

    private class PropertyComparator implements Comparator<Property> {
        @Override
        public int compare(Property o1, Property o2) {
            int com1 = o1.getName().compareToIgnoreCase(o2.getName());
            if (com1 != 0)
                return com1;
            return (o1.getStringValue().compareToIgnoreCase(o2.getStringValue()));
        }
    }

    /**
     * Reads the current repository properties from the wps config and matches them with registered R scripts.
     * It will add algorithms and default parameters is necessary.
     * 
     * @throws ExceptionReport
     * @throws IOException
     * @throws RAnnotationException
     * 
     */
    public void updateRepositoryConfiguration() {
        // Retrieve repository document and properties:
        String localRAlgorithmRepository_className = LocalRAlgorithmRepository.class.getCanonicalName();
        Repository[] repositoryDocuments = WPSConfig.getInstance().getRegisterdAlgorithmRepositories();
        Repository repositoryDocument = null;

        for (Repository doc : repositoryDocuments) {
            if (doc.getClassName().equals(localRAlgorithmRepository_className)) {
                repositoryDocument = doc;
                break;
            }
        }

        if (repositoryDocument == null) {
            LOGGER.error("Local R Algorithm Repository is not registered");
            return;
        }

        Property[] oldPropertyArray = repositoryDocument.getPropertyArray();
        HashMap<String, Property> algorithmPropertyHash = new HashMap<String, Property>();

        boolean propertyChanged = false;
        ArrayList<Property> newPropertyList = new ArrayList<Property>();

        // retrieve set of string representations for all config variables:
        HashSet<String> configVariableNames = new HashSet<String>();

        for (RWPSConfigVariables var : RWPSConfigVariables.values()) {
            configVariableNames.add(var.toString().toLowerCase());
        }

        for (Property property : oldPropertyArray) {
            String pname = property.getName().toLowerCase();

            // check the name and active state
            if (pname.equalsIgnoreCase(RWPSConfigVariables.ALGORITHM_PROPERTY_NAME.toString())) {
                LOGGER.debug("Algorithm property: " + property);

                // put id into a dictionary to check and add later:
                algorithmPropertyHash.put(property.getStringValue(), property);
            }
            else {
                LOGGER.debug("NOT-algorithm property: " + property);

                if (configVariableNames.contains(pname)) {
                    boolean success = handleConfigVariable(property);
                    if ( !success)
                        LOGGER.warn("Invalid config variable was omitted and deleted: " + property);

                    // config variable should occur only once, doubles are omitted:
                    configVariableNames.remove(pname);
                }
                else {
                    // valid properties which are not algorithms will be just passed to the new list
                    LOGGER.debug("Unprocessed property: " + property);
                }

                newPropertyList.add(property);
            }
        }

        propertyChanged = checkMandatoryParameters(repositoryDocument,
                                                   propertyChanged,
                                                   newPropertyList,
                                                   configVariableNames);

        propertyChanged = registerRScripts(repositoryDocument, algorithmPropertyHash, propertyChanged, newPropertyList);

        // there might be registered algorithms, which don't got a script file any more, those will be deleted
        // here:
        if ( !algorithmPropertyHash.isEmpty())
            propertyChanged = true;

        propertyChanged = checkPropertyOrder(oldPropertyArray, propertyChanged);

        if (propertyChanged) {
            Property[] newPropertyArray = newPropertyList.toArray(new Property[0]);

            // sort list of properties lexicographically:
            Arrays.sort(newPropertyArray, new PropertyComparator());
            repositoryDocument.setPropertyArray(newPropertyArray);

            // write compeltely new WPSConfig if property changed
            WPSConfigurationDocument wpsConfigurationDocument = WPSConfigurationDocument.Factory.newInstance();
            WPSConfiguration wpsConfig = WPSConfig.getInstance().getWPSConfig();
            wpsConfigurationDocument.setWPSConfiguration(wpsConfig);

            // writes the new WPSConfig to a file
            try {
                String configurationPath = WPSConfig.getConfigPath();
                File XMLFile = new File(configurationPath);
                wpsConfigurationDocument.save(XMLFile,
                                              new org.apache.xmlbeans.XmlOptions().setUseDefaultNamespace().setSavePrettyPrint());
                WPSConfig.forceInitialization(configurationPath);
                LOGGER.info("WPS Config was changed and saved to path {}", configurationPath);
            }
            catch (IOException | XmlException e) {
                LOGGER.error("Could not generate and save XML configuration file", e);
            }
        }
        else
            LOGGER.debug("Update ran but no properties changes.");

        LOGGER.info("Updated repository configuration. Batch start R if not running: {}", config.getEnableBatchStart());
    }

    private boolean checkPropertyOrder(Property[] oldPropertyArray, boolean propertyChanged) {
        boolean pChange = propertyChanged;

        // check if properties need to be re-ordered:
        if ( !propertyChanged) {
            PropertyComparator comp = new PropertyComparator();
            for (int i = 0; i < oldPropertyArray.length - 1; i++) {
                int order = comp.compare(oldPropertyArray[i], oldPropertyArray[i + 1]);
                if (order > 0) {
                    pChange = true;
                    break;
                }
            }
        }
        return pChange;
    }

    /**
     * 
     * @param repositoryDocument
     * @param algorithmPropertyHash
     *        A hashmap wkn -> algorithm property for all algorithms of the wps config
     * @param propertyChanged
     *        indicates, if a property has been changed previously. If so, the output will be true as well
     * @param newPropertyList
     *        The property list that possibly replaces the old property list from the wps config
     * @return true, if any properties were changed or added to the property array
     */
    private boolean registerRScripts(Repository repositoryDocument,
                                     HashMap<String, Property> algorithmPropertyHash,
                                     boolean propertyChanged,
                                     ArrayList<Property> newPropertyList) {
        boolean pChanged = propertyChanged;

        // check script dir for R process files
        Collection<File> scripts = config.getScriptFiles();
        fileRepository.reset();

        for (File file : scripts) {
            boolean b = registerRScriptsFromDirectory(file,
                                                      repositoryDocument,
                                                      algorithmPropertyHash,
                                                      newPropertyList,
                                                      pChanged);
            if (b)
                pChanged = b;
        }

        return propertyChanged || pChanged;
    }

    private boolean registerRScriptsFromDirectory(File directory,
                                                  Repository repositoryDocument,
                                                  HashMap<String, Property> algorithmPropertyHash,
                                                  ArrayList<Property> newPropertyList,
                                                  boolean pChanged) {
        if ( !directory.isDirectory()) {
            LOGGER.error("Provided file is not a directory, cannot load scripts: {}", directory);
            return false;
        }
        File[] scripts = directory.listFiles(new RFileExtensionFilter());
        LOGGER.debug("Loading {} script files from {}: {}", scripts.length, directory, Arrays.toString(scripts));

        for (File scriptf : scripts) {
            try {
                boolean registered = fileRepository.registerScript(scriptf);

                if ( !registered) {
                    LOGGER.debug("Could not register script based on file {}", scriptf);
                    continue;
                }

                String wkn = fileRepository.getWKNForScriptFile(scriptf);
                Property prop = algorithmPropertyHash.get(wkn);
                // case: property is missing in wps config
                if (prop == null) {
                    // add property if Algorithm is not inside process description yet
                    prop = repositoryDocument.addNewProperty();
                    prop.setActive(true);
                    prop.setName(RWPSConfigVariables.ALGORITHM_PROPERTY_NAME.toString());
                    prop.setStringValue(wkn);
                    newPropertyList.add(prop);
                    LOGGER.debug("Added new algorithm property to repo document: {}", prop);

                    pChanged = true;
                }
                else {
                    LOGGER.debug("Algorithm property already repo document: {}", prop);
                    newPropertyList.add(algorithmPropertyHash.remove(wkn));
                }

            }
            catch (RAnnotationException | IOException | ExceptionReport e) {
                LOGGER.error("Could not register script based on file {}", scriptf, e);
            }

            /*
             * if(prop.getActive() && addAlgorithm){ repository.addAlgorithm(wkn); }
             */
        }

        return pChanged;
    }

    /**
     * This method will insert any config variables (with default values) that should always occur in the wps
     * config
     * 
     * @param repositoryDocument
     * @param propertyChanged
     * @param newPropertyList
     * @param unusedConfigVariables
     *        Names of all config variables that do NOT occur in the property array
     * @return
     */
    private boolean checkMandatoryParameters(Repository repositoryDocument,
                                             boolean propertyChanged,
                                             ArrayList<Property> newPropertyList,
                                             HashSet<String> unusedConfigVariables) {

        /*
         * mandatory parameters, the ones from param that have not been covered yet.
         */
        // If there was no required parameters given by WPSconfig, host and port
        // defaults will be added
        // (RServe_User and RServe_port won't be added)
        if (unusedConfigVariables.contains(RWPSConfigVariables.RSERVE_HOST.toString().toLowerCase())) {
            Property host = repositoryDocument.addNewProperty();
            host.setActive(true);
            host.setName(RWPSConfigVariables.RSERVE_HOST.toString());
            host.setStringValue(config.getRServeHost());
            newPropertyList.add(host);
            propertyChanged = true;
        }

        if (unusedConfigVariables.contains(RWPSConfigVariables.RSERVE_PORT.toString().toLowerCase())) {
            Property port = repositoryDocument.addNewProperty();
            port.setActive(true);
            port.setName(RWPSConfigVariables.RSERVE_PORT.toString());
            port.setStringValue(Integer.toString(config.getRServePort()));
            newPropertyList.add(port);
            propertyChanged = true;
        }
        return propertyChanged;
    }

    /**
     * Retrieves configuration parameter from WPS config
     * 
     * @param property
     * @return true
     */
    private boolean handleConfigVariable(Property property) {
        String pname = property.getName();
        // RWPSConfigVariables.v
        boolean success = false;
        for (RWPSConfigVariables configvariable : RWPSConfigVariables.values()) {
            if (pname.equalsIgnoreCase(configvariable.toString())) {
                config.setConfigVariable(configvariable, property.getStringValue());
                success = true;
                break;
            }
        }

        return success;
    }

}
