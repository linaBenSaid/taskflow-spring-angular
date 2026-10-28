package com.taskflow.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

// Shared settings for every mapper:
// - componentModel SPRING: the generated classes are Spring beans, injected into services.
// - constructor injection: mappers that use other mappers get them through the constructor
//   (so unit tests can build them with plain `new`).
// - unmappedTargetPolicy ERROR: if a DTO gains a field nobody maps, the build fails instead of
//   silently returning null.
@MapperConfig(
		componentModel = MappingConstants.ComponentModel.SPRING,
		injectionStrategy = InjectionStrategy.CONSTRUCTOR,
		unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MappingConfig {
}
