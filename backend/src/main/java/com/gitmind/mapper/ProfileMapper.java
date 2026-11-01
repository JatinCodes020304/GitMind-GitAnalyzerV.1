package com.gitmind.mapper;

import com.gitmind.dto.ProfileDto;
import com.gitmind.model.GitHubProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


/**
 * MapStruct mapper for GitHub profile conversions.
 */
@Mapper(componentModel = "spring")
public interface ProfileMapper {

    ProfileDto toDto(GitHubProfile profile);
}
