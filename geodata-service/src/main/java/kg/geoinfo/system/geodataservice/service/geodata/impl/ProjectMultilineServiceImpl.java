package kg.geoinfo.system.geodataservice.service.geodata.impl;

import kg.geoinfo.system.geodataservice.dto.geodata.CreateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.ProjectMultilineDto;
import kg.geoinfo.system.geodataservice.dto.geodata.UpdateProjectMultilineDto;
import kg.geoinfo.system.geodataservice.mapper.geodata.ProjectMultilineMapper;
import kg.geoinfo.system.geodataservice.models.ProjectMultiline;
import kg.geoinfo.system.geodataservice.repository.ProjectMultilineRepository;
import kg.geoinfo.system.geodataservice.service.geodata.ProjectMultilineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectMultilineServiceImpl implements ProjectMultilineService {

    private final ProjectMultilineRepository projectMultilineRepository;
    private final ProjectMultilineMapper projectMultilineMapper;

    @Override
    @Transactional
    public ProjectMultilineDto create(CreateProjectMultilineDto createProjectMultilineDto) {
        ProjectMultiline projectMultiline = projectMultilineMapper.toEntity(createProjectMultilineDto);
        projectMultiline = projectMultilineRepository.save(projectMultiline);
        return projectMultilineMapper.toDto(projectMultiline);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMultilineDto findById(UUID id) {
        return projectMultilineRepository.findById(id)
                .map(projectMultilineMapper::toDto)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectMultilineDto> findAll(Pageable pageable) {
        return projectMultilineRepository.findAll(pageable)
                .map(projectMultilineMapper::toDto);
    }

    @Override
    @Transactional
    public ProjectMultilineDto update(UUID id, UpdateProjectMultilineDto updateProjectMultilineDto) {
        ProjectMultiline projectMultiline = projectMultilineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ProjectMultiline not found with id: " + id));
        projectMultilineMapper.update(projectMultiline, updateProjectMultilineDto);
        projectMultiline = projectMultilineRepository.save(projectMultiline);
        return projectMultilineMapper.toDto(projectMultiline);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        projectMultilineRepository.deleteById(id);
    }
}
