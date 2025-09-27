package kg.geoinfo.system.geodataservice.service;

import cn.hutool.core.bean.BeanUtil;
import jakarta.transaction.Transactional;
import kg.geoinfo.system.geodataservice.dto.ProjectDto;
import kg.geoinfo.system.geodataservice.mapper.ProjectMapper;
import kg.geoinfo.system.geodataservice.models.Project;
import kg.geoinfo.system.geodataservice.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class ProjectService {
    private final ProjectRepository repository;
    private final ProjectMapper projectMapper;

    public ProjectService(ProjectRepository repository, ProjectMapper projectMapper) {
        this.repository = repository;
        this.projectMapper = projectMapper;
    }

    public ProjectDto save(ProjectDto projectDto) {
        Project entity = projectMapper.toEntity(projectDto);
        return projectMapper.toDto(repository.save(entity));
    }

    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    public ProjectDto findById(UUID id) {
        return projectMapper.toDto(repository.findById(id).orElseThrow());
    }

    public Page<ProjectDto> findByCondition(ProjectDto projectDto, Pageable pageable) {
        Page<Project> entityPage = repository.findAll(pageable);
        List<Project> entities = entityPage.getContent();
        return new PageImpl<>(projectMapper.toDto(entities), pageable, entityPage.getTotalElements());
    }

    public ProjectDto update(ProjectDto projectDto, UUID id) {
        Project entity = repository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        projectMapper.update(entity, projectDto);
        return projectMapper.toDto(repository.save(entity));
    }
}