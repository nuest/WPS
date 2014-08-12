
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
