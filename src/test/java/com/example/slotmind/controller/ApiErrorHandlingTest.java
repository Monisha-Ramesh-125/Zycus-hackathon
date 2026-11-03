package com.example.slotmind.controller;

import com.example.slotmind.exception.GlobalExceptionHandler;
import com.example.slotmind.exception.NotFoundException;
import com.example.slotmind.exception.SlotUnavailableException;
import com.example.slotmind.entity.SlotStatus;
import com.example.slotmind.service.AppointmentService;
import com.example.slotmind.service.SuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorHandlingTest {

    AppointmentService service = mock(AppointmentService.class);
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AppointmentController(service, mock(SuggestionService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private static final String VALID = "{\"patientName\":\"Jane\",\"type\":\"ROUTINE\",\"providerId\":\"PRV-1\",\"slotId\":\"SLT-1\"}";

    @Test
    void missingPatientNameReturns400() throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ROUTINE\",\"providerId\":\"PRV-1\",\"slotId\":\"SLT-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownEnumValueReturns400() throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientName\":\"J\",\"type\":\"NOPE\",\"providerId\":\"P\",\"slotId\":\"S\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void badStatusFilterReturns400() throws Exception {
        mvc.perform(get("/api/appointments").param("status", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void slotUnavailableReturns409() throws Exception {
        when(service.book(any())).thenThrow(new SlotUnavailableException("SLT-1", SlotStatus.BOOKED));
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SLOT_UNAVAILABLE"));
    }

    @Test
    void notFoundReturns404() throws Exception {
        when(service.get("nope")).thenThrow(new NotFoundException("Appointment", "nope"));
        mvc.perform(get("/api/appointments/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("APPOINTMENT_NOT_FOUND"));
    }

    @Test
    void uniqueViolationReturns409() throws Exception {
        var cause = new org.hibernate.exception.ConstraintViolationException("dup", new java.sql.SQLException("dup"),
                org.hibernate.exception.ConstraintViolationException.ConstraintKind.UNIQUE, "uk_slot");
        when(service.book(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup", cause));
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"));
    }

    @Test
    void otherIntegrityViolationIsNotDisguisedAsConflict() throws Exception {
        var cause = new org.hibernate.exception.ConstraintViolationException("null", new java.sql.SQLException("null"),
                org.hibernate.exception.ConstraintViolationException.ConstraintKind.NOT_NULL, "col");
        when(service.book(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("null", cause));
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("DATA_INTEGRITY"));
    }
}
