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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.r.data.RDataTypeRegistry;
import org.n52.wps.server.r.data.R_Resource;
import org.n52.wps.server.r.syntax.RAnnotationException;
import org.n52.wps.server.r.syntax.RAttribute;
import org.n52.wps.server.r.syntax.ResourceAnnotation;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 
 * @author Daniel Nüst
 *
 */
public class ResourceFileRepo {

    private static R_Config config;

    private ResourceFileRepository rr;

    private static String scriptId = "test.resource.repo";

    private ResourceAnnotation annotation;

    private R_Resource resourceText = new R_Resource(scriptId, resourceFileText, true);

    private R_Resource res;

    private R_Resource tempResource;

    private Path resourceDir;

    private static String resourceMissing = "/file/does/not/exist";

    private static String resourceFileText = "dummy1.txt";

    private static String resourceFileImage = "dummy2.png";

    private static String resourceFileXml = "uniform.xml";

    private static RDataTypeRegistry dataTypeRegistry = new RDataTypeRegistry();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void prepare() {
        config = new R_Config();
    }

    @Before
    public void prepareRepo() throws URISyntaxException {
        rr = new ResourceFileRepository();
        ReflectionTestUtils.setField(rr, "config", config);

        resourceDir = Paths.get(getClass().getResource("/" + resourceFileText).toURI());
        resourceDir = resourceDir.getParent();
        rr.addResourceDirectory(resourceDir);

        String string = rr.toString();
        assertThat("toString contains the resource dir", string, containsString("52n-wps-r"));
        assertThat("toString contains the resource dir", string, containsString("target"));
    }

    @Before
    public void prepareAnnotation() throws IOException, RAnnotationException {
        HashMap<RAttribute, Object> attributeHash = new HashMap<RAttribute, Object>();
        List<R_Resource> resources = new ArrayList<R_Resource>();
        resources.add(resourceText);
        resources.add(new R_Resource(scriptId, resourceFileImage, true));
        resources.add(new R_Resource(scriptId, resourceFileXml, true));
        annotation = new ResourceAnnotation(attributeHash, resources, dataTypeRegistry);
    }

    public R_Resource prepareTempResource() throws IOException, RAnnotationException, ExceptionReport {
        File temp = File.createTempFile("wps4rIT_", ".txt");

        HashMap<RAttribute, Object> attributeHash = new HashMap<RAttribute, Object>();
        List<R_Resource> resources = new ArrayList<R_Resource>();
        R_Resource tempResource = new R_Resource(scriptId, temp.getAbsolutePath(), true);
        resources.add(tempResource);
        ResourceAnnotation ann = new ResourceAnnotation(attributeHash, resources, dataTypeRegistry);
        rr.registerResources(ann);

        return tempResource;
    }

    public ResourceAnnotation prepareAnnotationWithMissingResource() throws ExceptionReport,
            IOException,
            RAnnotationException {
        HashMap<RAttribute, Object> attributeHash = new HashMap<RAttribute, Object>();
        List<R_Resource> resources = new ArrayList<R_Resource>();
        resources.add(new R_Resource(scriptId, resourceMissing, true));
        resources.add(new R_Resource(scriptId, resourceFileXml, true));
        return new ResourceAnnotation(attributeHash, resources, dataTypeRegistry);
    }

    @After
    public void deleteTempResource() {
        if (tempResource == null)
            return;

        File file = Paths.get(tempResource.getResourceValue()).toFile();
        if (file.exists())
            file.delete();
    }

    @Test
    public void resourcesAreAvailable() throws ExceptionReport {
        rr.registerResources(annotation);
        assertThat("resource is available", rr.isResourceAvailable(resourceText), is(equalTo(true)));
        assertThat("resource is available",
                   rr.isResourceAvailable(new R_Resource(scriptId, resourceFileImage, true)),
                   is(equalTo(true)));
    }

    @Test
    public void absolutePathResource() throws ExceptionReport, IOException, RAnnotationException {
        tempResource = prepareTempResource();
        assertThat("resource is available", rr.isResourceAvailable(tempResource), is(equalTo(true)));
    }

    @Test
    public void resourcesCanBeRetrieved() throws ExceptionReport {
        rr.registerResources(annotation);

        Path actual = rr.getResource(resourceText);
        Path expected = Paths.get(resourceText.getResourceValue());
        assertTrue("resource path ends with name", actual.endsWith(expected));
        assertTrue("resource path starts with resource dir", actual.startsWith(resourceDir));
        Collection<R_Resource> matchingResources = rr.getReferencingResources(expected);

        assertFalse("resource list is not empty", matchingResources.isEmpty());
        assertThat("resource list has only one entry", matchingResources.size(), is(equalTo(1)));
        assertEquals("resource is the same", matchingResources.iterator().next(), resourceText);
    }

    @Test
    public void resourcePath() throws ExceptionReport {
        boolean registered = rr.registerResources(annotation);
        assertThat("all resources are registered", registered, is(equalTo(true)));

        Path resource = rr.getResource(resourceText);

        assertThat("resource path is absolute", resource.isAbsolute(), is(equalTo(true)));
        assertThat("resource path exists", Files.exists(resource), is(equalTo(true)));
        assertThat("resource file name matches original",
                   resource.getFileName().toString(),
                   is(equalTo(resourceFileText)));
    }

    @Test
    public void missingResourceIsNotStored() throws RAnnotationException, ExceptionReport, IOException {
        ResourceAnnotation withMissingResource = prepareAnnotationWithMissingResource();
        boolean registered = rr.registerResources(withMissingResource);
        assertThat("not all resources are registered", registered, is(equalTo(false)));

        assertThat("missing resources is not available",
                   rr.isResourceAvailable(new R_Resource(scriptId, resourceMissing, true)),
                   is(equalTo(false)));
    }

    @Test
    public void registrationReturnValue() throws RAnnotationException, ExceptionReport, IOException {
        boolean registered = rr.registerResources(annotation);
        assertThat("all resources are registered", registered, is(equalTo(true)));
    }

    @Test
    public void multipleScriptsToOneResource() throws RAnnotationException, ExceptionReport, IOException {
        String scriptId2 = scriptId + ".2";
        List<R_Resource> resources = new ArrayList<R_Resource>();
        res = new R_Resource(scriptId2, resourceFileText, true);
        resources.add(res);
        ResourceAnnotation ann = new ResourceAnnotation(new HashMap<RAttribute, Object>(), resources, dataTypeRegistry);
        rr.registerResources(ann);

        assertThat("resource is registered", rr.isResourceAvailable(res), is(equalTo(true)));
    }

    @Test
    public void exceptionOnNotExistingPath() throws ExceptionReport {
        // if ( !Files.exists(path))
        // throw new ExceptionReport("Resource file not found: " + path, ExceptionReport.NO_APPLICABLE_CODE);

        Path path = Paths.get("/d/oes/not/exist");
        thrown.expectMessage("file not found");
        thrown.expectMessage(path.toString());
        thrown.expect(ExceptionReport.class);

        rr.getReferencingResources(path);
    }

    @Test
    public void exceptionOnNotExistingResourceFile() throws ExceptionReport, IOException, RAnnotationException {
        // Path out = resourcePaths.get(resource);
        // if (out != null && Files.exists(out) && out.isAbsolute() && out.toFile().canRead()) {
        // return out;
        // }
        // String name = out == null ? "(unknown)" : out.toString();
        // throw new ExceptionReport("Error for resource: " + resource + ", File " + name +
        // " not found or broken.",
        // ExceptionReport.NO_APPLICABLE_CODE);

        tempResource = prepareTempResource();
        deleteTempResource();

        thrown.expectMessage("not found or broken");
        thrown.expectMessage(tempResource.getResourceValue());
        thrown.expectMessage(tempResource.getProcessId());
        thrown.expect(ExceptionReport.class);

        rr.getResource(tempResource);
    }

    @Test
    public void exceptionOnNotExistingResource() throws ExceptionReport {
        String path = "/d/oes/not/exist";
        R_Resource resource = new R_Resource("n.a", path, true);

        thrown.expectMessage("not found or broken");
        thrown.expectMessage(path);
        thrown.expect(ExceptionReport.class);

        rr.getResource(resource);
    }

    @Test
    public void reset() throws IOException, RAnnotationException, ExceptionReport {
        rr.reset();

        assertThat("resource is not available after reset", rr.isResourceAvailable(resourceText), is(equalTo(false)));
    }

}
