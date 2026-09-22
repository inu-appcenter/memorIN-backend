package com.memorin.global.instance.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Instance", description = "Public self-hosted instance information")
@RestController
@RequiredArgsConstructor
public class InstanceInfoController {

    private final BuildProperties buildProperties;

    @Value("${INSTANCE_PUBLIC:true}")
    private boolean instancePublic;

    @Value("${INSTANCE_NAME:memorin}")
    private String instanceName;

    @Value("${INSTANCE_DESCRIPTION:A memorIN instance}")
    private String instanceDescription;

    @Value("${SIGNUP_ENABLED:true}")
    private boolean signupEnabled;

    @Operation(
        summary = "Public instance information",
        description = "Returns only the explicitly whitelisted fields needed by an external registry. "
            + "Private instances return 404 so they cannot be registered.")
    @GetMapping("/api/instance/info")
    public ResponseEntity<InstanceInfoResponse> info() {
        if (!instancePublic) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new InstanceInfoResponse(
            instanceName,
            instanceDescription,
            true,
            signupEnabled,
            buildProperties.getVersion()
        ));
    }

    public record InstanceInfoResponse(
        String name,
        String description,
        @JsonProperty("public") boolean isPublic,
        boolean signupEnabled,
        String version
    ) {
    }
}
