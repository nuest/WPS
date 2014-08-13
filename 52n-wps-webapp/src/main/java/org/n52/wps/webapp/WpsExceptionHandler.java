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

import javax.xml.bind.annotation.XmlRootElement;

import org.n52.wps.server.ExceptionReport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 
 * @author Daniel Nüst
 *
 */
@ControllerAdvice
public class WpsExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({ExceptionReport.class})
    // @ResponseBody
    // ErrorMessage handleExceptionReport(ExceptionReport er) {
    public ResponseEntity<ErrorMessage> handleExceptionReport(ExceptionReport er) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ErrorMessage error = new ErrorMessage("InvalidRequest", er.getMessage());

        ResponseEntity<ErrorMessage> entity = new ResponseEntity<ErrorMessage>(error, headers, HttpStatus.BAD_REQUEST);
        return entity;
    }

    // @Override
    // protected ResponseEntity<Object> handleInvalidRequest(Exception e, WebRequest request) {
    // ExceptionReport er = (ExceptionReport) e;
    //
    // ErrorMessage error = new ErrorMessage("InvalidRequest", er.getMessage());
    //
    // HttpHeaders headers = new HttpHeaders();
    // headers.setContentType(MediaType.APPLICATION_JSON);
    //
    // return handleExceptionInternal(e, error, headers, HttpStatus.UNPROCESSABLE_ENTITY, request);
    // }

    @XmlRootElement
    public class ErrorMessage {
        private String code;
        private String message;

        public ErrorMessage() {
        }

        public ErrorMessage(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

}
