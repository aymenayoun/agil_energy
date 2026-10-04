package com.agil.energy.exception;

import com.agil.energy.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleResourceNotFound — returns 404 with message")
    void handleResourceNotFound_returns404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("Station 99 not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).contains("Station 99");
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("handleDuplicateResource — returns 409")
    void handleDuplicateResource_returns409() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDuplicateResource(new DuplicateResourceException("Email already exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("Email");
    }

    @Test
    @DisplayName("handleBusinessException — returns 400")
    void handleBusinessException_returns400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(new BusinessException("Stock insuffisant"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("insuffisant");
    }

    @Test
    @DisplayName("handleValidationErrors — returns 400 with field errors map")
    void handleValidationErrors_returns400WithFields() throws NoSuchMethodException {
        // Construct a real MethodArgumentNotValidException
        Method method = String.class.getMethod("toString");
        MethodParameter param = new MethodParameter(method, -1);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "obj");
        bindingResult.addError(new FieldError("obj", "email", "must not be blank"));
        bindingResult.addError(new FieldError("obj", "password", "must be at least 8 chars"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getData())
                .containsEntry("email", "must not be blank")
                .containsEntry("password", "must be at least 8 chars");
    }

    @Test
    @DisplayName("handleBadCredentials — returns 401")
    void handleBadCredentials_returns401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadCredentials(new BadCredentialsException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).containsIgnoringCase("incorrect");
    }

    @Test
    @DisplayName("handleAccessDenied — returns 403")
    void handleAccessDenied_returns403() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).containsIgnoringCase("refusé");
    }

    @Test
    @DisplayName("handleGenericException — returns 500")
    void handleGenericException_returns500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGenericException(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).contains("boom");
    }
}