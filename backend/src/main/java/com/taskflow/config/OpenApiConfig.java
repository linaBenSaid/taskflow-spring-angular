package com.taskflow.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Title and description shown at the top of Swagger UI (/swagger-ui.html).
//
// @SecurityScheme adds the "Authorize" button: log in with POST /api/auth/login, copy the token,
// click Authorize and paste it. Swagger UI then sends "Authorization: Bearer <token>" with every call.
// security = bearerAuth marks every endpoint as protected; AuthController opts out (login needs no token).
@Configuration
@OpenAPIDefinition(
		info = @Info(
				title = "TaskFlow API",
				version = "v1",
				description = """
						REST API for TaskFlow, a mini Jira: projects, members and tasks on a Kanban board.

						**How to try it:** call `POST /api/auth/register` or `POST /api/auth/login`, copy the `token` \
						from the response, click **Authorize** and paste it."""),
		security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
		name = "bearerAuth",
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT")
public class OpenApiConfig {
}
