/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.rest.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.Objects;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Throwables;

public class ApiError implements Serializable {

    private static final long serialVersionUID = -8244515572813244686L;

    public static Builder builder() {
        return new Builder();
    }

    public static ApiError of(Throwable t) {
        return builderFromThrowable(t).build();
    }

    public static ApiError of(String message) {
        return builder().message(message).build();
    }

    /**
     * @return An {@link ApiError.Builder} whose message is initialised to either the throwable's
     *         message or the throwable's class name if the message is null and whose details are
     *         initialised to the throwable's stack trace.
     */
    public static Builder builderFromThrowable(Throwable t) {
        checkNotNull(t, "throwable");
        String message = Exceptions.collapseText(t);
        return builder()
                .message(message)
                .details(Throwables.getStackTraceAsString(t));
    }

    public static class Builder {
        private String message;
        private String details;
        private Object data;
        private Integer errorCode;

        public Builder message(String message) {
            this.message = checkNotNull(message, "message");
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder errorCode(Status errorCode) {
            return errorCode(errorCode.getStatusCode());
        }

        public Builder errorCode(Integer errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        /** as {@link #prefixMessage(String, String)} with default separator of `: ` */
        public Builder prefixMessage(String prefix) {
            return prefixMessage(prefix, ": ");
        }

        /** puts a prefix in front of the message, with the given separator if there is already a message;
         * if there is no message, it simply sets the prefix as the message.
         * if no prefix or blank, does nothing.
         */
        public Builder prefixMessage(String prefix, String separatorIfMessageNotBlank) {
            if (Strings.isNonBlank(prefix)) {
                if (Strings.isBlank(message)) message(prefix);
                else message(prefix+separatorIfMessageNotBlank+message);
            }
            return this;
        }

        public ApiError build() {
            return new ApiError(message, details, data, errorCode);
        }

        public Builder copy(ApiError error) {
            return this
                    .message(error.message)
                    .details(error.details)
                    .errorCode(error.error);
        }

        public String getMessage() {
            return message;
        }
    }

    private final String message;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final String details;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Object data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer error;

    public ApiError(String message) { this(message, null); }
    public ApiError(String message, String details) { this(message, details, null, null); }
    public ApiError(
            @JsonProperty("message") String message,
            @JsonProperty("details") String details,
            @JsonProperty("data") Object data,
            @JsonProperty("error") Integer error) {
        this.message = checkNotNull(message, "message");
        this.details = details != null ? details : "";
        this.data = data;
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public Object getData() {
        return data;
    }
    
    public Integer getError() {
        return error;
    }

    public Response asBadRequestResponseJson() {
        return asResponse(Status.BAD_REQUEST, MediaType.APPLICATION_JSON_TYPE);
    }

    public Response asResponse(Status defaultStatusIfNoErrorCodeAlready, MediaType type) {
        boolean hasErrorAlready = this.getError()!=null && this.getError()!=0;
        int errorCode = hasErrorAlready ? error : defaultStatusIfNoErrorCodeAlready!=null ? defaultStatusIfNoErrorCodeAlready.getStatusCode() : Status.INTERNAL_SERVER_ERROR.getStatusCode();
        return Response.status(errorCode)
            .type(type)
            .entity(hasErrorAlready ? this: builder().copy(this).errorCode(errorCode).build())
            .build();
    }

    public Response asResponse(MediaType type) {
        return asResponse(null, type);
    }

    public Response asJsonResponse() {
        return asResponse(MediaType.APPLICATION_JSON_TYPE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiError)) return false;
        ApiError apiError = (ApiError) o;
        return Objects.equals(message, apiError.message) &&
                Objects.equals(details, apiError.details) &&
                Objects.equals(error, apiError.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, details, error);
    }

    @Override
    public String toString() {
        return "ApiError{" +
                "message='" + message + '\'' +
                ", details='" + details + '\'' +
                ", error=" + error +
                '}';
    }
}
