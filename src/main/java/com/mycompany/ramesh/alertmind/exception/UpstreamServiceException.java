package com.mycompany.ramesh.alertmind.exception;

import org.springframework.http.HttpStatusCode;

public class UpstreamServiceException extends RuntimeException {

	private final HttpStatusCode status;
	private final String responseBody;

	public UpstreamServiceException(HttpStatusCode status, String responseBody) {
		super(status + ": " + responseBody);
		this.status = status;
		this.responseBody = responseBody;
	}

	public HttpStatusCode getStatus() {
		return status;
	}

	public String getResponseBody() {
		return responseBody;
	}
}
