package com.mycompany.ramesh.alertmind.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(UpstreamServiceException.class)
	public ResponseEntity<ApiErrorResponse> handleUpstreamServiceException(UpstreamServiceException ex) {
		var body = new ApiErrorResponse("Upstream service error", ex.getResponseBody());
		return ResponseEntity.status(ex.getStatus()).body(body);
	}
}
