package com.gitmind.mapper;

import com.gitmind.dto.RepositoryDto;
import com.gitmind.model.GitHubRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for GitHub repository conversions.
 */
@Mapper(componentModel = "spring")
public interface RepositoryMapper {

    @Mapping(target = "licenseName", expression = "java(repository.getLicense() != null ? repository.getLicense().getName() : null)")
    @Mapping(target = "ownerLogin", expression = "java(repository.getOwner() != null ? repository.getOwner().getLogin() : null)")
    RepositoryDto toDto(GitHubRepository repository);
}
